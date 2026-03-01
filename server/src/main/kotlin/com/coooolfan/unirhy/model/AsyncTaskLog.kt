package com.coooolfan.unirhy.model

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

    val startedAt: Instant

    val completedAt: Instant?

    val params: String

    val completedReason: String?
}