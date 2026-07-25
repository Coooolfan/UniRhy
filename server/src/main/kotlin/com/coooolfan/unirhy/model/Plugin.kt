package com.coooolfan.unirhy.model

import org.babyfish.jimmer.sql.Entity
import org.babyfish.jimmer.sql.Id
import org.babyfish.jimmer.sql.Serialized
import tools.jackson.databind.JsonNode
import java.time.Instant

/**
 * 已安装插件。任务身份、并发与表单定义都是"每任务一份"的属性，
 * 保存在 `plugin_task`（由 `PluginTaskStore` 直接访问，不建实体）。
 */
@Entity
interface Plugin {
    /** 插件 ID，即任务命名空间（反向域名） */
    @Id
    val id: String

    val name: String?

    /** 仅用于展示，无版本比较逻辑 */
    val version: String

    val abi: String

    /** 插件级配置声明 `{schema, order}`；实际配置值保存在 `plugin_data` */
    @Serialized
    val configDefinition: JsonNode

    val wasm: ByteArray

    val enabled: Boolean

    val createdAt: Instant

    /** 多节点本地 Registry 的缓存失效标记；安装、覆盖上传、启禁用及并发修改时更新 */
    val updatedAt: Instant
}
