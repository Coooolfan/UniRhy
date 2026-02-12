package com.unirhy.e2e.support

import com.fasterxml.jackson.databind.JsonNode
import java.net.http.HttpResponse
import kotlin.test.assertEquals
import kotlin.test.assertFalse

object E2eAssert {
    fun status(response: HttpResponse<*>, expected: Int, step: String) {
        assertEquals(expected, response.statusCode(), "$step unexpected http status")
    }

    fun jsonAt(responseBody: String, jsonPointer: String, expected: Any?, step: String) {
        val actualNode = E2eJson.mapper.readTree(responseBody).at(jsonPointer)
        assertFalse(actualNode.isMissingNode, "$step missing json pointer $jsonPointer")
        val expectedNode: JsonNode = E2eJson.mapper.valueToTree(expected)
        assertEquals(expectedNode, actualNode, "$step mismatch at $jsonPointer")
    }

    fun apiError(
        response: HttpResponse<String>,
        family: String,
        code: String,
        expectedStatus: Int? = null,
        step: String,
    ) {
        if (expectedStatus != null) {
            status(response, expectedStatus, step)
        }
        apiError(response.body(), family = family, code = code, step = step)
    }

    fun apiError(
        responseBody: String,
        family: String,
        code: String,
        step: String,
    ) {
        jsonAt(responseBody, "/family", family, step)
        jsonAt(responseBody, "/code", code, step)
    }
}
