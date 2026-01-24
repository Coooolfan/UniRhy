package com.coooolfan.unirhy.controller

import cn.dev33.satoken.annotation.SaCheckLogin
import com.coooolfan.unirhy.model.SystemConfig
import com.coooolfan.unirhy.model.by
import com.coooolfan.unirhy.model.dto.SystemConfigCreate
import com.coooolfan.unirhy.model.dto.SystemConfigUpdate
import com.coooolfan.unirhy.service.SystemConfigService
import org.babyfish.jimmer.client.FetchBy
import org.babyfish.jimmer.sql.fetcher.Fetcher
import org.babyfish.jimmer.sql.kt.fetcher.newFetcher
import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController

/**
 * 系统设置管理接口
 *
 * 提供系统配置的增删改查能力
 */
@SaCheckLogin
@RestController
@RequestMapping("/api/system/config")
class SystemConfigController(private val service: SystemConfigService) {

    /**
     * 获取系统配置
     *
     * 此接口用于获取当前系统配置
     * 需要用户登录认证才能访问
     *
     * @return SystemConfig 返回系统配置（默认 fetcher）
     *
     * @api GET /api/system/config
     * @permission 需要登录认证
     * @description 调用SystemConfigService.get()方法获取系统配置
     */
    @GetMapping
    fun get(): @FetchBy("DEFAULT_SYSTEM_CONFIG_FETCHER") SystemConfig {
        return service.get(DEFAULT_SYSTEM_CONFIG_FETCHER)
    }

    /**
     * 创建系统配置
     *
     * 此接口用于创建系统配置（单例）
     * 需要用户登录认证才能访问
     *
     * @param create 创建参数
     * @return SystemConfig 返回创建后的系统配置（默认 fetcher）
     *
     * @api POST /api/system/config
     * @permission 需要登录认证
     * @description 调用SystemConfigService.create()方法创建系统配置
     */
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    fun create(create: SystemConfigCreate): @FetchBy("DEFAULT_SYSTEM_CONFIG_FETCHER") SystemConfig {
        return service.create(create, DEFAULT_SYSTEM_CONFIG_FETCHER)
    }

    /**
     * 更新系统配置
     *
     * 此接口用于更新系统配置（单例）
     * 需要用户登录认证才能访问
     *
     * @param update 更新参数
     * @return SystemConfig 返回更新后的系统配置（默认 fetcher）
     *
     * @api PUT /api/system/config
     * @permission 需要登录认证
     * @description 调用SystemConfigService.update()方法更新系统配置
     */
    @PutMapping
    fun update(update: SystemConfigUpdate): @FetchBy("DEFAULT_SYSTEM_CONFIG_FETCHER") SystemConfig {
        return service.update(update, DEFAULT_SYSTEM_CONFIG_FETCHER)
    }

    companion object {
        private val DEFAULT_SYSTEM_CONFIG_FETCHER: Fetcher<SystemConfig> =
            newFetcher(SystemConfig::class).by {
                allScalarFields()
                ossProviderId()
                fsProviderId()
            }
    }
}