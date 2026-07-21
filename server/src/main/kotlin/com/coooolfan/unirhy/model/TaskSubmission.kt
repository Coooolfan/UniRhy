package com.coooolfan.unirhy.model

import com.coooolfan.unirhy.service.task.common.TaskStatus
import org.babyfish.jimmer.sql.Entity
import org.babyfish.jimmer.sql.GeneratedValue
import org.babyfish.jimmer.sql.GenerationType
import org.babyfish.jimmer.sql.Id
import org.babyfish.jimmer.sql.OneToMany
import org.babyfish.jimmer.sql.Serialized
import tools.jackson.databind.JsonNode
import java.time.Instant

/**
 * 一次用户任务触发的规划资源；规划产生的 [AsyncTask] 与之为一对多关系。
 *
 * 状态只描述规划与任务投递结果，不聚合子任务执行结果。
 */
@Entity
interface TaskSubmission {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long

    val namespace: String

    val taskType: String

    /** 统一提交请求的参数，根 JSON Object */
    @Serialized
    val params: JsonNode

    val status: TaskStatus

    val createdAt: Instant

    val startedAt: Instant?

    val completedAt: Instant?

    val completedReason: String?

    @OneToMany(mappedBy = "submission")
    val tasks: List<AsyncTask>
}
