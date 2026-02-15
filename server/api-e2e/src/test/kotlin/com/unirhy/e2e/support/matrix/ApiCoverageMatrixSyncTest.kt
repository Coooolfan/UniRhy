package com.unirhy.e2e.support.matrix

import org.junit.jupiter.api.Test
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ApiCoverageMatrixSyncTest {
    @Test
    fun `coverage matrix should be synced`() {
        val endpoints = ApiEndpointScanner.scan()
        // Snapshot guard: when controller endpoints change intentionally, update this constant and regenerate matrix.
        assertEquals(
            EXPECTED_ENDPOINT_COUNT,
            endpoints.size,
            "controller endpoint count changed, please review matrix rules",
        )

        val endpointKeys = endpoints.map { it.toKey() }.toSet()
        val unknownKeys = ApiCoverageRegistry.coverageByKey.keys - endpointKeys
        assertTrue(
            unknownKeys.isEmpty(),
            "coverage registry contains unknown endpoints: ${unknownKeys.joinToString()}",
        )

        val rendered = ApiCoverageMatrixRenderer.render(
            endpoints = endpoints,
            coverageByKey = ApiCoverageRegistry.coverageByKey,
        )
        val outputPath = ApiCoverageMatrixRenderer.outputPath()

        if (writeEnabled()) {
            Files.createDirectories(outputPath.parent)
            Files.writeString(outputPath, rendered, StandardCharsets.UTF_8)
            return
        }

        assertTrue(
            Files.exists(outputPath),
            "coverage matrix file does not exist: $outputPath, run ./gradlew :api-e2e:generateCoverageMatrix",
        )
        val existing = Files.readString(outputPath, StandardCharsets.UTF_8)
        assertEquals(
            normalizeLineEnding(rendered),
            normalizeLineEnding(existing),
            "coverage matrix is outdated, run ./gradlew :api-e2e:generateCoverageMatrix",
        )
    }

    private fun writeEnabled(): Boolean {
        return System.getProperty("e2e.coverage.write", "false").toBooleanStrictOrNull() ?: false
    }

    private fun normalizeLineEnding(content: String): String {
        return content.replace("\r\n", "\n")
    }

    companion object {
        private const val EXPECTED_ENDPOINT_COUNT = 44
    }
}
