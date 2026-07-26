package com.coooolfan.unirhy.service.plugin

import com.coooolfan.unirhy.model.PluginTask
import com.coooolfan.unirhy.model.by
import com.coooolfan.unirhy.model.concurrency
import com.coooolfan.unirhy.model.enabled
import com.coooolfan.unirhy.model.name
import com.coooolfan.unirhy.model.plugin
import com.coooolfan.unirhy.model.pluginId
import com.coooolfan.unirhy.model.taskType
import com.coooolfan.unirhy.service.task.common.TaskKey
import org.babyfish.jimmer.sql.ast.mutation.SaveMode
import org.babyfish.jimmer.sql.fetcher.Fetcher
import org.babyfish.jimmer.sql.kt.KSqlClient
import org.babyfish.jimmer.sql.kt.ast.expression.eq
import org.babyfish.jimmer.sql.kt.ast.expression.valueNotIn
import org.babyfish.jimmer.sql.kt.fetcher.newFetcher
import org.springframework.stereotype.Repository

/** `(pluginId, taskType)` 即任务身份 */
val PluginTask.key: TaskKey get() = TaskKey(pluginId, taskType)

private val PLUGIN_TASK_FETCHER: Fetcher<PluginTask> = newFetcher(PluginTask::class).by {
    allScalarFields()
    pluginId()
}

/** 附带插件展示名，供任务定义列表使用 */
/** `plugin_task` 的数据库访问 */
@Repository
class PluginTaskStore(
    private val sql: KSqlClient,
) {

    fun findByPlugin(pluginId: String): List<PluginTask> =
        sql.createQuery(PluginTask::class) {
            where(table.pluginId eq pluginId)
            orderBy(table.taskType)
            select(table.fetch(PLUGIN_TASK_FETCHER))
        }.execute()

    fun find(key: TaskKey): PluginTask? =
        sql.createQuery(PluginTask::class) {
            where(table.pluginId eq key.namespace, table.taskType eq key.taskType)
            select(table.fetch(PLUGIN_TASK_FETCHER))
        }.execute().firstOrNull()

    /** 全部已安装插件的任务定义，含禁用插件 */
    fun findAll(): List<PluginTask> =
        sql.createQuery(PluginTask::class) {
            orderBy(table.pluginId, table.taskType)
            select(table.fetch(PLUGIN_TASK_FETCHER))
        }.execute()

    /** 仅已启用插件的任务定义 */
    fun findEnabled(): List<PluginTask> =
        sql.createQuery(PluginTask::class) {
            where(table.plugin.enabled eq true)
            orderBy(table.pluginId, table.taskType)
            select(table.fetch(PLUGIN_TASK_FETCHER))
        }.execute()

    /**
     * 覆盖写入某插件的任务定义集合：删除不再声明的任务，其余按 `(plugin, taskType)` upsert。
     * 覆盖升级时的 `concurrency` 保留由调用方决定（见 `PluginService.upload`）。
     */
    fun replaceAll(pluginId: String, tasks: List<PluginTask>) {
        val retained = tasks.map { it.taskType }
        sql.createDelete(PluginTask::class) {
            where(table.pluginId eq pluginId)
            if (retained.isNotEmpty()) {
                where(table.taskType valueNotIn retained)
            }
        }.execute()
        if (tasks.isEmpty()) return
        sql.saveEntities(tasks, SaveMode.UPSERT)
    }

    /** 管理员直接修改并发值；任务不存在时返回 false */
    fun updateConcurrency(key: TaskKey, concurrency: Int): Boolean =
        sql.createUpdate(PluginTask::class) {
            set(table.concurrency, concurrency)
            where(table.pluginId eq key.namespace, table.taskType eq key.taskType)
        }.execute() > 0
}
