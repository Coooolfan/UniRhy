package com.coooolfan.unirhy.model

import org.babyfish.jimmer.sql.DissociateAction
import org.babyfish.jimmer.sql.Entity
import org.babyfish.jimmer.sql.GeneratedValue
import org.babyfish.jimmer.sql.GenerationType
import org.babyfish.jimmer.sql.Id
import org.babyfish.jimmer.sql.IdView
import org.babyfish.jimmer.sql.Key
import org.babyfish.jimmer.sql.KeyUniqueConstraint
import org.babyfish.jimmer.sql.ManyToOne
import org.babyfish.jimmer.sql.OnDissociate
import org.babyfish.jimmer.sql.Serialized
import org.babyfish.jimmer.sql.Table
import tools.jackson.databind.JsonNode

/**
 * 插件声明的单个任务。业务身份是 `(plugin, taskType)`，[id] 仅为代理主键。
 *
 * [userSubmittable] 表示该任务能否被用户直接从表单投递（入口任务）；
 * 非入口任务只能由上游 Executor 的返回值产生，但其表单定义仍作为
 * payload 契约参与校验。
 */
@Entity
@KeyUniqueConstraint(noMoreUniqueConstraints = true)
@Table(name = "plugin_task")
interface PluginTask {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long

    @Key
    @ManyToOne
    @OnDissociate(DissociateAction.DELETE)
    val plugin: Plugin

    /** 即任务命名空间 */
    @IdView
    val pluginId: String

    @Key
    val taskType: String

    /** 单节点并发上限；管理员可调整，覆盖升级时保留 */
    val concurrency: Int

    /** 能否被用户直接从表单投递 */
    val userSubmittable: Boolean

    /** 任务参数声明 `{schema, order}` */
    @Serialized
    val formDefinition: JsonNode
}
