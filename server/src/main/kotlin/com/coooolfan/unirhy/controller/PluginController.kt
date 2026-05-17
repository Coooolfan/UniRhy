package com.coooolfan.unirhy.controller

import cn.dev33.satoken.annotation.SaCheckLogin
import com.coooolfan.unirhy.service.PluginService
import com.coooolfan.unirhy.service.plugin.PluginForm
import com.coooolfan.unirhy.service.plugin.PluginFormField
import com.coooolfan.unirhy.service.task.PluginTaskService
import com.coooolfan.unirhy.service.task.common.TaskType
import jakarta.servlet.http.HttpServletResponse
import org.springframework.http.HttpStatus
import com.fasterxml.jackson.databind.ObjectMapper
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.multipart.MultipartFile
import org.springframework.web.server.ResponseStatusException

data class PluginInfoResponse(
    val id: String,
    val name: String?,
    val version: String,
    val taskType: TaskType,
    val extension: String,
    val isAvailable: Boolean,
    val enabled: Boolean,
    val form: PluginForm,
)

@RestController
@RequestMapping("/api/plugins")
@SaCheckLogin
class PluginController(
    private val pluginService: PluginService,
    private val pluginTaskService: PluginTaskService,
    private val objectMapper: ObjectMapper,
) {
    @GetMapping
    fun listPlugins(): List<PluginInfoResponse> {
        val loadedTaskTypes = pluginTaskService.getLoadedTaskTypes()
        return pluginService.listPlugins().map { plugin ->
            val taskType = runCatching { TaskType.valueOf(plugin.taskType) }.getOrNull()
            PluginInfoResponse(
                id = plugin.id,
                name = plugin.name,
                version = plugin.version,
                taskType = taskType ?: TaskType.ARTIST_NORMALIZATION,
                extension = plugin.extension,
                isAvailable = taskType != null && taskType in loadedTaskTypes,
                enabled = plugin.enabled,
                form = PluginForm(fields = pluginService.parseFormFields(plugin)),
            )
        }
    }

    @PostMapping("/upload", consumes = ["multipart/form-data"])
    @ResponseStatus(HttpStatus.CREATED)
    fun upload(@RequestParam("file") file: MultipartFile) {
        pluginService.upload(file)
    }

    @PutMapping("/{id}/enabled")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun setEnabled(@PathVariable id: String, @RequestParam enabled: Boolean) {
        pluginService.setEnabled(id, enabled)
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun delete(@PathVariable id: String) {
        pluginService.delete(id)
    }

    @GetMapping("/{id}/download")
    fun download(@PathVariable id: String, response: HttpServletResponse) {
        val plugin = pluginService.getPlugin(id)
        val zipBytes = pluginService.export(id)
        response.contentType = "application/octet-stream"
        response.setHeader("Content-Disposition", "attachment; filename=\"${plugin.id}-${plugin.version}.up\"")
        response.outputStream.write(zipBytes)
    }

    @PostMapping("/{taskType}/submit")
    @ResponseStatus(HttpStatus.ACCEPTED)
    fun submitPluginTask(
        @PathVariable taskType: String,
        @RequestBody params: Map<String, String>,
    ) {
        val type = runCatching { TaskType.valueOf(taskType) }.getOrElse {
            throw ResponseStatusException(HttpStatus.BAD_REQUEST, "unknown task type: $taskType")
        }
        try {
            pluginTaskService.submit(type, objectMapper.writeValueAsBytes(params))
        } catch (ex: IllegalArgumentException) {
            throw ResponseStatusException(HttpStatus.BAD_REQUEST, ex.message)
        }
    }
}
