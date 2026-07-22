package com.coooolfan.unirhy.service.plugin.hostapi

import com.coooolfan.unirhy.model.Work
import com.coooolfan.unirhy.model.by
import com.coooolfan.unirhy.model.dto.WorkMergeReq
import com.coooolfan.unirhy.service.WorkService
import org.babyfish.jimmer.Page
import org.babyfish.jimmer.sql.exception.EmptyResultException
import org.babyfish.jimmer.sql.fetcher.Fetcher
import org.babyfish.jimmer.sql.kt.fetcher.newFetcher
import run.endive.runtime.HostFunction
import run.endive.runtime.Instance
import tools.jackson.databind.ObjectMapper

private val HOST_WORK_LIST_FETCHER: Fetcher<Work> = newFetcher(Work::class).by {
    allScalarFields()
    recordings {
        allScalarFields()
        artists {
            allScalarFields()
        }
        cover {
            allScalarFields()
        }
    }
}

private val HOST_WORK_DETAIL_FETCHER: Fetcher<Work> = newFetcher(Work::class).by {
    allScalarFields()
    recordings {
        allScalarFields()
        artists {
            allScalarFields()
        }
        cover {
            allScalarFields()
        }
        assets {
            allScalarFields()
            mediaFile {
                allScalarFields()
            }
        }
    }
}

private data class WorkPageData(
    val rows: List<Work>,
    val totalRowCount: Long,
)

internal fun buildWorkHostFunctions(
    workService: WorkService,
    objectMapper: ObjectMapper,
    instanceRef: () -> Instance,
    callExecutor: PluginHostCallExecutor = DIRECT_PLUGIN_HOST_CALL_EXECUTOR,
): List<HostFunction> {
    val support = PluginHostSupport(objectMapper, callExecutor, instanceRef)

    val hostWorkList = support.jsonFunction("host_work_list") { request ->
        val pageRequest = support.page(request)
        workService.listWork(pageRequest.pageIndex, pageRequest.pageSize, HOST_WORK_LIST_FETCHER).toHostData()
    }

    val hostWorkGet = support.jsonFunction("host_work_get") { request ->
        findWork(workService, request.requiredLong("id"), HOST_WORK_DETAIL_FETCHER)
    }

    val hostWorkSearch = support.jsonFunction("host_work_search") { request ->
        workService.getWorkByName(request.requiredText("name"), HOST_WORK_DETAIL_FETCHER)
    }

    val hostWorkRandom = support.jsonFunction("host_work_random") { request ->
        workService.randomWork(
            timestamp = request.optionalLong("timestamp"),
            length = request.optionalLong("length"),
            offset = request.optionalLong("offset"),
            fetcher = HOST_WORK_DETAIL_FETCHER,
        ) ?: notFound("No work is available")
    }

    val hostWorkUpdate = support.jsonFunction("host_work_update") { request ->
        val id = request.requiredLong("id")
        val current = findWork(workService, id, HOST_WORK_DETAIL_FETCHER)
        if (!request.has("title")) {
            current
        } else {
            workService.updateWork(
                Work {
                    this.id = id
                    title = request.requiredText("title")
                },
                HOST_WORK_DETAIL_FETCHER,
            )
        }
    }

    val hostWorkDelete = support.jsonFunction("host_work_delete") { request ->
        val id = request.requiredLong("id")
        findWork(workService, id, HOST_WORK_LIST_FETCHER)
        workService.deleteWork(id)
        null
    }

    val hostWorkMerge = support.jsonFunction("host_work_merge") { request ->
        val merge = WorkMergeReq(
            targetId = request.requiredLong("targetId"),
            needMergeIds = request.requiredLongList("needMergeIds").toSet(),
        )
        for (id in merge.needMergeIds + merge.targetId) {
            findWork(workService, id, HOST_WORK_LIST_FETCHER)
        }
        workService.mergeWork(merge)
        null
    }

    return listOf(
        hostWorkList,
        hostWorkGet,
        hostWorkSearch,
        hostWorkRandom,
        hostWorkUpdate,
        hostWorkDelete,
        hostWorkMerge,
    )
}

private fun Page<Work>.toHostData(): WorkPageData = WorkPageData(
    rows = rows,
    totalRowCount = totalRowCount,
)

private fun findWork(workService: WorkService, id: Long, fetcher: Fetcher<Work>): Work =
    try {
        workService.getWorkById(id, fetcher)
    } catch (_: EmptyResultException) {
        notFound("Work not found: $id")
    }
