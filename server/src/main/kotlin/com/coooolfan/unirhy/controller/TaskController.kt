package com.coooolfan.unirhy.controller

import cn.dev33.satoken.annotation.SaCheckLogin
import com.coooolfan.unirhy.service.task.ScanTaskRequest
import com.coooolfan.unirhy.service.task.ScanTaskService
import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController

@SaCheckLogin
@RestController
@RequestMapping("/api/task")
class TaskController(private val scanTaskService: ScanTaskService) {

    @PostMapping("/scan")
    @ResponseStatus(HttpStatus.ACCEPTED)
    fun executeScanTask(@RequestBody request: ScanTaskRequest) {
        scanTaskService.execute(request)
    }
}
