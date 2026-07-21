package com.coooolfan.unirhy.controller

import cn.dev33.satoken.annotation.SaCheckLogin
import com.coooolfan.unirhy.error.TaskException
import com.coooolfan.unirhy.service.task.TaskStatisticsService
import com.coooolfan.unirhy.service.task.TaskStatisticsResponse
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

/**
 * 任务统计接口
 */
@SaCheckLogin
@RestController
@RequestMapping("/api/task-statistics")
class TaskStatisticsController(
    private val taskStatisticsService: TaskStatisticsService,
) {

    /**
     * 按 TaskKey 统计 submission 与 async task 的状态计数
     *
     * @param taskKeys 紧凑序列化形式（`namespace:TASK_TYPE`）的 TaskKey，
     *   可重复传递以过滤多个；缺省返回全部当前定义或存在历史记录的 TaskKey
     *
     * @api GET /api/task-statistics
     * @permission 需要登录认证
     */
    @GetMapping
    @Throws(TaskException.InvalidTaskKey::class)
    fun getTaskStatistics(
        @RequestParam(required = false) taskKeys: List<String>?,
    ): List<TaskStatisticsResponse> = taskStatisticsService.statistics(taskKeys)
}
