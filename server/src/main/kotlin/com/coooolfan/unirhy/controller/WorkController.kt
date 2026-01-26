package com.coooolfan.unirhy.controller

import cn.dev33.satoken.annotation.SaCheckLogin
import com.coooolfan.unirhy.error.CommonException
import com.coooolfan.unirhy.model.Work
import com.coooolfan.unirhy.model.by
import com.coooolfan.unirhy.service.WorkService
import org.babyfish.jimmer.client.FetchBy
import org.babyfish.jimmer.sql.kt.fetcher.newFetcher
import org.springframework.web.bind.annotation.*

/**
 * 作品管理接口
 *
 * 提供作品列表查询、随机获取与删除能力
 */
@SaCheckLogin
@RestController
@RequestMapping("/api/work")
class WorkController(
    private val service: WorkService,
) {
    /**
     * 获取作品列表
     *
     * @return List<Work> 返回作品列表（默认 fetcher）
     *
     * @api GET /api/work
     * @permission 需要登录认证
     * @description 调用WorkService.listWork()方法获取作品列表
     */
    @GetMapping
    fun listWork(): List<@FetchBy("DEFAULT_WORK_FETCHER") Work> {
        return service.listWork(DEFAULT_WORK_FETCHER)
    }

    /**
     * 获取时间窗口内的随机作品
     *
     * 通过时间戳、时间长度与时区偏移计算出“时间窗口编号”，并将其作为随机种子，
     * 从而保证同一窗口内的所有请求都返回相同的作品。
     *
     * @param timestamp 时间戳（秒/毫秒），可空，默认当前时间
     * @param length 时间长度（毫秒），可空，默认 86400000（天），常用：3600000（小时）/43200000（半天）/86400000（天）/604800000（周）
     * @param offset 时区偏移（毫秒），可空，默认 0L，建议传浏览器 `Date.getTimezoneOffset() * 60000`（毫秒，满足 `utc = local + offset`）
     * @return Work 返回随机作品（默认 fetcher）
     *
     * @api GET /api/work/random
     * @permission 需要登录认证
     * @description 调用WorkService.randomWork()方法获取时间窗口内的随机作品
     */
    @GetMapping("/random")
    fun randomWork(
        @RequestParam(required = false) timestamp: Long?,
        @RequestParam(required = false) length: Long?,
        @RequestParam(required = false) offset: Long?,
    ): @FetchBy("DEFAULT_WORK_FETCHER") Work {
        return service.randomWork(timestamp, length, offset, DEFAULT_WORK_FETCHER) ?: throw CommonException.NotFound()
    }

    /**
     * 删除指定作品
     *
     * @param id 作品 ID
     *
     * @api DELETE /api/work/{id}
     * @permission 需要登录认证
     * @description 调用WorkService.deleteWork()方法删除作品
     */
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
                artists()
                cover()
            }
        }
    }
}
