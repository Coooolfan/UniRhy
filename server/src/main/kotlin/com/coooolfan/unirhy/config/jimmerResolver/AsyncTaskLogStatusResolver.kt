package com.coooolfan.unirhy.config.jimmerResolver

import com.coooolfan.unirhy.service.task.common.AsyncTaskManager
import org.babyfish.jimmer.sql.kt.KTransientResolver
import org.springframework.stereotype.Component

@Component
class AsyncTaskLogStatusResolver(
    private val asyncTaskManager: AsyncTaskManager
) : KTransientResolver<Long, Boolean> {

    override fun resolve(ids: Collection<Long>): Map<Long, Boolean> {
        val runningLogIds = asyncTaskManager.listRunningLogIds()
        return ids.associateWith { logId -> logId in runningLogIds }
    }
}