package com.coooolfan.unirhy.controller

import cn.dev33.satoken.annotation.SaCheckLogin
import com.coooolfan.unirhy.service.task.TaskInfo
import com.coooolfan.unirhy.service.task.TaskManagerService
import com.coooolfan.unirhy.service.task.TaskRequest
import com.coooolfan.unirhy.service.task.TaskType
import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController

@SaCheckLogin
@RestController
@RequestMapping("/api/task")
class TaskManagerController(private val service: TaskManagerService) {

    /**
     * 获取当前可用的任务类型列表
     *
     * @return List<TaskType> 返回当前已注册的任务类型
     *
     * @api GET /api/task
     * @permission 需要登录认证
     */
    @GetMapping
    fun listAvailableTasks(): List<TaskType> {
        return service.listTaskTypes()
    }

    /**
     * 执行指定任务
     *
     * @param request 任务请求参数（密封类多态）
     * @return TaskInfo 返回任务执行信息
     *
     * @api POST /api/task
     * @permission 需要登录认证
     */
    @PostMapping
    @ResponseStatus(HttpStatus.ACCEPTED)
    fun executeTask(@RequestBody request: TaskRequest): TaskInfo {
        return service.execute(request)
    }
}
