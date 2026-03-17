package com.coooolfan.unirhy.config

import cn.dev33.satoken.`fun`.strategy.SaCorsHandleFunction
import cn.dev33.satoken.context.model.SaRequest
import cn.dev33.satoken.context.model.SaResponse
import cn.dev33.satoken.interceptor.SaInterceptor
import cn.dev33.satoken.router.SaHttpMethod
import cn.dev33.satoken.router.SaRouter
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.web.servlet.config.annotation.InterceptorRegistry
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer

@Configuration
class SaTokenConfig(
    @Value("\${unirhy.cors.allowed-origins:http://localhost:5173,http://127.0.0.1:5173}")
    allowedOriginsRaw: String,
) : WebMvcConfigurer {
    private val allowedOrigins = parseAllowedOrigins(allowedOriginsRaw)

    override fun addInterceptors(registry: InterceptorRegistry) {
        // 注册 Sa-Token 拦截器，打开注解式鉴权功能
        registry.addInterceptor(SaInterceptor())
            // 所有接口都会检查是否登录
            .addPathPatterns("/**")
    }

    /**
     * 跨域
     */
    @Bean
    fun corsHandle(): SaCorsHandleFunction {
        return SaCorsHandleFunction { req, res, _ ->
            applyCorsHeaders(req, res)

            // 如果是预检请求，则立即返回到前端
            SaRouter.match(SaHttpMethod.OPTIONS).back()
        }
    }

    internal fun applyCorsHeaders(req: SaRequest, res: SaResponse) {
        val origin = req.getHeader(ORIGIN_HEADER)?.trim().orEmpty()
        if (origin.isEmpty() || origin !in allowedOrigins) {
            return
        }

        val requestedHeaders = req.getHeader(ACCESS_CONTROL_REQUEST_HEADERS)?.trim().orEmpty()
        val allowHeaders = requestedHeaders.ifEmpty { DEFAULT_ALLOWED_HEADERS }

        res.setHeader(ACCESS_CONTROL_ALLOW_ORIGIN, origin)
        res.setHeader(ACCESS_CONTROL_ALLOW_CREDENTIALS, "true")
        res.setHeader(ACCESS_CONTROL_ALLOW_METHODS, DEFAULT_ALLOWED_METHODS)
        res.setHeader(ACCESS_CONTROL_ALLOW_HEADERS, allowHeaders)
        res.setHeader(ACCESS_CONTROL_MAX_AGE, "3600")
        res.addHeader(VARY_HEADER, ORIGIN_HEADER)
        res.addHeader(VARY_HEADER, ACCESS_CONTROL_REQUEST_METHOD)
        res.addHeader(VARY_HEADER, ACCESS_CONTROL_REQUEST_HEADERS)
    }

    private fun parseAllowedOrigins(raw: String): Set<String> {
        return raw.split(',')
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .toSet()
    }

    companion object {
        private const val ORIGIN_HEADER = "Origin"
        private const val VARY_HEADER = "Vary"
        private const val ACCESS_CONTROL_ALLOW_ORIGIN = "Access-Control-Allow-Origin"
        private const val ACCESS_CONTROL_ALLOW_CREDENTIALS = "Access-Control-Allow-Credentials"
        private const val ACCESS_CONTROL_ALLOW_METHODS = "Access-Control-Allow-Methods"
        private const val ACCESS_CONTROL_ALLOW_HEADERS = "Access-Control-Allow-Headers"
        private const val ACCESS_CONTROL_MAX_AGE = "Access-Control-Max-Age"
        private const val ACCESS_CONTROL_REQUEST_METHOD = "Access-Control-Request-Method"
        private const val ACCESS_CONTROL_REQUEST_HEADERS = "Access-Control-Request-Headers"
        private const val DEFAULT_ALLOWED_METHODS = "GET,POST,PUT,PATCH,DELETE,HEAD,OPTIONS"
        private const val DEFAULT_ALLOWED_HEADERS =
            "content-type, tenant, unirhy-token, range, if-none-match, if-modified-since, if-range"
    }
}
