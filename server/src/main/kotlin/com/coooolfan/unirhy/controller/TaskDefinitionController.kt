package com.coooolfan.unirhy.controller

import cn.dev33.satoken.annotation.SaCheckLogin
import com.coooolfan.unirhy.error.TaskException
import com.coooolfan.unirhy.service.task.TaskDefinitionService
import com.coooolfan.unirhy.service.task.TaskDefinitionView
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

/**
 * 任务定义查询接口
 *
 * 内建任务与已启用插件任务的统一定义视图，供前端渲染提交表单
 */
@SaCheckLogin
@RestController
@RequestMapping("/api/task-definitions")
class TaskDefinitionController(
    private val taskDefinitionService: TaskDefinitionService,
) {

    /**
     * 获取当前可提交的任务定义列表
     *
     * @return 每项包含 namespace、taskType、名称以及 form.schema / form.order
     *
     * @api GET /api/task-definitions
     * @permission 需要登录认证
     */
    @GetMapping
    fun listTaskDefinitions(): List<TaskDefinitionView> = taskDefinitionService.list()

    /**
     * 获取单项任务定义
     *
     * @param namespace 任务命名空间
     * @param taskType 任务类型
     *
     * @api GET /api/task-definitions/{namespace}/{taskType}
     * @permission 需要登录认证
     */
    @GetMapping("/{namespace}/{taskType}")
    @Throws(TaskException.DefinitionNotFound::class, TaskException.InvalidTaskKey::class)
    fun getTaskDefinition(
        @PathVariable namespace: String,
        @PathVariable taskType: String,
    ): TaskDefinitionView = taskDefinitionService.get(namespace, taskType)
}
