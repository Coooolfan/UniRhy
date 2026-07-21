package com.coooolfan.unirhy.service.task

import com.coooolfan.unirhy.service.task.common.TaskKey
import tools.jackson.databind.JsonNode
import tools.jackson.databind.json.JsonMapper

/**
 * 内建任务常量：TaskKey、执行并发值与静态表单定义。
 *
 * 内建任务不创建伪插件记录，定义留在服务端。
 */
object BuiltInTasks {

    val METADATA_PARSE = TaskKey(TaskKey.BUILT_IN_NAMESPACE, "METADATA_PARSE")
    val TRANSCODE = TaskKey(TaskKey.BUILT_IN_NAMESPACE, "TRANSCODE")

    const val METADATA_PARSE_CONCURRENCY = 10
    const val TRANSCODE_CONCURRENCY = 1

    const val METADATA_PARSE_NAME = "元数据解析"
    const val TRANSCODE_NAME = "音频转码"

    val METADATA_PARSE_FORM: JsonNode = JsonMapper.shared().readTree(
        """
        {
          "schema": {
            "type": "object",
            "properties": {
              "providerType": {
                "type": "string",
                "title": "存储节点类型",
                "enum": ["FILE_SYSTEM", "OSS"]
              },
              "providerId": {
                "type": "integer",
                "title": "存储节点 ID"
              }
            },
            "required": ["providerType", "providerId"],
            "additionalProperties": false
          },
          "order": ["providerType", "providerId"]
        }
        """.trimIndent()
    )

    val TRANSCODE_FORM: JsonNode = JsonMapper.shared().readTree(
        """
        {
          "schema": {
            "type": "object",
            "properties": {
              "srcProviderType": {
                "type": "string",
                "title": "源存储节点类型",
                "enum": ["FILE_SYSTEM", "OSS"]
              },
              "srcProviderId": {
                "type": "integer",
                "title": "源存储节点 ID"
              },
              "dstProviderType": {
                "type": "string",
                "title": "目标存储节点类型",
                "enum": ["FILE_SYSTEM", "OSS"]
              },
              "dstProviderId": {
                "type": "integer",
                "title": "目标存储节点 ID"
              },
              "targetCodec": {
                "type": "string",
                "title": "目标编码",
                "enum": ["OPUS"],
                "default": "OPUS"
              }
            },
            "required": ["srcProviderType", "srcProviderId", "dstProviderType", "dstProviderId"],
            "additionalProperties": false
          },
          "order": ["srcProviderType", "srcProviderId", "dstProviderType", "dstProviderId", "targetCodec"]
        }
        """.trimIndent()
    )
}
