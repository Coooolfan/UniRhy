package com.coooolfan.unirhy.model

import com.coooolfan.unirhy.service.task.common.TaskStatus
import org.babyfish.jimmer.sql.DissociateAction
import org.babyfish.jimmer.sql.Entity
import org.babyfish.jimmer.sql.GeneratedValue
import org.babyfish.jimmer.sql.GenerationType
import org.babyfish.jimmer.sql.Id
import org.babyfish.jimmer.sql.IdView
import org.babyfish.jimmer.sql.JoinColumn
import org.babyfish.jimmer.sql.ManyToOne
import org.babyfish.jimmer.sql.OnDissociate
import org.babyfish.jimmer.sql.OneToMany
import org.babyfish.jimmer.sql.Serialized
import tools.jackson.databind.JsonNode
import java.time.Instant

/**
 * 统一任务资源。所有任务节点同构：由 `(namespace, taskType)` 对应的 Executor 执行，
 * Executor 返回的后继被入队为子任务，返回空序列即叶子。
 * [parent] 只表示产生关系，不参与执行角色判定。
 */
@Entity
interface AsyncTask {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long

    @ManyToOne
    @JoinColumn(name = "parent_task_id")
    @OnDissociate(DissociateAction.DELETE)
    val parent: AsyncTask?

    @IdView
    val parentId: Long?

    @OneToMany(mappedBy = "parent")
    val childTasks: List<AsyncTask>

    val namespace: String

    val taskType: String

    /** 入口任务使用用户表单参数，其余任务使用上游 Executor 生成的执行载荷。 */
    @Serialized
    val payload: JsonNode

    val status: TaskStatus

    val createdAt: Instant

    val startedAt: Instant?

    val completedAt: Instant?

    val completedReason: String?
}
