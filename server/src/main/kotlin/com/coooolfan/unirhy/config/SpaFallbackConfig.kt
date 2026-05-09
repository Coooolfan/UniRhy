package com.coooolfan.unirhy.config

import org.springframework.context.annotation.Configuration
import org.springframework.web.servlet.config.annotation.ViewControllerRegistry
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer

@Configuration
class SpaFallbackConfig : WebMvcConfigurer {
    override fun addViewControllers(registry: ViewControllerRegistry) {
        listOf(
            "/init",
            "/login",
            "/dashboard",
        ).forEach { path ->
            registry.addViewController(path).setViewName(FORWARD_INDEX)
            registry.addViewController("$path/**").setViewName(FORWARD_INDEX)
        }
    }

    companion object {
        private const val FORWARD_INDEX = "forward:/index.html"
    }
}