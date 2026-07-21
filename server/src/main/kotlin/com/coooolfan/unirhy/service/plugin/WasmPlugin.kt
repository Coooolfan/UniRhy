package com.coooolfan.unirhy.service.plugin

import run.endive.runtime.HostFunction
import run.endive.runtime.ImportValues
import run.endive.runtime.Instance
import run.endive.wasm.Parser
import run.endive.wasm.WasmModule
import tools.jackson.databind.json.JsonMapper
import java.io.ByteArrayInputStream

/**
 * 已加载的 WASM 插件：缓存解析后的 Module，每次 `plan()` / `run()` 调用创建独立 Instance。
 *
 * Instance 不跨调用共享，也不使用 Instance 池；模块声明的线性内存 initial / maximum
 * 原样生效，Host 不施加额外内存 cap，也不设置调用 deadline。
 */
class WasmPlugin private constructor(
    val pluginId: String,
    private val module: WasmModule,
    private val hostFunctionsFactory: (instanceRef: () -> Instance) -> List<HostFunction>,
) {

    /** 将一次表单提交拆分为若干任务载荷 JSON */
    fun plan(paramsJson: ByteArray): List<String> {
        val result = withInstance { instance -> callJson(instance, "plan", paramsJson) }
        return try {
            val node = JsonMapper.shared().readTree(result)
            node.values().map { it.toString() }
        } catch (ex: Exception) {
            throw WasmPluginException("failed to parse plan() result: ${ex.message}", ex)
        }
    }

    /** 执行单个任务载荷 */
    fun run(payloadJson: ByteArray) {
        withInstance { instance ->
            val alloc = instance.export("alloc")
            val dealloc = instance.export("dealloc")
            val len = payloadJson.size
            val ptr = alloc.apply(len.toLong())[0].toInt()
            try {
                instance.memory().write(ptr, payloadJson)
                instance.export("run").apply(ptr.toLong(), len.toLong())
            } catch (ex: Exception) {
                throw WasmPluginException("plugin run() failed: ${ex.message}", ex)
            } finally {
                dealloc.apply(ptr.toLong(), len.toLong())
            }
        }
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
            val outputPtr = (packed ushr 32).toInt()
            val outputLen = (packed and 0xFFFF_FFFFL).toInt()
            if (outputLen < 0) {
                throw WasmPluginException("plugin returned negative output length: $outputLen")
            }
            val outputBytes = instance.memory().readBytes(outputPtr, outputLen)
            if (outputLen > 0) {
                dealloc.apply(outputPtr.toLong(), outputLen.toLong())
            }
            return outputBytes
        } finally {
            dealloc.apply(inputPtr.toLong(), inputLen.toLong())
        }
    }

    companion object {
        private val REQUIRED_EXPORTS = listOf("alloc", "dealloc", "plan", "run")

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
            return plugin
        }
    }
}
