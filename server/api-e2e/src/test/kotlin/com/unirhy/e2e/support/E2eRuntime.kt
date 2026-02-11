package com.unirhy.e2e.support

import org.springframework.test.context.DynamicPropertyRegistry
import java.nio.file.Files
import java.nio.file.Path
import java.util.*
import kotlin.io.path.createDirectories

object E2eRuntime {
    private val contextDelegate = lazy { E2eRunContext.create() }

    val context: E2eRunContext
        get() = contextDelegate.value

    fun registerDatasource(registry: DynamicPropertyRegistry) {
        val current = context
        registry.add("spring.datasource.url") { current.database.jdbcUrl }
        registry.add("spring.datasource.username") { current.database.user }
        registry.add("spring.datasource.password") { current.database.password }
        registry.add("spring.flyway.url") { current.database.jdbcUrl }
        registry.add("spring.flyway.user") { current.database.user }
        registry.add("spring.flyway.password") { current.database.password }
    }

    fun cleanup() {
        if (contextDelegate.isInitialized()) {
            contextDelegate.value.cleanup()
        }
    }
}

data class E2eRunContext(
    val database: EphemeralPostgresDatabase,
    val workspace: Path,
    val scanWorkspace: Path,
    val admin: AdminCredentials,
) {
    fun cleanup() {
        workspace.deleteRecursivelyIfExists()
        database.dropQuietly()
    }

    companion object {
        fun create(): E2eRunContext {
            var database: EphemeralPostgresDatabase? = null
            var workspace: Path? = null
            try {
                database = createEphemeralDatabase()
                workspace = Files.createTempDirectory("unirhy-e2e-workspace-")
                val scanWorkspace = workspace.resolve("scan-root").createDirectories()
                val runId = UUID.randomUUID().toString().replace("-", "").take(12)

                return E2eRunContext(
                    database = database,
                    workspace = workspace,
                    scanWorkspace = scanWorkspace,
                    admin = AdminCredentials(
                        name = "e2e-admin",
                        email = "e2e-$runId@example.invalid",
                        password = "e2e-$runId-password",
                    ),
                )
            } catch (ex: Exception) {
                workspace?.deleteRecursivelyIfExists()
                database?.dropQuietly()
                throw ex
            }
        }

        private fun createEphemeralDatabase(): EphemeralPostgresDatabase {
            val databaseName = "unirhy_e2e_${UUID.randomUUID().toString().replace("-", "").take(12)}"
            val database = EphemeralPostgresDatabase(
                host = env("DB_HOST", "db"),
                port = env("DB_PORT", "5432").toInt(),
                user = env("DB_USER", "postgres"),
                password = System.getenv("DB_PASSWORD").orEmpty(),
                adminDatabase = env("POSTGRES_ADMIN_DB", env("POSTGRES_DB", "postgres")),
                databaseName = databaseName,
            )
            database.create()
            return database
        }

        private fun env(name: String, defaultValue: String): String {
            return System.getenv(name)
                ?.trim()
                ?.takeIf { it.isNotEmpty() }
                ?: defaultValue
        }
    }
}

data class AdminCredentials(
    val name: String,
    val email: String,
    val password: String,
)
