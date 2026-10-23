package com.starter.controller

import com.starter.dto.*
import com.starter.email.*
import org.assertj.core.api.Assertions.*
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import org.mockito.Mockito.*
import org.springframework.context.annotation.AnnotationConfigApplicationContext
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.security.access.AccessDeniedException
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity
import org.springframework.security.core.authority.SimpleGrantedAuthority
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.security.core.userdetails.User

class EmailAuthorizationTest {
    @Configuration @EnableMethodSecurity(proxyTargetClass = true)
    open class TestConfig {
        @Bean open fun settings(): EmailSettingsService = mock(EmailSettingsService::class.java)
        @Bean open fun delivery(): EmailDeliveryService = mock(EmailDeliveryService::class.java)
        @Bean open fun controller(settings: EmailSettingsService, delivery: EmailDeliveryService) = AdminEmailController(settings, delivery)
    }
    @AfterEach fun clear() = SecurityContextHolder.clearContext()
    @Test fun `regular users cannot read credentials configure or use the email sender`() {
        SecurityContextHolder.getContext().authentication = UsernamePasswordAuthenticationToken("user@example.test", "", listOf(SimpleGrantedAuthority("ROLE_USER")))
        AnnotationConfigApplicationContext(TestConfig::class.java).use { context ->
            val controller = context.getBean(AdminEmailController::class.java)
            assertThatThrownBy { controller.settings() }.isInstanceOf(AccessDeniedException::class.java)
            assertThatThrownBy { controller.save(EmailSettingsRequest(EmailConfigurationSource.ADMIN), User("user@example.test", "", emptyList())) }.isInstanceOf(AccessDeniedException::class.java)
            assertThatThrownBy { controller.test(EmailTestRequest("reader@example.test")) }.isInstanceOf(AccessDeniedException::class.java)
            assertThatThrownBy { controller.send(SendEmailRequest("reader@example.test", "Test", "Text")) }.isInstanceOf(AccessDeniedException::class.java)
            verifyNoInteractions(context.getBean(EmailSettingsService::class.java), context.getBean(EmailDeliveryService::class.java))
        }
    }
    @Test fun `disabled email rejects even direct administrator send requests`() {
        val settings = mock(EmailSettingsService::class.java); val delivery = mock(EmailDeliveryService::class.java)
        val request = SendEmailRequest("reader@example.test", "Test", "Text")
        `when`(delivery.send(request)).thenReturn(EmailDeliveryResult("DISABLED", "Email delivery is disabled"))
        assertThatThrownBy { AdminEmailController(settings, delivery).send(request) }.isInstanceOf(com.starter.exception.ForbiddenException::class.java)
    }
}
