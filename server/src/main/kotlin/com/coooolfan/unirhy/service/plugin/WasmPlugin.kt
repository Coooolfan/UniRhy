package com.coooolfan.unirhy.service.plugin

import run.endive.runtime.HostFunction
import run.endive.runtime.ImportValues
import run.endive.runtime.Instance
import run.endive.wasm.Parser
import run.endive.wasm.WasmModule
import run.endive.wasm.types.FunctionType
import run.endive.wasm.types.ValType
import tools.jackson.databind.JsonNode
import tools.jackson.databind.json.JsonMapper
import java.io.ByteArrayInputStream

/** `execute()` 返回的后继任务；[namespace] 缺省时表示插件自身命名空间 */
data class WasmSuccessor(
    val namespace: String?,
    val taskType: String,
    val payload: JsonNode,
)

/**
 * 已加载的 WASM 插件：缓存解析后的 Module，每次 `execute()` 调用创建独立 Instance。
 *
 * Instance 不跨调用共享，也不使用 Instance 池；模块声明的线性内存 initial / maximum
 * 原样生效，Host 不施加额外内存 cap，也不设置调用 deadline。
 */
class WasmPlugin private constructor(
    val pluginId: String,
    private val module: WasmModule,
    private val hostFunctionsFactory: (instanceRef: () -> Instance) -> List<HostFunction>,
) {

    /**
     * 执行单个任务节点。入口任务与工作任务走同一导出函数，插件按 `taskType` 自行分发。
     *
     * 入参信封：`{"taskId": 1, "taskType": "...", "payload": {...}}`
     * 出参信封：`{"ok": true, "successors": [{"namespace"?: "...", "taskType": "...", "payload": {...}}]}`
     * 或 `{"ok": false, "error": "..."}`。`successors` 缺省或为空即叶子任务。
     */
    fun execute(taskId: Long, taskType: String, payloadJson: ByteArray): List<WasmSuccessor> {
        val mapper = JsonMapper.shared()
        val envelope = mapper.createObjectNode()
        envelope.put("taskId", taskId)
        envelope.put("taskType", taskType)
        envelope.replace("payload", mapper.readTree(payloadJson))
        val output = withInstance { instance ->
            callJson(instance, "execute", envelope.toString().toByteArray(Charsets.UTF_8))
        }
        val result = try {
            mapper.readTree(output)
        } catch (ex: Exception) {
            throw WasmPluginException("failed to parse execute() result: ${ex.message}", ex)
        }
        val ok = result.get("ok")?.takeIf { it.isBoolean }?.booleanValue()
            ?: throw WasmPluginException("plugin execute() result must contain boolean field 'ok'")
        if (!ok) {
            val error = result.get("error")?.takeIf { it.isString }?.stringValue()?.trim()
            if (error.isNullOrEmpty()) {
                throw WasmPluginException("plugin execute() failed without an error message")
            }
            throw WasmPluginException("plugin execute() failed: $error")
        }
        return parseSuccessors(result.get("successors"))
    }

    private fun parseSuccessors(node: JsonNode?): List<WasmSuccessor> {
        if (node == null || node.isNull) return emptyList()
        if (!node.isArray) {
            throw WasmPluginException("plugin execute() field 'successors' must be an array")
        }
        val successors = ArrayList<WasmSuccessor>()
        for (item in node.values()) {
            if (!item.isObject) {
                throw WasmPluginException("plugin execute() successor must be an object")
            }
            val successorType = item.get("taskType")?.takeIf { it.isString }?.stringValue()
                ?: throw WasmPluginException("plugin execute() successor must contain string field 'taskType'")
            val payload = item.get("payload")?.takeIf { it.isObject }
                ?: throw WasmPluginException("plugin execute() successor must contain object field 'payload'")
            successors += WasmSuccessor(
                namespace = item.get("namespace")?.takeIf { it.isString }?.stringValue(),
                taskType = successorType,
                payload = payload,
            )
        }
        return successors
    }

    private fun <T> withInstance(block: (Instance) -> T): T = block(newInstance())

    private fun newInstance(): Instance {
        val instanceHolder = arrayOfNulls<Instance>(1)
        val hostFunctions =
            hostFunctionsFactory { instanceHolder[0] ?: error("plugin instance not initialized yet") }
        val imports = ImportValues.builder().addFunction(*hostFunctions.toTypedArray()).build()
        val instance = try {
            Instance.builder(module).withImportValues(imports).build()
        } catch (ex: Exception) {
            throw WasmPluginException("failed to instantiate wasm for plugin $pluginId: ${ex.message}", ex)
        }
        instanceHolder[0] = instance
        return instance
    }

    private fun callJson(instance: Instance, exportName: String, inputJson: ByteArray): ByteArray {
        val alloc = instance.export("alloc")
        val dealloc = instance.export("dealloc")
        val export = instance.export(exportName)
        val inputLen = inputJson.size
        val inputPtr = alloc.apply(inputLen.toLong())[0].toInt()
        try {
            instance.memory().write(inputPtr, inputJson)
            val packed = export.apply(inputPtr.toLong(), inputLen.toLong())[0]
            return readPackedOutput(instance, packed)
        } finally {
            dealloc.apply(inputPtr.toLong(), inputLen.toLong())
        }
    }

    private fun readPackedOutput(instance: Instance, packed: Long): ByteArray {
        val outputPtr = (packed ushr 32).toInt()
        val outputLen = (packed and 0xFFFF_FFFFL).toInt()
        if (outputLen < 0) {
            throw WasmPluginException("plugin returned negative output length: $outputLen")
        }
        val outputBytes = instance.memory().readBytes(outputPtr, outputLen)
        if (outputLen > 0) {
            instance.export("dealloc").apply(outputPtr.toLong(), outputLen.toLong())
        }
        return outputBytes
    }

    companion object {
        private val REQUIRED_EXPORTS = listOf("alloc", "dealloc", "execute")
        private val EXECUTE_FUNCTION_TYPE = FunctionType.of(
            arrayOf(ValType.I32, ValType.I32),
            arrayOf(ValType.I64),
        )

        /** 仅解析模块字节，用于上传时的格式检查 */
        fun parseModule(wasmBytes: ByteArray): WasmModule =
            try {
                Parser.parse(ByteArrayInputStream(wasmBytes))
            } catch (ex: Exception) {
                throw WasmPluginException("failed to parse wasm module: ${ex.message}", ex)
            }

        /**
         * 解析、实例化并校验导出函数；任一步失败抛出 [WasmPluginException]。
         * 校验用 Instance 即弃，后续调用各自创建新 Instance。
         */
        fun load(
            pluginId: String,
            wasmBytes: ByteArray,
            hostFunctionsFactory: (instanceRef: () -> Instance) -> List<HostFunction>,
        ): WasmPlugin {
            val module = parseModule(wasmBytes)
            val plugin = WasmPlugin(pluginId, module, hostFunctionsFactory)
            val probeInstance = plugin.newInstance()
            for (exportName in REQUIRED_EXPORTS) {
                try {
                    probeInstance.export(exportName)
                } catch (ex: Exception) {
                    throw WasmPluginException("plugin $pluginId missing required export: $exportName", ex)
                }
            }
            val executeType = probeInstance.exportType("execute")
            if (!executeType.typesMatch(EXECUTE_FUNCTION_TYPE)) {
                throw WasmPluginException(
                    "plugin $pluginId export 'execute' must have signature (i32, i32) -> i64, got $executeType",
                )
            }
            return plugin
        }
    }
}
