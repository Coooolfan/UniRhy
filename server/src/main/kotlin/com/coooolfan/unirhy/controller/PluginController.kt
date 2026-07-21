package com.coooolfan.unirhy.controller

import cn.dev33.satoken.annotation.SaCheckLogin
import cn.dev33.satoken.annotation.SaCheckRole
import com.coooolfan.unirhy.config.ROLE_ADMIN
import com.coooolfan.unirhy.error.CommonException
import com.coooolfan.unirhy.error.PluginException
import com.coooolfan.unirhy.service.PluginService
import com.coooolfan.unirhy.service.task.PluginTaskService
import jakarta.servlet.http.HttpServletResponse
import tools.jackson.databind.JsonNode
import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.multipart.MultipartFile

data class PluginInfoResponse(
    val id: String,
    val name: String?,
    val version: String,
    val taskType: String,
    val concurrency: Int,
    val isAvailable: Boolean,
    val enabled: Boolean,
    val formDefinition: JsonNode,
)

/**
 * 插件管理接口
 *
 * 提供插件的上传、启停、并发调整、删除与导出能力；
 * 任务提交经由统一的 `/api/task-submissions`
 */
@RestController
@RequestMapping("/api")
@SaCheckLogin
class PluginController(
    private val pluginService: PluginService,
    private val pluginTaskService: PluginTaskService,
) {
    /**
     * 获取插件列表
     *
     * 此接口用于获取系统中已安装的全部插件信息，并标记插件在本节点是否已加载可用
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
        return pluginService.listPlugins().map { plugin ->
            PluginInfoResponse(
                id = plugin.id,
                name = plugin.name,
                version = plugin.version,
                taskType = plugin.taskType,
                concurrency = plugin.concurrency,
                isAvailable = pluginTaskService.isLoaded(plugin.id),
                enabled = plugin.enabled,
                formDefinition = plugin.formDefinition,
            )
        }
    }

    /**
     * 上传插件
     *
     * 此接口用于上传 `.up` 插件包并完成安装；同 id 上传即覆盖升级，上传后保持禁用
     * 需要管理员角色才能访问
     *
     * @param file 插件包文件
     *
     * @api POST /api/plugins
     * @permission 需要管理员权限
     * @description 调用PluginService.upload()方法上传并安装插件
     */
    @PostMapping("/plugins", consumes = ["multipart/form-data"])
    @SaCheckRole(ROLE_ADMIN)
    @ResponseStatus(HttpStatus.CREATED)
    @Throws(
        CommonException.Forbidden::class,
        PluginException.PackageTooLarge::class,
        PluginException.WasmTooLarge::class,
        PluginException.ManifestMissing::class,
        PluginException.WasmMissing::class,
        PluginException.InvalidManifest::class,
        PluginException.UnsupportedRuntime::class,
        PluginException.UnsupportedAbi::class,
    )
    fun upload(@RequestParam("file") file: MultipartFile) {
        pluginService.upload(file)
    }

    /**
     * 启用或禁用插件
     *
     * 启用前完成 WASM 解析、实例化与导出函数校验；校验失败时插件保持禁用
     * 需要管理员角色才能访问
     *
     * @param id 插件 ID
     * @param enabled 是否启用
     *
     * @api PUT /api/plugins/{id}/enabled-state
     * @permission 需要管理员权限
     * @description 调用PluginService.setEnabled()方法切换插件启用状态
     */
    @PutMapping("/plugins/{id}/enabled-state")
    @SaCheckRole(ROLE_ADMIN)
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Throws(
        CommonException.Forbidden::class,
        PluginException.NotFound::class,
        PluginException.LoadFailed::class,
    )
    fun setEnabled(@PathVariable id: String, @RequestParam enabled: Boolean) {
        pluginService.setEnabled(id, enabled)
    }

    /**
     * 修改插件任务执行并发值
     *
     * 修改后无需重启服务，各节点在下一轮对账时生效
     * 需要管理员角色才能访问
     *
     * @param id 插件 ID
     * @param concurrency 正整数并发值
     *
     * @api PUT /api/plugins/{id}/concurrency
     * @permission 需要管理员权限
     * @description 调用PluginService.updateConcurrency()方法修改并发值
     */
    @PutMapping("/plugins/{id}/concurrency")
    @SaCheckRole(ROLE_ADMIN)
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Throws(
        CommonException.Forbidden::class,
        PluginException.NotFound::class,
        PluginException.InvalidConcurrency::class,
    )
    fun updateConcurrency(@PathVariable id: String, @RequestParam concurrency: Int) {
        pluginService.updateConcurrency(id, concurrency)
    }

    /**
     * 删除插件
     *
     * 只允许删除已禁用的插件；存在活动 submission / task 时返回 409
     * 需要管理员角色才能访问
     *
     * @param id 插件 ID
     *
     * @api DELETE /api/plugins/{id}
     * @permission 需要管理员权限
     * @description 调用PluginService.delete()方法删除插件
     */
    @DeleteMapping("/plugins/{id}")
    @SaCheckRole(ROLE_ADMIN)
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Throws(
        CommonException.Forbidden::class,
        PluginException.NotFound::class,
        PluginException.DeleteConflict::class,
    )
    fun delete(@PathVariable id: String) {
        pluginService.delete(id)
    }

    /**
     * 导出（下载）插件包
     *
     * 此接口用于将指定插件打包为 `.up` 文件并以附件形式下载；
     * manifest 中的 `task.concurrency` 为当前并发值
     * 需要管理员角色才能访问
     *
     * @param id 插件 ID
     * @param response Servlet 响应对象，用于写入二进制内容
     *
     * @api GET /api/plugins/{id}/package
     * @permission 需要管理员权限
     * @description 调用PluginService.export()方法导出插件包
     */
    @GetMapping("/plugins/{id}/package")
    @SaCheckRole(ROLE_ADMIN)
    @Throws(CommonException.Forbidden::class, PluginException.NotFound::class)
    fun download(@PathVariable id: String, response: HttpServletResponse) {
        val plugin = pluginService.getPlugin(id)
        val zipBytes = pluginService.export(id)
        response.contentType = "application/octet-stream"
        response.setHeader("Content-Disposition", "attachment; filename=\"${plugin.id}-${plugin.version}.up\"")
        response.outputStream.write(zipBytes)
    }
}
