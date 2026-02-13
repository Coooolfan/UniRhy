package com.unirhy.e2e.support

import com.fasterxml.jackson.databind.JsonNode
import java.net.http.HttpResponse
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

object E2eAssert {
    fun status(response: HttpResponse<*>, expected: Int, step: String) {
        assertEquals(expected, response.statusCode(), "$step unexpected http status")
    }

    fun jsonAt(responseBody: String, jsonPointer: String, expected: Any?, step: String) {
        val actualNode = E2eJson.mapper.readTree(responseBody).at(jsonPointer)
        assertFalse(actualNode.isMissingNode, "$step missing json pointer $jsonPointer")
        if (expected is Byte || expected is Short || expected is Int || expected is Long) {
            val expectedLong = (expected as Number).toLong()
            if (actualNode.isIntegralNumber) {
                assertEquals(expectedLong, actualNode.longValue(), "$step mismatch at $jsonPointer")
                return
            }
        }
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

    fun jsonArrayContainsId(responseBody: String, expectedId: Long, step: String) {
        val root = E2eJson.mapper.readTree(responseBody)
        assertTrue(root.isArray, "$step expected array response")
        val exists = root.any { node -> node.path("id").asLong() == expectedId }
        assertTrue(exists, "$step expected id=$expectedId to exist")
    }

    fun jsonArrayNotContainsId(responseBody: String, expectedId: Long, step: String) {
        val root = E2eJson.mapper.readTree(responseBody)
        assertTrue(root.isArray, "$step expected array response")
        val exists = root.any { node -> node.path("id").asLong() == expectedId }
        assertFalse(exists, "$step expected id=$expectedId to be absent")
    }
}
