package com.coooolfan.unirhy.model

import org.babyfish.jimmer.sql.Entity
import org.babyfish.jimmer.sql.Id
import org.babyfish.jimmer.sql.Serialized
import tools.jackson.databind.JsonNode
import java.time.Instant

@Entity
interface Plugin {
    /** 插件 ID，即任务命名空间（反向域名） */
    @Id
    val id: String

    val name: String?

    /** 仅用于展示，无版本比较逻辑 */
    val version: String

    val abi: String

    /** 插件自有任务名段（全大写标识符），`(id, taskType)` 即任务身份二元组 */
    val taskType: String

    /** 当前使用的任务执行并发值；首次安装由 manifest 初始化，之后由管理员直接修改 */
    val concurrency: Int

    /** 完整表单定义 `{schema, order}` */
    @Serialized
    val formDefinition: JsonNode

    val wasm: ByteArray

    val enabled: Boolean

    val createdAt: Instant

    /** 多节点本地 Registry 的缓存失效标记；安装、覆盖上传、启禁用及并发修改时更新 */
    val updatedAt: Instant
}
