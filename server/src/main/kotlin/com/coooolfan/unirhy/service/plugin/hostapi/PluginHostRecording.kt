package com.coooolfan.unirhy.service.plugin.hostapi

import com.coooolfan.unirhy.model.Recording
import com.coooolfan.unirhy.model.by
import com.coooolfan.unirhy.model.dto.RecordingMergeReq
import com.coooolfan.unirhy.service.RecordingService
import org.babyfish.jimmer.Page
import org.babyfish.jimmer.sql.fetcher.Fetcher
import org.babyfish.jimmer.sql.kt.fetcher.newFetcher
import run.endive.runtime.HostFunction
import run.endive.runtime.Instance
import tools.jackson.databind.ObjectMapper

private val HOST_RECORDING_SUMMARY_FETCHER: Fetcher<Recording> = newFetcher(Recording::class).by {
    allScalarFields()
    work {
        allScalarFields()
    }
    artists {
        allScalarFields()
    }
    cover {
        allScalarFields()
    }
}

private val HOST_RECORDING_DETAIL_FETCHER: Fetcher<Recording> = newFetcher(Recording::class).by {
    allScalarFields()
    work {
        allScalarFields()
    }
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
    albums {
        allScalarFields()
        cover {
            allScalarFields()
        }
    }
}

private data class RecordingPageData(
    val rows: List<Recording>,
    val totalRowCount: Long,
)

internal fun buildRecordingHostFunctions(
    recordingService: RecordingService,
    objectMapper: ObjectMapper,
    instanceRef: () -> Instance,
    callExecutor: PluginHostCallExecutor = DIRECT_PLUGIN_HOST_CALL_EXECUTOR,
): List<HostFunction> {
    val support = PluginHostSupport(objectMapper, callExecutor, instanceRef)

    val hostRecordingGet = support.jsonFunction("host_recording_get") { request ->
        recordingService.getRecording(request.requiredLong("id"), HOST_RECORDING_DETAIL_FETCHER)
    }

    val hostRecordingList = support.jsonFunction("host_recording_list") { request ->
        val pageRequest = support.page(request)
        recordingService.listRecordings(
            pageIndex = pageRequest.pageIndex,
            pageSize = pageRequest.pageSize,
            ids = request.optionalLongList("ids"),
            workId = request.optionalLong("workId"),
            fetcher = HOST_RECORDING_SUMMARY_FETCHER,
        ).toHostData()
    }

    val hostRecordingUpdate = support.jsonFunction("host_recording_update") { request ->
        val id = request.requiredLong("id")
        recordingService.getRecording(id, HOST_RECORDING_SUMMARY_FETCHER)
        recordingService.updateRecording(
            Recording {
                this.id = id
                label = request.requiredTextList("label")
                title = request.requiredNullableText("title")
                comment = request.requiredText("comment")
                if (request.has("defaultInWork")) {
                    defaultInWork = request.optionalBoolean("defaultInWork")
                        ?: invalidArgument("Field 'defaultInWork' must be a boolean")
                }
            },
        )
        null
    }

    val hostRecordingMerge = support.jsonFunction("host_recording_merge") { request ->
        val merge = RecordingMergeReq(
            targetId = request.requiredLong("targetId"),
            needMergeIds = request.requiredLongList("needMergeIds").toSet(),
        )
        for (id in merge.needMergeIds + merge.targetId) {
            recordingService.getRecording(id, HOST_RECORDING_SUMMARY_FETCHER)
        }
        recordingService.mergeRecording(merge)
        null
    }

    return listOf(
        hostRecordingGet,
        hostRecordingList,
        hostRecordingUpdate,
        hostRecordingMerge,
    )
}

private fun Page<Recording>.toHostData(): RecordingPageData = RecordingPageData(
    rows = rows,
    totalRowCount = totalRowCount,
)
