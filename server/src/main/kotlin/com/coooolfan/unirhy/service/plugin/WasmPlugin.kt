package com.coooolfan.unirhy.service.plugin

import com.dylibso.chicory.runtime.ExportFunction
import com.dylibso.chicory.runtime.HostFunction
import com.dylibso.chicory.runtime.ImportValues
import com.dylibso.chicory.runtime.Instance
import com.dylibso.chicory.wasm.Parser
import org.slf4j.LoggerFactory
import java.io.ByteArrayInputStream
import java.nio.file.Files
import java.nio.file.Path

class WasmPlugin private constructor(
    val manifest: PluginManifest,
    private val instance: Instance,
) {
    private val alloc: ExportFunction = instance.export("alloc")
    private val dealloc: ExportFunction = instance.export("dealloc")

    fun plan(paramsJson: ByteArray): List<String> {
        val result = callJson("plan", paramsJson)
        return try {
            val node = tools.jackson.databind.json.JsonMapper().readTree(result)
            node.values().map { it.toString() }
        } catch (ex: Exception) {
            throw WasmPluginException("failed to parse plan() result: ${ex.message}", ex)
        }
    }

    fun run(payloadJson: ByteArray) {
        val export = instance.export("run")
        val len = payloadJson.size
        val ptr = alloc.apply(len.toLong())[0].toInt()
        try {
            instance.memory().write(ptr, payloadJson)
            export.apply(ptr.toLong(), len.toLong())
        } catch (ex: Exception) {
            throw WasmPluginException("plugin run() failed: ${ex.message}", ex)
        } finally {
            dealloc.apply(ptr.toLong(), len.toLong())
        }
    }

    fun callJson(exportName: String, inputJson: ByteArray): ByteArray {
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
        private val logger = LoggerFactory.getLogger(WasmPlugin::class.java)

        fun load(
            manifest: PluginManifest,
            wasmBytes: ByteArray,
            hostFunctionsFactory: (manifest: PluginManifest, instanceRef: () -> Instance) -> List<HostFunction>,
        ): WasmPlugin? {
            val module = try {
                Parser.parse(ByteArrayInputStream(wasmBytes))
            } catch (ex: Exception) {
                logger.warn("Failed to parse wasm bytes for plugin {}: {}", manifest.id, ex.message)
                return null
            }
            val instanceHolder = arrayOfNulls<Instance>(1)
            val hostFunctions =
                hostFunctionsFactory(manifest) { instanceHolder[0] ?: error("plugin instance not initialized yet") }
            val imports = ImportValues.builder().addFunction(*hostFunctions.toTypedArray()).build()
            val instance = try {
                Instance.builder(module).withImportValues(imports).build()
            } catch (ex: Exception) {
                logger.warn("Failed to instantiate wasm for plugin {}: {}", manifest.id, ex.message)
                return null
            }
            instanceHolder[0] = instance
            return WasmPlugin(manifest, instance)
        }

        fun load(
            pluginDir: Path,
            hostFunctionsFactory: (manifest: PluginManifest, instanceRef: () -> Instance) -> List<HostFunction>,
        ): WasmPlugin? {
            val manifestPath = pluginDir.resolve("plugin.yml")
            val wasmPath = pluginDir.resolve("plugin.wasm")
            val manifest = loadPluginManifest(manifestPath) ?: return null
            if (!Files.isRegularFile(wasmPath)) {
                logger.warn("Plugin wasm file not found at {}", wasmPath)
                return null
            }
            val module = try {
                Parser.parse(wasmPath.toFile())
            } catch (ex: Exception) {
                logger.warn("Failed to parse wasm at {}: {}", wasmPath, ex.message)
                return null
            }

            val instanceHolder = arrayOfNulls<Instance>(1)
            val hostFunctions =
                hostFunctionsFactory(manifest) { instanceHolder[0] ?: error("plugin instance not initialized yet") }
            val imports = ImportValues.builder().addFunction(*hostFunctions.toTypedArray()).build()

            val instance = try {
                Instance.builder(module).withImportValues(imports).build()
            } catch (ex: Exception) {
                logger.warn("Failed to instantiate wasm at {}: {}", wasmPath, ex.message)
                return null
            }
            instanceHolder[0] = instance
            return WasmPlugin(manifest, instance)
        }
    }
}
