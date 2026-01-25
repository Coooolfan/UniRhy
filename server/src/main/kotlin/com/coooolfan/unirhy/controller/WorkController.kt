package com.coooolfan.unirhy.controller

import cn.dev33.satoken.annotation.SaCheckLogin
import com.coooolfan.unirhy.model.Work
import com.coooolfan.unirhy.model.by
import com.coooolfan.unirhy.service.WorkService
import org.babyfish.jimmer.client.FetchBy
import org.babyfish.jimmer.sql.kt.fetcher.newFetcher
import org.springframework.web.bind.annotation.*

@SaCheckLogin
@RestController
@RequestMapping("/api/work")
class WorkController(
    private val service: WorkService,
) {
    @GetMapping
    fun listWork(): List<@FetchBy("DEFAULT_WORK_FETCHER") Work> {
        return service.listWork(DEFAULT_WORK_FETCHER)
    }

    @DeleteMapping("/{id}")
    fun deleteWork(@PathVariable id: Long) {
        service.deleteWork(id)
    }

    companion object {
        private val DEFAULT_WORK_FETCHER = newFetcher(Work::class).by {
            allScalarFields()
            recordings {
                allScalarFields()
                assets {
                    allScalarFields()
                    mediaFile()
                }
            }
        }
    }
}