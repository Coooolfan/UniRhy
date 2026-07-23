package com.coooolfan.unirhy.service.plugin

import run.endive.runtime.HostFunction
import run.endive.runtime.ImportValues
import run.endive.runtime.Instance
import run.endive.wasm.Parser
import run.endive.wasm.WasmModule
import run.endive.wasm.types.FunctionType
import run.endive.wasm.types.ValType
import tools.jackson.databind.json.JsonMapper
import java.io.ByteArrayInputStream

data class WasmExecutionContext(
    val taskId: Long,
    val taskType: String,
)

/**
 * 已加载的 WASM 插件：缓存解析后的 Module，每次 `plan()` / `run()` 调用创建独立 Instance。
 *
 * Instance 不跨调用共享，也不使用 Instance 池；模块声明的线性内存 initial / maximum
 * 原样生效，Host 不施加额外内存 cap，也不设置调用 deadline。
 */
class WasmPlugin private constructor(
    val pluginId: String,
    private val module: WasmModule,
    private val hostFunctionsFactory: (
        instanceRef: () -> Instance,
        executionContext: WasmExecutionContext?,
    ) -> List<HostFunction>,
) {

    /** 将一次表单提交拆分为若干任务载荷 JSON */
    fun plan(paramsJson: ByteArray): List<String> {
        val result = withInstance(null) { instance -> callJson(instance, "plan", paramsJson) }
        return try {
            val node = JsonMapper.shared().readTree(result)
            node.values().map { it.toString() }
        } catch (ex: Exception) {
            throw WasmPluginException("failed to parse plan() result: ${ex.message}", ex)
        }
    }

    /** 执行单个任务载荷并读取结果信封。 */
    fun run(taskId: Long, taskType: String, payloadJson: ByteArray) {
        withInstance(WasmExecutionContext(taskId, taskType)) { instance ->
            val alloc = instance.export("alloc")
            val dealloc = instance.export("dealloc")
            val len = payloadJson.size
            val ptr = alloc.apply(len.toLong())[0].toInt()
            val results = try {
                instance.memory().write(ptr, payloadJson)
                instance.export("run").apply(ptr.toLong(), len.toLong())
            } catch (ex: Exception) {
                throw WasmPluginException("plugin run() failed: ${ex.message}", ex)
            } finally {
                dealloc.apply(ptr.toLong(), len.toLong())
            }
            if (results.size != 1) {
                throw WasmPluginException("plugin run() returned ${results.size} values; expected one")
            }
            val output = readPackedOutput(instance, results[0])
            val result = try {
                JsonMapper.shared().readTree(output)
            } catch (ex: Exception) {
                throw WasmPluginException("failed to parse run() result: ${ex.message}", ex)
            }
            val ok = result.get("ok")?.takeIf { it.isBoolean }?.booleanValue()
                ?: throw WasmPluginException("plugin run() result must contain boolean field 'ok'")
            if (!ok) {
                val error = result.get("error")?.takeIf { it.isString }?.stringValue()?.trim()
                if (error.isNullOrEmpty()) {
                    throw WasmPluginException("plugin run() failed without an error message")
                }
                throw WasmPluginException("plugin run() failed: $error")
            }
        }
    }

    private fun <T> withInstance(context: WasmExecutionContext?, block: (Instance) -> T): T =
        block(newInstance(context))

    private fun newInstance(context: WasmExecutionContext? = null): Instance {
        val instanceHolder = arrayOfNulls<Instance>(1)
        val hostFunctions =
            hostFunctionsFactory(
                { instanceHolder[0] ?: error("plugin instance not initialized yet") },
                context,
            )
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
        private val REQUIRED_EXPORTS = listOf("alloc", "dealloc", "plan", "run")
        private val RUN_FUNCTION_TYPE = FunctionType.of(
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
            hostFunctionsFactory: (
                instanceRef: () -> Instance,
                executionContext: WasmExecutionContext?,
            ) -> List<HostFunction>,
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
            val runType = probeInstance.exportType("run")
            if (!runType.typesMatch(RUN_FUNCTION_TYPE)) {
                throw WasmPluginException(
                    "plugin $pluginId export 'run' must have signature (i32, i32) -> i64, got $runType",
                )
            }
            return plugin
        }
    }
}
