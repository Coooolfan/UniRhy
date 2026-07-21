package com.coooolfan.unirhy.service.plugin

import org.springframework.jdbc.core.namedparam.MapSqlParameterSource
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate
import org.springframework.stereotype.Component

data class PluginAvailabilityRow(
    val id: String,
    val taskType: String,
    val enabled: Boolean,
    val formDefinitionJson: String,
)

/**
 * 插件可用性的数据库权威读取。
 *
 * `plugin.enabled` 是所有节点判断插件可用性的权威状态，
 * Worker claim 与 submission 创建不能只依赖节点本地注册表。
 */
@Component
class PluginStore(
    private val jdbc: NamedParameterJdbcTemplate,
) {

    /**
     * 共享锁定插件行并返回可用性信息；不存在返回 null。
     * submission 创建事务持有该共享锁直到插入提交，防止并发删除遗漏新 submission。
     */
    fun lockForShare(pluginId: String): PluginAvailabilityRow? =
        jdbc.query(
            """
            SELECT id, task_type, enabled, form_definition::text
            FROM public.plugin
            WHERE id = :id
            FOR SHARE
            """.trimIndent(),
            MapSqlParameterSource("id", pluginId),
        ) { rs, _ ->
            PluginAvailabilityRow(
                id = rs.getString(1),
                taskType = rs.getString(2),
                enabled = rs.getBoolean(3),
                formDefinitionJson = rs.getString(4),
            )
        }.firstOrNull()

    /** Worker claim 的插件启用谓词：插件记录存在且 enabled = true */
    fun isEnabled(pluginId: String): Boolean =
        jdbc.query(
            "SELECT enabled FROM public.plugin WHERE id = :id",
            MapSqlParameterSource("id", pluginId),
        ) { rs, _ -> rs.getBoolean(1) }.firstOrNull() == true
}
