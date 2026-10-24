package com.starter.plugins

import org.springframework.context.annotation.Configuration
import org.springframework.web.servlet.config.annotation.InterceptorRegistry
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer

@Configuration
class PluginWebConfiguration(private val access: PluginAccessInterceptor, private val modules: BundledModuleGuard) : WebMvcConfigurer {
    override fun addInterceptors(registry: InterceptorRegistry) {
        registry.addInterceptor(access).addPathPatterns("/api/plugins/*", "/api/plugins/*/**")
        registry.addInterceptor(modules).addPathPatterns("/api/documents/**", "/api/admin/document-templates/**", "/api/workflows/**", "/api/admin/workflows/**")
    }
}
