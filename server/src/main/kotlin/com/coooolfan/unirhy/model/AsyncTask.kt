package com.coooolfan.unirhy.model

import com.coooolfan.unirhy.service.task.common.TaskAction
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
 * 统一任务资源。根任务承载用户表单参数并执行 PLAN，运行中的任务可创建 RUN 子任务。
 * [parent] 仅表示产生关系；每个节点的状态只描述该节点自身的生命周期。
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

    val action: TaskAction

    /** PLAN 使用入口表单参数，RUN 使用 Planner 或父任务生成的执行载荷。 */
    @Serialized
    val payload: JsonNode

    val status: TaskStatus

    val createdAt: Instant

    val startedAt: Instant?

    val completedAt: Instant?

    val completedReason: String?
}
