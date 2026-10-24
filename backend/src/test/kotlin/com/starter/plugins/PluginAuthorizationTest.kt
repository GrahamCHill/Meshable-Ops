package com.starter.plugins

import org.assertj.core.api.Assertions.*
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import org.mockito.Mockito.*
import org.springframework.context.annotation.*
import org.springframework.security.access.AccessDeniedException
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity
import org.springframework.security.core.authority.SimpleGrantedAuthority
import org.springframework.security.core.context.SecurityContextHolder

class PluginAuthorizationTest {
    @Configuration @EnableMethodSecurity(proxyTargetClass = true)
    open class Config {
        @Bean open fun registry() = mock(PluginRegistry::class.java)
        @Bean open fun controller(registry: PluginRegistry) = AdminPluginController(registry)
        @Bean open fun data() = mock(PluginDataService::class.java)
        @Bean open fun dataController(data: PluginDataService) = AdminPluginDataController(data)
        @Bean open fun configuration() = mock(PluginConfigurationService::class.java)
        @Bean open fun configurationController(configuration: PluginConfigurationService) = PluginConfigurationController(configuration)
        @Bean open fun messaging() = mock(com.starter.notification.NotificationService::class.java)
        @Bean open fun drives() = mock(com.starter.plugins.integrations.MicrosoftDriveService::class.java)
        @Bean open fun rates() = mock(com.starter.security.RateLimits::class.java)
        @Bean open fun integrationController(messaging: com.starter.notification.NotificationService, drives: com.starter.plugins.integrations.MicrosoftDriveService, rates: com.starter.security.RateLimits) = com.starter.plugins.integrations.IntegrationController(messaging, drives, rates)
    }
    @AfterEach fun clear() = SecurityContextHolder.clearContext()
    @Test fun `ordinary accounts cannot list installed plugins or change their settings`() {
        SecurityContextHolder.getContext().authentication = UsernamePasswordAuthenticationToken("user@example.test", "", listOf(SimpleGrantedAuthority("ROLE_USER")))
        AnnotationConfigApplicationContext(Config::class.java).use { context ->
            val controller = context.getBean(AdminPluginController::class.java)
            assertThatThrownBy { controller.installed() }.isInstanceOf(AccessDeniedException::class.java)
            assertThatThrownBy { controller.configure("kanban", PluginSettingsRequest(true)) }.isInstanceOf(AccessDeniedException::class.java)
            assertThatThrownBy { context.getBean(AdminPluginDataController::class.java).clear("kanban", PluginClearRequest("kanban")) }.isInstanceOf(AccessDeniedException::class.java)
            verifyNoInteractions(context.getBean(PluginDataService::class.java))
            val configuration = context.getBean(PluginConfigurationController::class.java)
            assertThatThrownBy { configuration.view("telegram") }.isInstanceOf(AccessDeniedException::class.java)
            assertThatThrownBy { configuration.save("telegram", PluginConfigurationRequest()) }.isInstanceOf(AccessDeniedException::class.java)
            val integrations = context.getBean(com.starter.plugins.integrations.IntegrationController::class.java)
            val principal = org.springframework.security.core.userdetails.User.withUsername("user@example.test").password("unused").roles("USER").build()
            assertThatThrownBy { integrations.send("telegram", com.starter.notification.ChannelMessage("test"), principal) }.isInstanceOf(AccessDeniedException::class.java)
            assertThatThrownBy { integrations.files("sharepoint", principal) }.isInstanceOf(AccessDeniedException::class.java)
            verifyNoInteractions(context.getBean(PluginConfigurationService::class.java), context.getBean(com.starter.notification.NotificationService::class.java), context.getBean(com.starter.plugins.integrations.MicrosoftDriveService::class.java))
            verifyNoInteractions(context.getBean(PluginRegistry::class.java))
        }
    }
}
