package com.coooolfan.unirhy.model

import com.coooolfan.unirhy.service.task.common.TaskStatus
import com.coooolfan.unirhy.service.task.common.TaskType
import org.babyfish.jimmer.sql.Entity
import org.babyfish.jimmer.sql.GeneratedValue
import org.babyfish.jimmer.sql.GenerationType
import org.babyfish.jimmer.sql.Id
import java.time.Instant

@Entity
interface AsyncTaskLog {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long

    val taskType: TaskType

    val createdAt: Instant

    val startedAt: Instant?

    val completedAt: Instant?

    val params: String

    val completedReason: String?

    val status: TaskStatus

    // TODO: 加个字段用于标记现在在哪个节点上被消费
}
