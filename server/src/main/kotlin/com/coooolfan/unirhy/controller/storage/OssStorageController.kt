package com.coooolfan.unirhy.controller.storage

import cn.dev33.satoken.annotation.SaCheckLogin
import com.coooolfan.unirhy.model.storage.FileProviderOss
import com.coooolfan.unirhy.model.storage.by
import com.coooolfan.unirhy.model.storage.dto.FileProviderOssCreate
import com.coooolfan.unirhy.model.storage.dto.FileProviderOssUpdate
import com.coooolfan.unirhy.service.storage.OssStorageService
import org.babyfish.jimmer.client.FetchBy
import org.babyfish.jimmer.sql.fetcher.Fetcher
import org.babyfish.jimmer.sql.kt.fetcher.newFetcher
import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController

/**
 * OSS存储管理接口
 *
 * 提供OSS存储配置的增删改查能力
 */
@SaCheckLogin
@RestController
@RequestMapping("/api/storage/oss")
class OssStorageController(private val service: OssStorageService) {

    /**
     * 获取OSS存储列表
     *
     * 此接口用于获取所有OSS存储配置
     * 需要用户登录认证才能访问
     *
     * @return List<FileProviderOss> 返回OSS存储列表（默认 fetcher）
     *
     * @api GET /api/storage/oss
     * @permission 需要登录认证
     * @description 调用OssStorageService.list()方法获取存储列表
     */
    @GetMapping
    fun list(): List<@FetchBy("DEFAULT_OSS_FETCHER") FileProviderOss> {
        return service.list(DEFAULT_OSS_FETCHER)
    }

    /**
     * 获取指定OSS存储配置
     *
     * 此接口用于获取指定ID的OSS存储配置详情
     * 需要用户登录认证才能访问
     *
     * @param id 存储配置 ID
     * @return FileProviderOss 返回OSS存储配置（默认 fetcher）
     *
     * @api GET /api/storage/oss/{id}
     * @permission 需要登录认证
     * @description 调用OssStorageService.get()方法获取存储配置
     */
    @GetMapping("/{id}")
    fun get(@PathVariable id: Long): @FetchBy("DEFAULT_OSS_FETCHER") FileProviderOss {
        return service.get(id, DEFAULT_OSS_FETCHER)
    }

    /**
     * 创建OSS存储配置
     *
     * 此接口用于创建新的OSS存储配置
     * 需要用户登录认证才能访问
     *
     * @param create 创建参数
     * @return FileProviderOss 返回创建后的OSS存储配置（默认 fetcher）
     *
     * @api POST /api/storage/oss
     * @permission 需要登录认证
     * @description 调用OssStorageService.create()方法创建存储配置
     */
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    fun create(create: FileProviderOssCreate): @FetchBy("DEFAULT_OSS_FETCHER") FileProviderOss {
        return service.create(create, DEFAULT_OSS_FETCHER)
    }

    /**
     * 更新指定OSS存储配置
     *
     * 此接口用于更新指定ID的OSS存储配置信息
     * 需要用户登录认证才能访问
     *
     * @param id 存储配置 ID
     * @param update 更新参数
     * @return FileProviderOss 返回更新后的OSS存储配置（默认 fetcher）
     *
     * @api PUT /api/storage/oss/{id}
     * @permission 需要登录认证
     * @description 调用OssStorageService.update()方法更新存储配置
     */
    @PutMapping("/{id}")
    fun update(
        @PathVariable id: Long,
        update: FileProviderOssUpdate,
    ): @FetchBy("DEFAULT_OSS_FETCHER") FileProviderOss {
        return service.update(update.toEntity { this.id = id }, DEFAULT_OSS_FETCHER)
    }

    /**
     * 删除指定OSS存储配置
     *
     * 此接口用于删除指定ID的OSS存储配置
     * 需要用户登录认证才能访问
     *
     * @param id 存储配置 ID
     *
     * @api DELETE /api/storage/oss/{id}
     * @permission 需要登录认证
     * @description 调用OssStorageService.delete()方法删除存储配置
     */
    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun delete(@PathVariable id: Long) {
        service.delete(id)
    }

    companion object {
        private val DEFAULT_OSS_FETCHER: Fetcher<FileProviderOss> = newFetcher(FileProviderOss::class).by {
            allScalarFields()
        }
    }
}
