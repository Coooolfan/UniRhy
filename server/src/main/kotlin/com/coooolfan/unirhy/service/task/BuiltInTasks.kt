package com.coooolfan.unirhy.service.task

import com.coooolfan.unirhy.service.task.common.TaskKey
import tools.jackson.databind.JsonNode
import tools.jackson.databind.json.JsonMapper

/**
 * 内建任务常量：TaskKey、执行并发值与静态表单定义。
 *
 * 内建任务不创建伪插件记录，定义留在服务端。
 *
 * 每个任务族拆成两个 key：入口 key 可被用户从表单投递，负责展开出工作单元；
 * 工作 key 只由入口任务产出，负责消费单个单元。本地能力门控只作用于工作 key，
 * 因此任意节点都能规划，只有具备能力的节点执行。
 */
object BuiltInTasks {

    val METADATA_PARSE = TaskKey(TaskKey.BUILT_IN_NAMESPACE, "METADATA_PARSE")
    val METADATA_PARSE_ITEM = TaskKey(TaskKey.BUILT_IN_NAMESPACE, "METADATA_PARSE_ITEM")
    val TRANSCODE = TaskKey(TaskKey.BUILT_IN_NAMESPACE, "TRANSCODE")
    val TRANSCODE_ITEM = TaskKey(TaskKey.BUILT_IN_NAMESPACE, "TRANSCODE_ITEM")

    /** 入口任务只做 resolve、遍历与批量插入，无需高并发 */
    const val ENTRY_CONCURRENCY = 2

    const val METADATA_PARSE_ITEM_CONCURRENCY = 10
    const val TRANSCODE_ITEM_CONCURRENCY = 1

    const val METADATA_PARSE_NAME = "资产扫描"
    const val METADATA_PARSE_ITEM_NAME = "元数据解析"
    const val TRANSCODE_NAME = "音频转码扫描"
    const val TRANSCODE_ITEM_NAME = "音频转码"

    /** 可被用户从表单投递的入口任务 */
    val ENTRY_KEYS = listOf(METADATA_PARSE, TRANSCODE)

    val ALL_KEYS = listOf(METADATA_PARSE, METADATA_PARSE_ITEM, TRANSCODE, TRANSCODE_ITEM)

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

    /** 工作任务的 payload 契约，不作为用户表单渲染；跨 key 派活时用于校验 */
    val METADATA_PARSE_ITEM_FORM: JsonNode = JsonMapper.shared().readTree(
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
              },
              "objectKey": {
                "type": "string",
                "title": "对象键"
              }
            },
            "required": ["providerType", "providerId", "objectKey"],
            "additionalProperties": false
          },
          "order": ["providerType", "providerId", "objectKey"]
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

    /** 工作任务的 payload 契约，不作为用户表单渲染；跨 key 派活时用于校验 */
    val TRANSCODE_ITEM_FORM: JsonNode = JsonMapper.shared().readTree(
        """
        {
          "schema": {
            "type": "object",
            "properties": {
              "recordingId": {
                "type": "integer",
                "title": "录音 ID"
              },
              "srcObjectKey": {
                "type": "string",
                "title": "源对象键"
              },
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
            "required": ["recordingId", "srcObjectKey", "srcProviderId", "dstProviderId"],
            "additionalProperties": false
          },
          "order": [
            "recordingId", "srcObjectKey", "srcProviderType", "srcProviderId",
            "dstProviderType", "dstProviderId", "targetCodec"
          ]
        }
        """.trimIndent()
    )
}
