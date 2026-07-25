package com.coooolfan.unirhy.service.plugin

import com.coooolfan.unirhy.service.task.common.TaskKey
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate
import org.springframework.stereotype.Repository

/**
 * 插件声明的单个任务。`(pluginId, taskType)` 即 [TaskKey]。
 *
 * [userSubmittable] 表示该任务能否被用户直接从表单投递（入口任务）；
 * 非入口任务只能由上游 Executor 的返回值产生，但其表单定义仍作为
 * payload 契约参与校验。
 */
data class PluginTaskRow(
    val pluginId: String,
    val taskType: String,
    val concurrency: Int,
    val userSubmittable: Boolean,
    val formDefinitionJson: String,
) {
    val key: TaskKey get() = TaskKey(pluginId, taskType)
}

/** 已启用插件的任务定义与其插件展示名 */
data class EnabledPluginTaskRow(
    val task: PluginTaskRow,
    val pluginName: String?,
)

/**
 * `plugin_task` 的数据库访问。复合主键表不建 Jimmer 实体，
 * 与 `plugin_data` 的处理方式一致。
 */
@Repository
class PluginTaskStore(
    private val jdbc: NamedParameterJdbcTemplate,
) {

    fun findByPlugin(pluginId: String): List<PluginTaskRow> = jdbc.query(
        """
        SELECT plugin_id, task_type, concurrency, user_submittable, form_definition::text
        FROM public.plugin_task
        WHERE plugin_id = :pluginId
        ORDER BY task_type
        """.trimIndent(),
        MapSqlParameterSource("pluginId", pluginId),
        ::mapRow,
    )

    fun find(key: TaskKey): PluginTaskRow? = jdbc.query(
        """
        SELECT plugin_id, task_type, concurrency, user_submittable, form_definition::text
        FROM public.plugin_task
        WHERE plugin_id = :pluginId AND task_type = :taskType
        """.trimIndent(),
        MapSqlParameterSource()
            .addValue("pluginId", key.namespace)
            .addValue("taskType", key.taskType),
        ::mapRow,
    ).firstOrNull()

    /** 全部已安装插件的任务定义，含禁用插件 */
    fun findAll(): List<PluginTaskRow> = jdbc.query(
        """
        SELECT plugin_id, task_type, concurrency, user_submittable, form_definition::text
        FROM public.plugin_task
        ORDER BY plugin_id, task_type
        """.trimIndent(),
        ::mapRow,
    )

    /** 仅已启用插件的任务定义，附带插件展示名 */
    fun findEnabled(): List<EnabledPluginTaskRow> = jdbc.query(
        """
        SELECT t.plugin_id, t.task_type, t.concurrency, t.user_submittable, t.form_definition::text, p.name
        FROM public.plugin_task t
                 JOIN public.plugin p ON p.id = t.plugin_id
        WHERE p.enabled = TRUE
        ORDER BY t.plugin_id, t.task_type
        """.trimIndent(),
    ) { rs, rowNum ->
        EnabledPluginTaskRow(task = mapRow(rs, rowNum), pluginName = rs.getString(6))
    }

    /**
     * 覆盖写入某插件的任务定义集合：删除不再声明的任务，其余按 taskType upsert。
     * 覆盖升级时保留已有 `concurrency`（管理员可能已调整过）。
     */
    fun replaceAll(pluginId: String, tasks: List<PluginTaskRow>) {
        val retained = tasks.map { it.taskType }
        if (retained.isEmpty()) {
            jdbc.update(
                "DELETE FROM public.plugin_task WHERE plugin_id = :pluginId",
                MapSqlParameterSource("pluginId", pluginId),
            )
            return
        }
        jdbc.update(
            """
            DELETE FROM public.plugin_task
            WHERE plugin_id = :pluginId AND task_type NOT IN (:retained)
            """.trimIndent(),
            MapSqlParameterSource().addValue("pluginId", pluginId).addValue("retained", retained),
        )
        val upsertSql = """
            INSERT INTO public.plugin_task
                (plugin_id, task_type, concurrency, user_submittable, form_definition)
            VALUES (:pluginId, :taskType, :concurrency, :userSubmittable, CAST(:formDefinition AS jsonb))
            ON CONFLICT (plugin_id, task_type) DO UPDATE
                SET user_submittable = EXCLUDED.user_submittable,
                    form_definition  = EXCLUDED.form_definition
        """.trimIndent()
        val params = tasks.map { task ->
            MapSqlParameterSource()
                .addValue("pluginId", pluginId)
                .addValue("taskType", task.taskType)
                .addValue("concurrency", task.concurrency)
                .addValue("userSubmittable", task.userSubmittable)
                .addValue("formDefinition", task.formDefinitionJson)
        }.toTypedArray()
        jdbc.batchUpdate(upsertSql, params)
    }

    /** 管理员直接修改并发值；任务不存在时返回 false */
    fun updateConcurrency(key: TaskKey, concurrency: Int): Boolean = jdbc.update(
        """
        UPDATE public.plugin_task
        SET concurrency = :concurrency
        WHERE plugin_id = :pluginId AND task_type = :taskType
        """.trimIndent(),
        MapSqlParameterSource()
            .addValue("pluginId", key.namespace)
            .addValue("taskType", key.taskType)
            .addValue("concurrency", concurrency),
    ) > 0

    private fun mapRow(rs: java.sql.ResultSet, rowNum: Int): PluginTaskRow = PluginTaskRow(
        pluginId = rs.getString(1),
        taskType = rs.getString(2),
        concurrency = rs.getInt(3),
        userSubmittable = rs.getBoolean(4),
        formDefinitionJson = rs.getString(5),
    )
}
