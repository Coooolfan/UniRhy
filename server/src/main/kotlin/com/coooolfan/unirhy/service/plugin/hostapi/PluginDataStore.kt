package com.coooolfan.unirhy.service.plugin.hostapi

import org.springframework.jdbc.core.namedparam.MapSqlParameterSource
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate
import org.springframework.stereotype.Repository

internal data class StoredPluginData(
    val key: String,
    val json: String?,
    val encrypted: ByteArray?,
)

@Repository
internal class PluginDataStore(
    private val jdbc: NamedParameterJdbcTemplate,
) {
    fun find(pluginId: String, key: String): StoredPluginData? = jdbc.query(
        """
        SELECT key, value::text, encrypted_value
        FROM public.plugin_data
        WHERE plugin_id = :pluginId AND key = :key
        """.trimIndent(),
        MapSqlParameterSource().addValue("pluginId", pluginId).addValue("key", key),
        ::mapRow,
    ).firstOrNull()

    fun find(pluginId: String, keys: Collection<String>): Map<String, StoredPluginData> {
        if (keys.isEmpty()) return emptyMap()
        return jdbc.query(
            """
            SELECT key, value::text, encrypted_value
            FROM public.plugin_data
            WHERE plugin_id = :pluginId AND key IN (:keys)
            """.trimIndent(),
            MapSqlParameterSource().addValue("pluginId", pluginId).addValue("keys", keys),
            ::mapRow,
        ).associateBy(StoredPluginData::key)
    }

    fun listKeys(pluginId: String, prefix: String, offset: Int, limit: Int): List<String> = jdbc.query(
        """
        SELECT key
        FROM public.plugin_data
        WHERE plugin_id = :pluginId
          AND LEFT(key, LENGTH(:prefix)) = :prefix
        ORDER BY key
        OFFSET :offset
        LIMIT :limit
        """.trimIndent(),
        MapSqlParameterSource()
            .addValue("pluginId", pluginId)
            .addValue("prefix", prefix)
            .addValue("offset", offset)
            .addValue("limit", limit),
    ) { rs, _ -> rs.getString(1) }

    fun countKeys(pluginId: String, prefix: String): Long = jdbc.queryForObject(
        """
        SELECT COUNT(*)
        FROM public.plugin_data
        WHERE plugin_id = :pluginId
          AND LEFT(key, LENGTH(:prefix)) = :prefix
        """.trimIndent(),
        MapSqlParameterSource().addValue("pluginId", pluginId).addValue("prefix", prefix),
        Long::class.java,
    ) ?: 0L

    fun upsertJson(pluginId: String, key: String, json: String) {
        jdbc.update(
            """
            INSERT INTO public.plugin_data (plugin_id, key, value, encrypted_value)
            VALUES (:pluginId, :key, CAST(:value AS jsonb), NULL)
            ON CONFLICT (plugin_id, key) DO UPDATE
            SET value = EXCLUDED.value, encrypted_value = NULL
            """.trimIndent(),
            MapSqlParameterSource()
                .addValue("pluginId", pluginId)
                .addValue("key", key)
                .addValue("value", json),
        )
    }

    fun upsertEncrypted(pluginId: String, key: String, encrypted: ByteArray) {
        jdbc.update(
            """
            INSERT INTO public.plugin_data (plugin_id, key, value, encrypted_value)
            VALUES (:pluginId, :key, NULL, :encrypted)
            ON CONFLICT (plugin_id, key) DO UPDATE
            SET value = NULL, encrypted_value = EXCLUDED.encrypted_value
            """.trimIndent(),
            MapSqlParameterSource()
                .addValue("pluginId", pluginId)
                .addValue("key", key)
                .addValue("encrypted", encrypted),
        )
    }

    fun delete(pluginId: String, keys: Collection<String>) {
        if (keys.isEmpty()) return
        jdbc.update(
            "DELETE FROM public.plugin_data WHERE plugin_id = :pluginId AND key IN (:keys)",
            MapSqlParameterSource().addValue("pluginId", pluginId).addValue("keys", keys),
        )
    }

    private fun mapRow(rs: java.sql.ResultSet, rowNum: Int): StoredPluginData = StoredPluginData(
        key = rs.getString("key"),
        json = rs.getString("value"),
        encrypted = rs.getBytes("encrypted_value"),
    )
}
