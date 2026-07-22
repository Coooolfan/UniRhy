package com.unirhy.e2e;

import com.coooolfan.unirhy.service.plugin.hostapi.NestedPluginHostCallExecutor;
import com.coooolfan.unirhy.service.plugin.hostapi.PluginHostSupport;
import com.unirhy.e2e.support.E2eRunContext;
import com.unirhy.e2e.support.E2eRuntime;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentMatchers;
import org.springframework.aop.support.AopUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.EnableTransactionManagement;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import run.endive.runtime.ExportFunction;
import run.endive.runtime.HostFunction;
import run.endive.runtime.Instance;
import run.endive.runtime.Memory;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ObjectNode;

import javax.sql.DataSource;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = PluginHostTransactionE2eTest.JdbcTestConfiguration.class)
@Tag("full")
class PluginHostTransactionE2eTest {

    private static final String TABLE = "plugin_host_transaction_test";

    private final JdbcTemplate jdbc;
    private final PlatformTransactionManager transactionManager;
    private final TransactionalWriter writer;
    private final TransactionTemplate transactions;

    @Autowired
    PluginHostTransactionE2eTest(
            JdbcTemplate jdbc,
            PlatformTransactionManager transactionManager,
            TransactionalWriter writer
    ) {
        this.jdbc = jdbc;
        this.transactionManager = transactionManager;
        this.writer = writer;
        this.transactions = new TransactionTemplate(transactionManager);
    }

    @BeforeEach
    void setUpTable() {
        jdbc.execute("DROP TABLE IF EXISTS " + TABLE);
        jdbc.execute("CREATE TABLE " + TABLE + " (id BIGINT PRIMARY KEY, value TEXT NOT NULL UNIQUE)");
        jdbc.update("INSERT INTO " + TABLE + " (id, value) VALUES (?, ?)", 1L, "occupied");
    }

    @AfterAll
    static void cleanup() {
        E2eRuntime.INSTANCE.cleanup();
    }

    @Test
    void databaseErrorEnvelopeDoesNotPoisonOuterCommitOrRollback() {
        assertTrue(AopUtils.isAopProxy(writer));
        HostHarness host = new HostHarness(transactionManager);
        HostFunction conflictingInsert = host.function("host_test_conflict", ignored -> {
            writer.insert(2L, "occupied");
            return null;
        });
        HostFunction validInsert = host.function("host_test_insert", request -> {
            writer.insert(
                    request.path("id").longValue(),
                    request.path("value").stringValue()
            );
            return null;
        });

        transactions.executeWithoutResult(status -> {
            assertConflict(host.invoke(conflictingInsert, "{}"));
            assertSuccess(host.invoke(validInsert, "{\"id\":3,\"value\":\"committed\"}"));
        });
        assertEquals(1, countByValue("committed"));

        transactions.executeWithoutResult(status -> {
            assertConflict(host.invoke(conflictingInsert, "{}"));
            assertSuccess(host.invoke(validInsert, "{\"id\":4,\"value\":\"rolled-back\"}"));
            status.setRollbackOnly();
        });
        assertEquals(0, countByValue("rolled-back"));
    }

    @Test
    void responseSerializationFailureRollsBackTheHostCallSavepoint() {
        HostHarness host = new HostHarness(transactionManager);
        HostFunction unserializableResult = host.function("host_test_serialization", ignored -> {
            writer.insert(5L, "serialization-failed");
            return new SelfReferentialValue();
        });
        HostFunction validInsert = host.function("host_test_insert", ignored -> {
            writer.insert(6L, "after-serialization-failure");
            return null;
        });

        transactions.executeWithoutResult(status -> {
            assertInternal(host.invoke(unserializableResult, "{}"));
            assertSuccess(host.invoke(validInsert, "{}"));
        });

        assertEquals(0, countByValue("serialization-failed"));
        assertEquals(1, countByValue("after-serialization-failure"));
    }

