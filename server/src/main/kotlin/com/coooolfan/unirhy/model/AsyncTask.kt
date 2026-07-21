package com.coooolfan.unirhy.model

import com.coooolfan.unirhy.service.task.common.TaskStatus
import org.babyfish.jimmer.sql.Entity
import org.babyfish.jimmer.sql.GeneratedValue
import org.babyfish.jimmer.sql.GenerationType
import org.babyfish.jimmer.sql.Id
import org.babyfish.jimmer.sql.IdView
import org.babyfish.jimmer.sql.ManyToOne
import org.babyfish.jimmer.sql.OnDissociate
import org.babyfish.jimmer.sql.DissociateAction
import org.babyfish.jimmer.sql.Serialized
import tools.jackson.databind.JsonNode
import java.time.Instant

/**
 * 单条可排队、claim、完成、失败、取消和重新排队的执行任务资源。
 *
 * `namespace/taskType` 为执行与索引反规范化保存，必须与父 submission 一致。
 */
@Entity
interface AsyncTask {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long

    @ManyToOne
    @OnDissociate(DissociateAction.DELETE)
    val submission: TaskSubmission

    @IdView
    val submissionId: Long

    val namespace: String

    val taskType: String

    /** Planner 产生的任务载荷，允许任意合法 JSON 值 */
    @Serialized
    val payload: JsonNode

    val status: TaskStatus

    val createdAt: Instant

    val startedAt: Instant?

    val completedAt: Instant?

    val completedReason: String?
}
