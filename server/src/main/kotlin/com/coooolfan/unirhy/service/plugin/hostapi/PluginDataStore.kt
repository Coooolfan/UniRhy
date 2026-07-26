package com.coooolfan.unirhy.service.plugin.hostapi

import com.coooolfan.unirhy.model.PluginData
import com.coooolfan.unirhy.model.encryptedValue
import com.coooolfan.unirhy.model.id
import com.coooolfan.unirhy.model.key
import com.coooolfan.unirhy.model.pluginId
import com.coooolfan.unirhy.model.value
import org.babyfish.jimmer.sql.ast.mutation.SaveMode
import org.babyfish.jimmer.sql.kt.KSqlClient
import org.babyfish.jimmer.sql.kt.ast.expression.count
import org.babyfish.jimmer.sql.kt.ast.expression.eq
import org.babyfish.jimmer.sql.kt.ast.expression.left
import org.babyfish.jimmer.sql.kt.ast.expression.valueIn
import org.springframework.stereotype.Repository
import tools.jackson.databind.JsonNode

/**
 * Postgres `LEFT(s, n)` 按字符计数，Kotlin `String.length` 按 UTF-16 单元计数；
 * 前缀含非 BMP 字符（如 emoji）时必须按码点数取长度，否则前缀过滤会错位。
 */
internal fun prefixLength(prefix: String): Int = prefix.codePointCount(0, prefix.length)

@Repository
internal class PluginDataStore(
    private val sql: KSqlClient,
) {
    fun find(pluginId: String, key: String): PluginData? =
        sql.createQuery(PluginData::class) {
            where(table.pluginId eq pluginId, table.key eq key)
            select(table)
        }.execute().firstOrNull()

    fun find(pluginId: String, keys: Collection<String>): Map<String, PluginData> {
        if (keys.isEmpty()) return emptyMap()
        return sql.createQuery(PluginData::class) {
            where(table.pluginId eq pluginId, table.key valueIn keys)
            select(table)
        }.execute().associateBy(PluginData::key)
    }

    fun listKeys(pluginId: String, prefix: String, offset: Int, limit: Int): List<String> =
        sql.createQuery(PluginData::class) {
            where(table.pluginId eq pluginId)
            where(table.key.left(prefixLength(prefix)) eq prefix)
            orderBy(table.key)
            select(table.key)
        }.limit(limit, offset.toLong()).execute()

    fun countKeys(pluginId: String, prefix: String): Long =
        sql.createQuery(PluginData::class) {
            where(table.pluginId eq pluginId)
            where(table.key.left(prefixLength(prefix)) eq prefix)
            select(count(table.id))
        }.execute().first()

    /** 明文写入；必须显式清空密文列以满足 `ck_plugin_data_value` */
    fun upsertJson(pluginId: String, key: String, value: JsonNode) {
        sql.save(
            PluginData {
                this.pluginId = pluginId
                this.key = key
                this.value = value
                this.encryptedValue = null
            },
            SaveMode.UPSERT,
        )
    }

    /** 密文写入；必须显式清空明文列以满足 `ck_plugin_data_value` */
    fun upsertEncrypted(pluginId: String, key: String, encrypted: ByteArray) {
        sql.save(
            PluginData {
                this.pluginId = pluginId
                this.key = key
                this.value = null
                this.encryptedValue = encrypted
            },
            SaveMode.UPSERT,
        )
    }

    fun delete(pluginId: String, keys: Collection<String>) {
        if (keys.isEmpty()) return
        sql.createDelete(PluginData::class) {
            where(table.pluginId eq pluginId, table.key valueIn keys)
        }.execute()
    }
}
