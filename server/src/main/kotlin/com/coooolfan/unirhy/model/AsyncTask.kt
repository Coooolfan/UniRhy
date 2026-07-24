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
 * 统一任务资源。根任务承载用户表单参数并通过 Planner 生成子任务，非根任务执行具体载荷。
 * [parent] 既表示产生关系，也决定节点交由 Planner 还是 Handler 处理。
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

    /** 根任务使用入口表单参数，非根任务使用 Planner 或父任务生成的执行载荷。 */
    @Serialized
    val payload: JsonNode

    val status: TaskStatus

    val createdAt: Instant

    val startedAt: Instant?

    val completedAt: Instant?

    val completedReason: String?
}
