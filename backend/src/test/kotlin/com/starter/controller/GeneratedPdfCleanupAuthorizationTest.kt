package com.starter.controller

import com.starter.service.GeneratedPdfCleanupService
import com.starter.service.PdfCleanupInput
import com.starter.access.AccessControlService
import com.starter.access.AccessRequestGuard
import com.starter.exception.ForbiddenException
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
import org.springframework.mock.web.MockHttpServletRequest
import org.springframework.mock.web.MockHttpServletResponse
import org.springframework.web.bind.annotation.RequestMapping

class GeneratedPdfCleanupAuthorizationTest {
    @Configuration @EnableMethodSecurity(proxyTargetClass = true)
    open class Config {
        @Bean open fun service(): GeneratedPdfCleanupService = mock(GeneratedPdfCleanupService::class.java)
        @Bean open fun controller(service: GeneratedPdfCleanupService) = GeneratedPdfCleanupController(service)
    }
    @AfterEach fun clear() = SecurityContextHolder.clearContext()

    @Test fun `members and external accounts cannot see change or run cleanup`() {
        for (role in listOf("USER", "BUSINESS_MANAGER", "EXTERNAL")) {
            SecurityContextHolder.getContext().authentication = UsernamePasswordAuthenticationToken("user@example.test", "", listOf(SimpleGrantedAuthority("ROLE_$role")))
            AnnotationConfigApplicationContext(Config::class.java).use { context ->
                val controller = context.getBean(GeneratedPdfCleanupController::class.java)
                val principal = User.withUsername("user@example.test").password("").roles(role).build()
                assertThatThrownBy { controller.settings() }.isInstanceOf(AccessDeniedException::class.java)
                assertThatThrownBy { controller.update(principal, PdfCleanupInput()) }.isInstanceOf(AccessDeniedException::class.java)
                assertThatThrownBy { controller.clear(principal) }.isInstanceOf(AccessDeniedException::class.java)
                verifyNoInteractions(context.getBean(GeneratedPdfCleanupService::class.java))
            }
        }
    }

    @Test fun `cleanup endpoints follow the existing storage administration feature permission`() {
        val path = GeneratedPdfCleanupController::class.java.getAnnotation(RequestMapping::class.java).value.single()
        val access = mock(AccessControlService::class.java)
        `when`(access.actor()).thenReturn(com.starter.model.postgres.User(role = "ADMIN"))
        doThrow(ForbiddenException("Storage administration unavailable")).`when`(access).requireFeature("admin.storage")
        for ((method, suffix) in listOf("GET" to "", "PUT" to "", "POST" to "/clear")) {
            assertThat(AccessRequestGuard.feature(path + suffix, method)).isEqualTo("admin.storage")
            assertThatThrownBy { AccessRequestGuard(access).preHandle(MockHttpServletRequest(method, path + suffix), MockHttpServletResponse(), Any()) }
                .isInstanceOf(ForbiddenException::class.java)
        }
    }
}
