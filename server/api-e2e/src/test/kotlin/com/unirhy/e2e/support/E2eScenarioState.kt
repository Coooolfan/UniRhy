package com.unirhy.e2e.support

import com.fasterxml.jackson.databind.JsonNode

class E2eScenarioState(
    val runtime: E2eRunContext,
    val scanSample: ScanSample,
    val api: E2eHttpClient,
) {
    private val idStore = linkedMapOf<String, Long>()
    private val jsonStore = linkedMapOf<String, JsonNode>()

    fun putId(key: String, value: Long) {
        idStore[key] = value
    }

    fun requireId(key: String): Long {
        return requireNotNull(idStore[key]) { "id key not found: $key" }
    }

    fun putJson(key: String, value: JsonNode) {
        jsonStore[key] = value
    }

    fun requireJson(key: String): JsonNode {
        return requireNotNull(jsonStore[key]) { "json key not found: $key" }
    }

    companion object {
        const val KEY_MEDIA_ID = "mediaId"
        const val KEY_WORK_LIST = "workList"
    }
}
