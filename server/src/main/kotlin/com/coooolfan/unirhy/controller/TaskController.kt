package com.coooolfan.unirhy.controller

import cn.dev33.satoken.annotation.SaCheckLogin
import com.coooolfan.unirhy.service.task.AsyncTaskManager
import com.coooolfan.unirhy.service.task.RunningTaskView
import com.coooolfan.unirhy.service.task.ScanTaskRequest
import com.coooolfan.unirhy.service.task.ScanTaskService
import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController

/**
 * 任务管理接口
 *
 * 提供任务触发能力（例如扫描任务）
 */
@SaCheckLogin
@RestController
@RequestMapping("/api/task")
class TaskController(
    private val scanTaskService: ScanTaskService,
    private val asyncTaskManager: AsyncTaskManager,
) {

    /**
     * 触发扫描任务
     *
     * 此接口用于提交媒体扫描任务，对同一存储节点的请求是幂等的
     * 需要用户登录认证才能访问
     *
     * @param request 扫描任务请求参数
     *
     * @api POST /api/task/scan
     * @permission 需要登录认证
     * @description 调用ScanTaskService.submit()方法执行扫描任务
     */
    @PostMapping("/scan")
    @ResponseStatus(HttpStatus.ACCEPTED)
    fun executeScanTask(@RequestBody request: ScanTaskRequest) {
        scanTaskService.submit(request)
    }

    /**
     * 获取运行中的任务
     *
     * @api GET /api/task/running
     * @permission 需要登录认证
     */
    @GetMapping("/running")
    fun listRunningTasks(): List<RunningTaskView> {
        return asyncTaskManager.listRunning()
    }
}
