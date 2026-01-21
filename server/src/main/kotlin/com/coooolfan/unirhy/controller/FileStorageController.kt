package com.coooolfan.unirhy.controller

import cn.dev33.satoken.annotation.SaCheckLogin
import com.coooolfan.unirhy.model.storage.by
import com.coooolfan.unirhy.model.storage.dto.FileProviderFileSystemCreate
import com.coooolfan.unirhy.model.storage.dto.FileProviderFileSystemUpdate
import com.coooolfan.unirhy.model.storage.dto.FileProviderOssCreate
import com.coooolfan.unirhy.model.storage.dto.FileProviderOssUpdate
import com.coooolfan.unirhy.model.storage.FileProviderFileSystem
import com.coooolfan.unirhy.model.storage.FileProviderOss
import com.coooolfan.unirhy.service.StorageService
import org.babyfish.jimmer.client.FetchBy
import org.babyfish.jimmer.sql.fetcher.Fetcher
import org.babyfish.jimmer.sql.kt.fetcher.newFetcher
import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("/api/storage")
class StorageController(private val service: StorageService) {

    @SaCheckLogin
    @GetMapping("/oss")
    fun listOss(): List<@FetchBy("DEFAULT_OSS_FETCHER") FileProviderOss> {
        return service.listOss(DEFAULT_OSS_FETCHER)
    }

    @SaCheckLogin
    @GetMapping("/oss/{id}")
    fun getOss(@PathVariable id: Long): @FetchBy("DEFAULT_OSS_FETCHER") FileProviderOss {
        return service.getOss(id, DEFAULT_OSS_FETCHER)
    }

    @SaCheckLogin
    @PostMapping("/oss")
    @ResponseStatus(HttpStatus.CREATED)
    fun createOss(create: FileProviderOssCreate): @FetchBy("DEFAULT_OSS_FETCHER") FileProviderOss {
        return service.createOss(create, DEFAULT_OSS_FETCHER)
    }

    @SaCheckLogin
    @PutMapping("/oss/{id}")
    fun updateOss(
        @PathVariable id: Long,
        update: FileProviderOssUpdate,
    ): @FetchBy("DEFAULT_OSS_FETCHER") FileProviderOss {
        return service.updateOss(update.toEntity { this.id = id }, DEFAULT_OSS_FETCHER)
    }

    @SaCheckLogin
    @DeleteMapping("/oss/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun deleteOss(@PathVariable id: Long) {
        service.deleteOss(id)
    }

    @SaCheckLogin
    @GetMapping("/fs")
    fun listFileSystem(): List<@FetchBy("DEFAULT_FILE_SYSTEM_FETCHER") FileProviderFileSystem> {
        return service.listFileSystem(DEFAULT_FILE_SYSTEM_FETCHER)
    }

    @SaCheckLogin
    @GetMapping("/fs/{id}")
    fun getFileSystem(@PathVariable id: Long): @FetchBy("DEFAULT_FILE_SYSTEM_FETCHER") FileProviderFileSystem {
        return service.getFileSystem(id, DEFAULT_FILE_SYSTEM_FETCHER)
    }

    @SaCheckLogin
    @PostMapping("/fs")
    @ResponseStatus(HttpStatus.CREATED)
    fun createFileSystem(create: FileProviderFileSystemCreate): @FetchBy("DEFAULT_FILE_SYSTEM_FETCHER") FileProviderFileSystem {
        return service.createFileSystem(create, DEFAULT_FILE_SYSTEM_FETCHER)
    }

    @SaCheckLogin
    @PutMapping("/fs/{id}")
    fun updateFileSystem(
        @PathVariable id: Long,
        update: FileProviderFileSystemUpdate,
    ): @FetchBy("DEFAULT_FILE_SYSTEM_FETCHER") FileProviderFileSystem {
        return service.updateFileSystem(update.toEntity { this.id = id }, DEFAULT_FILE_SYSTEM_FETCHER)
    }

    @SaCheckLogin
    @DeleteMapping("/fs/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun deleteFileSystem(@PathVariable id: Long) {
        service.deleteFileSystem(id)
    }

    companion object {
        private val DEFAULT_OSS_FETCHER: Fetcher<FileProviderOss> = newFetcher(FileProviderOss::class).by {
            allScalarFields()
        }

        private val DEFAULT_FILE_SYSTEM_FETCHER: Fetcher<FileProviderFileSystem> = newFetcher(FileProviderFileSystem::class).by {
            allScalarFields()
        }
    }
}