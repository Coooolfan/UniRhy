package com.coooolfan.unirhy.controller.storage

import cn.dev33.satoken.annotation.SaCheckLogin
import com.coooolfan.unirhy.model.storage.FileProviderFileSystem
import com.coooolfan.unirhy.model.storage.by
import com.coooolfan.unirhy.model.storage.dto.FileProviderFileSystemCreate
import com.coooolfan.unirhy.model.storage.dto.FileProviderFileSystemUpdate
import com.coooolfan.unirhy.service.storage.FileSystemStorageService
import org.babyfish.jimmer.client.FetchBy
import org.babyfish.jimmer.sql.fetcher.Fetcher
import org.babyfish.jimmer.sql.kt.fetcher.newFetcher
import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.*

/**
 * 文件系统存储管理接口
 *
 * 提供文件系统存储配置的增删改查能力
 */
@SaCheckLogin
@RestController
@RequestMapping("/api/storage/fs")
class FileSystemStorageController(private val service: FileSystemStorageService) {

    /**
     * 获取文件系统存储列表
     *
     * 此接口用于获取所有文件系统存储配置
     * 需要用户登录认证才能访问
     *
     * @return List<FileProviderFileSystem> 返回文件系统存储列表（默认 fetcher）
     *
     * @api GET /api/storage/fs
     * @permission 需要登录认证
     * @description 调用FileSystemStorageService.list()方法获取存储列表
     */
    @GetMapping
    fun list(): List<@FetchBy("DEFAULT_FILE_SYSTEM_FETCHER") FileProviderFileSystem> {
        return service.list(DEFAULT_FILE_SYSTEM_FETCHER)
    }

    /**
     * 获取指定文件系统存储配置
     *
     * 此接口用于获取指定ID的文件系统存储配置详情
     * 需要用户登录认证才能访问
     *
     * @param id 存储配置 ID
     * @return FileProviderFileSystem 返回文件系统存储配置（默认 fetcher）
     *
     * @api GET /api/storage/fs/{id}
     * @permission 需要登录认证
     * @description 调用FileSystemStorageService.get()方法获取存储配置
     */
    @GetMapping("/{id}")
    fun get(@PathVariable id: Long): @FetchBy("DEFAULT_FILE_SYSTEM_FETCHER") FileProviderFileSystem {
        return service.get(id, DEFAULT_FILE_SYSTEM_FETCHER)
    }

    /**
     * 创建文件系统存储配置
     *
     * 此接口用于创建新的文件系统存储配置
     * 需要用户登录认证才能访问
     *
     * @param create 创建参数
     * @return FileProviderFileSystem 返回创建后的文件系统存储配置（默认 fetcher）
     *
     * @api POST /api/storage/fs
     * @permission 需要登录认证
     * @description 调用FileSystemStorageService.create()方法创建存储配置
     */
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    fun create(@RequestBody create: FileProviderFileSystemCreate): @FetchBy("DEFAULT_FILE_SYSTEM_FETCHER") FileProviderFileSystem {
        return service.create(create, DEFAULT_FILE_SYSTEM_FETCHER)
    }

    /**
     * 更新指定文件系统存储配置
     *
     * 此接口用于更新指定ID的文件系统存储配置信息
     * 需要用户登录认证才能访问
     *
     * @param id 存储配置 ID
     * @param update 更新参数
     * @return FileProviderFileSystem 返回更新后的文件系统存储配置（默认 fetcher）
     *
     * @api PUT /api/storage/fs/{id}
     * @permission 需要登录认证
     * @description 调用FileSystemStorageService.update()方法更新存储配置
     */
    @PutMapping("/{id}")
    fun update(
        @PathVariable id: Long,
        update: FileProviderFileSystemUpdate,
    ): @FetchBy("DEFAULT_FILE_SYSTEM_FETCHER") FileProviderFileSystem {
        return service.update(update.toEntity { this.id = id }, DEFAULT_FILE_SYSTEM_FETCHER)
    }

    /**
     * 删除指定文件系统存储配置
     *
     * 此接口用于删除指定ID的文件系统存储配置
     * 需要用户登录认证才能访问
     *
     * @param id 存储配置 ID
     *
     * @api DELETE /api/storage/fs/{id}
     * @permission 需要登录认证
     * @description 调用FileSystemStorageService.delete()方法删除存储配置
     */
    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun delete(@PathVariable id: Long) {
        service.delete(id)
    }

    companion object {
        private val DEFAULT_FILE_SYSTEM_FETCHER: Fetcher<FileProviderFileSystem> =
            newFetcher(FileProviderFileSystem::class).by {
                allScalarFields()
            }
    }
}
