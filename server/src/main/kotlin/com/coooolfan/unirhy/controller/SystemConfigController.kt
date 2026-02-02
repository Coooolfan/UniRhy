package com.coooolfan.unirhy.controller

import cn.dev33.satoken.annotation.SaCheckLogin
import com.coooolfan.unirhy.model.SystemConfig
import com.coooolfan.unirhy.model.by
import com.coooolfan.unirhy.model.dto.SystemConfigCreate
import com.coooolfan.unirhy.model.dto.SystemConfigUpdate
import com.coooolfan.unirhy.model.dto.SystemStatus
import com.coooolfan.unirhy.service.SystemConfigService
import org.babyfish.jimmer.client.FetchBy
import org.babyfish.jimmer.sql.fetcher.Fetcher
import org.babyfish.jimmer.sql.kt.fetcher.newFetcher
import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.*

/**
 * 系统设置管理接口
 *
 * 提供系统配置的增删改查能力
 */
@RestController
@RequestMapping("/api/system/config")
class SystemConfigController(private val service: SystemConfigService) {

    /**
     * 获取系统初始化状态
     *
     * 此接口用于获取系统是否已完成初始化
     * 无需登录认证即可访问
     *
     * @return SystemStatus 返回系统初始化状态
     *
     * @api GET /api/system/config/status
     * @permission 不需要登录认证
     * @description 调用SystemConfigService.initialized()方法获取系统初始化状态
     */
    @GetMapping("/status")
    fun isInitialized(): SystemStatus {
        return SystemStatus(service.initialized())
    }

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
    @SaCheckLogin
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
    @SaCheckLogin
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
    @SaCheckLogin
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
