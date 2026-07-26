package com.coooolfan.unirhy.service.plugin

import com.coooolfan.unirhy.model.Plugin
import com.coooolfan.unirhy.model.enabled
import com.coooolfan.unirhy.model.id
import org.babyfish.jimmer.sql.ast.query.LockMode
import org.babyfish.jimmer.sql.ast.query.LockWait
import org.babyfish.jimmer.sql.kt.KSqlClient
import org.babyfish.jimmer.sql.kt.ast.expression.eq
import org.springframework.stereotype.Component

data class PluginAvailabilityRow(
    val id: String,
    val enabled: Boolean,
)

/**
 * 插件可用性的数据库权威读取。
 *
 * `plugin.enabled` 是所有节点判断插件可用性的权威状态，
 * Worker claim 与根任务创建不能只依赖节点本地注册表。
 */
@Component
class PluginStore(
    private val sql: KSqlClient,
) {

    /**
     * 共享锁定插件行并返回可用性信息；不存在返回 null。
     * 根任务创建事务持有该共享锁直到插入完成，防止并发删除遗漏新任务。
     */
    fun lockForShare(pluginId: String): PluginAvailabilityRow? =
        sql.createQuery(Plugin::class) {
            where(table.id eq pluginId)
            select(table.id, table.enabled)
        }.forUpdate(LockMode.SHARE, LockWait.DEFAULT).execute().firstOrNull()?.let {
            PluginAvailabilityRow(id = it._1, enabled = it._2)
        }

    /** Worker claim 的插件启用谓词：插件记录存在且 enabled = true */
    fun isEnabled(pluginId: String): Boolean =
        sql.createQuery(Plugin::class) {
            where(table.id eq pluginId)
            select(table.enabled)
        }.execute().firstOrNull() == true
}
