package com.unirhy.e2e.support.matrix

import java.nio.file.Path

object ApiCoverageMatrixRenderer {
    fun outputPath(): Path {
        val cwd = Path.of("").toAbsolutePath().normalize()
        return if (cwd.fileName?.toString() == "api-e2e") {
            cwd.resolve("README/API_COVERAGE_MATRIX.md")
        } else {
            cwd.resolve("api-e2e/README/API_COVERAGE_MATRIX.md")
        }
    }

    fun render(
        endpoints: List<ApiEndpoint>,
        coverageByKey: Map<ApiEndpointKey, CoverageMark>,
    ): String {
        val builder = StringBuilder()
        builder.appendLine("# API 覆盖矩阵")
        builder.appendLine()
        builder.appendLine("> 此文件由 `:api-e2e:generateCoverageMatrix` 自动生成，请勿手改。")
        builder.appendLine()
        builder.appendLine("- 生成命令：`cd server && ./gradlew :api-e2e:generateCoverageMatrix`")
        builder.appendLine("- 校验命令：`cd server && ./gradlew :api-e2e:test`")
        builder.appendLine("- 统计口径：按 `HTTP 方法 + Path + headers 条件` 计数（媒体 Range/非Range 分开）。")
        builder.appendLine("- 当前接口总数：`${endpoints.size}`")
        builder.appendLine()
        builder.appendLine("| # | 覆盖级别 | HTTP | Path | 条件 | 需登录 | Controller#method | 用例引用 | 备注 |")
        builder.appendLine("|---|---|---|---|---|---|---|---|---|")

        endpoints.forEachIndexed { index, endpoint ->
            val mark = coverageByKey[endpoint.toKey()] ?: CoverageMark(level = CoverageLevel.TODO)
            val condition = endpoint.condition.ifBlank { "-" }
            val requiresLogin = if (endpoint.requiresLogin) "Y" else "N"
            val testRef = mark.testRef.ifBlank { "-" }
            val note = mark.note.ifBlank { "-" }
            builder.appendLine(
                "| ${index + 1} | ${mark.level.name} | ${endpoint.method} | ${endpoint.path} | $condition | " +
                    "$requiresLogin | ${endpoint.controllerMethod} | $testRef | $note |",
            )
        }
        return builder.toString()
    }
}
