package com.starter.plugins

import org.springframework.context.annotation.Configuration
import org.springframework.web.servlet.config.annotation.InterceptorRegistry
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer

@Configuration
class PluginWebConfiguration(private val access: PluginAccessInterceptor) : WebMvcConfigurer {
    override fun addInterceptors(registry: InterceptorRegistry) {
        registry.addInterceptor(access).addPathPatterns("/api/plugins/*", "/api/plugins/*/**")
    }
}
