package com.coooolfan.unirhy.controller

import cn.dev33.satoken.annotation.SaCheckLogin
import cn.dev33.satoken.annotation.SaCheckRole
import com.coooolfan.unirhy.config.ROLE_ADMIN
import com.coooolfan.unirhy.error.CommonException
import com.coooolfan.unirhy.error.PluginException
import com.coooolfan.unirhy.service.PluginService
import com.coooolfan.unirhy.service.PluginService.Companion.resolvedTaskType
import com.coooolfan.unirhy.service.plugin.PluginForm
import com.coooolfan.unirhy.service.plugin.PluginFormField
import com.coooolfan.unirhy.service.task.PluginTaskService
import com.coooolfan.unirhy.service.task.common.TaskType
import jakarta.servlet.http.HttpServletResponse
import org.springframework.http.HttpStatus
import tools.jackson.databind.ObjectMapper
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

data class PluginInfoResponse(
    val id: String,
    val name: String?,
    val version: String,
    val taskType: TaskType?,
    val extension: String,
    val isAvailable: Boolean,
    val enabled: Boolean,
    val form: PluginForm,
)

/**
 * 插件管理接口
 *
 * 提供插件的上传、启停、删除、导出与插件任务提交能力
 */
@RestController
@RequestMapping("/api")
@SaCheckLogin
class PluginController(
    private val pluginService: PluginService,
    private val pluginTaskService: PluginTaskService,
    private val objectMapper: ObjectMapper,
) {
    /**
     * 获取插件列表
     *
     * 此接口用于获取系统中已安装的全部插件信息，并标记插件是否已加载可用
     * 需要用户登录认证才能访问
     *
     * @return List<PluginInfoResponse> 返回插件信息列表
     *
     * @api GET /api/plugins
     * @permission 需要登录认证
     * @description 调用PluginService.listPlugins()方法获取插件列表
     */
    @GetMapping("/plugins")
    fun listPlugins(): List<PluginInfoResponse> {
        val loadedTaskTypes = pluginTaskService.getLoadedTaskTypes()
        return pluginService.listPlugins().map { plugin ->
            val taskType = plugin.resolvedTaskType()
            PluginInfoResponse(
                id = plugin.id,
                name = plugin.name,
                version = plugin.version,
                taskType = taskType,
                extension = plugin.extension,
                isAvailable = taskType != null && taskType in loadedTaskTypes,
                enabled = plugin.enabled,
                form = PluginForm(fields = pluginService.parseFormFields(plugin)),
            )
        }
    }

    /**
     * 上传插件
     *
     * 此接口用于上传 `.up` 插件包并完成安装
     * 需要用户登录认证才能访问
     *
     * @param file 插件包文件
     *
     * @api POST /api/plugins
     * @permission 需要登录认证
     * @description 调用PluginService.upload()方法上传并安装插件
     */
    @PostMapping("/plugins", consumes = ["multipart/form-data"])
    @SaCheckRole(ROLE_ADMIN)
    @ResponseStatus(HttpStatus.CREATED)
    @Throws(CommonException.Forbidden::class)
    fun upload(@RequestParam("file") file: MultipartFile) {
        pluginService.upload(file)
    }

    /**
     * 启用或禁用插件
     *
     * 此接口用于切换指定插件的启用状态
     * 需要用户登录认证才能访问
     *
     * @param id 插件 ID
     * @param enabled 是否启用
     *
     * @api PUT /api/plugins/{id}/enabled-state
     * @permission 需要登录认证
     * @description 调用PluginService.setEnabled()方法切换插件启用状态
     */
    @PutMapping("/plugins/{id}/enabled-state")
    @SaCheckRole(ROLE_ADMIN)
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Throws(CommonException.Forbidden::class)
    fun setEnabled(@PathVariable id: String, @RequestParam enabled: Boolean) {
        pluginService.setEnabled(id, enabled)
    }

    /**
     * 删除插件
     *
     * 此接口用于删除指定 ID 的插件
     * 需要用户登录认证才能访问
     *
     * @param id 插件 ID
     *
     * @api DELETE /api/plugins/{id}
     * @permission 需要登录认证
     * @description 调用PluginService.delete()方法删除插件
     */
    @DeleteMapping("/plugins/{id}")
    @SaCheckRole(ROLE_ADMIN)
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Throws(CommonException.Forbidden::class)
    fun delete(@PathVariable id: String) {
        pluginService.delete(id)
    }

    /**
     * 导出（下载）插件包
     *
     * 此接口用于将指定插件打包为 `.up` 文件并以附件形式下载
     * 需要管理员角色才能访问
     *
     * @param id 插件 ID
     * @param response Servlet 响应对象，用于写入二进制内容
     *
     * @api GET /api/plugins/{id}/package
     * @permission 需要管理员角色
     * @description 调用PluginService.export()方法导出插件包
     */
    @GetMapping("/plugins/{id}/package")
    @SaCheckRole(ROLE_ADMIN)
    @Throws(CommonException.Forbidden::class)
    fun download(@PathVariable id: String, response: HttpServletResponse) {
        val plugin = pluginService.getPlugin(id)
        val zipBytes = pluginService.export(id)
        response.contentType = "application/octet-stream"
        response.setHeader("Content-Disposition", "attachment; filename=\"${plugin.id}-${plugin.version}.up\"")
        response.outputStream.write(zipBytes)
    }

    /**
     * 提交插件任务
     *
     * 此接口用于按任务类型异步触发插件任务，参数以键值对方式透传给对应插件
     * 需要用户登录认证才能访问
     *
     * @param taskType 任务类型（对应 TaskType 枚举值）
     * @param params 任务参数键值对
     *
     * @api POST /api/plugin-task-submissions/{taskType}
     * @permission 需要登录认证
     * @description 调用PluginTaskService.submit()方法提交插件任务
     */
    @PostMapping("/plugin-task-submissions/{taskType}")
    @SaCheckRole(ROLE_ADMIN)
    @ResponseStatus(HttpStatus.ACCEPTED)
    @Throws(
        CommonException.Forbidden::class,
        PluginException.UnknownTaskType::class,
        PluginException.InvalidTaskParams::class,
    )
    fun submitPluginTask(
        @PathVariable taskType: String,
        @RequestBody params: Map<String, String>,
    ) {
        val type = runCatching { TaskType.valueOf(taskType) }.getOrElse {
            throw PluginException.unknownTaskType(taskType = taskType)
        }
        try {
            pluginTaskService.submit(type, objectMapper.writeValueAsBytes(params))
        } catch (ex: IllegalArgumentException) {
            throw PluginException.invalidTaskParams(message = ex.message)
        }
    }
}