    private int countByValue(String value) {
        Integer count = jdbc.queryForObject(
                "SELECT COUNT(*) FROM " + TABLE + " WHERE value = ?",
                Integer.class,
                value
        );
        return count == null ? 0 : count;
    }

    private static void assertConflict(JsonNode response) {
        assertFalse(response.path("ok").booleanValue());
        assertEquals("CONFLICT", response.path("error").path("code").stringValue());
    }

    private static void assertSuccess(JsonNode response) {
        assertTrue(response.path("ok").booleanValue());
        assertTrue(response.path("data").isNull());
    }

    private static void assertInternal(JsonNode response) {
        assertFalse(response.path("ok").booleanValue());
        assertEquals("INTERNAL", response.path("error").path("code").stringValue());
    }

    static class SelfReferentialValue {
        public final SelfReferentialValue self = this;
    }

    @Configuration(proxyBeanMethods = false)
    @EnableTransactionManagement
    static class JdbcTestConfiguration {

        @Bean
        DataSource dataSource() {
            E2eRunContext context = E2eRuntime.INSTANCE.getContext();
            DriverManagerDataSource dataSource = new DriverManagerDataSource();
            dataSource.setUrl(context.getDatabase().getJdbcUrl());
            dataSource.setUsername(context.getDatabase().getUser());
            dataSource.setPassword(context.getDatabase().getPassword());
            return dataSource;
        }

        @Bean
        JdbcTemplate jdbcTemplate(DataSource dataSource) {
            return new JdbcTemplate(dataSource);
        }

        @Bean
        PlatformTransactionManager transactionManager(DataSource dataSource) {
            return new DataSourceTransactionManager(dataSource);
        }

        @Bean
        TransactionalWriter transactionalWriter(JdbcTemplate jdbcTemplate) {
            return new TransactionalWriter(jdbcTemplate);
        }
    }

    static class TransactionalWriter {
        private final JdbcTemplate jdbc;

        TransactionalWriter(JdbcTemplate jdbc) {
            this.jdbc = jdbc;
        }

        @Transactional
        public void insert(long id, String value) {
            jdbc.update("INSERT INTO " + TABLE + " (id, value) VALUES (?, ?)", id, value);
        }
    }

    private static final class HostHarness {
        private static final int REQUEST_PTR = 16;
        private static final int RESPONSE_PTR = 1024;

        private final JsonMapper objectMapper = JsonMapper.shared();
        private final AtomicReference<byte[]> requestBytes = new AtomicReference<>();
        private final AtomicReference<byte[]> responseBytes = new AtomicReference<>();
        private final Instance instance = mock(Instance.class);
        private final PluginHostSupport support;

        private HostHarness(PlatformTransactionManager transactionManager) {
            Memory memory = mock(Memory.class);
            ExportFunction alloc = ignored -> new long[]{RESPONSE_PTR};
            when(instance.memory()).thenReturn(memory);
            when(instance.export("alloc")).thenReturn(alloc);
            when(memory.readBytes(ArgumentMatchers.eq(REQUEST_PTR), ArgumentMatchers.anyInt()))
                    .thenAnswer(ignored -> requestBytes.get());
            doAnswer(invocation -> {
                responseBytes.set(invocation.getArgument(1));
                return null;
            }).when(memory).write(ArgumentMatchers.eq(RESPONSE_PTR), ArgumentMatchers.any(byte[].class));
            support = new PluginHostSupport(
                    objectMapper,
                    new NestedPluginHostCallExecutor(transactionManager),
                    () -> instance
            );
        }

        private HostFunction function(String name, Function<ObjectNode, Object> handler) {
            return support.jsonFunction(name, handler::apply);
        }

        private JsonNode invoke(HostFunction function, String request) {
            byte[] bytes = request.getBytes(java.nio.charset.StandardCharsets.UTF_8);
            requestBytes.set(bytes);
            responseBytes.set(null);
            function.handle().apply(instance, REQUEST_PTR, bytes.length);
            return objectMapper.readTree(responseBytes.get());
        }
    }
}
