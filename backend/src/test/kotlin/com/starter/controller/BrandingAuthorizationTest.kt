package com.starter.controller

import com.starter.model.postgres.BrandingKind
import com.starter.service.branding.WorkspaceBrandingService
import com.starter.dto.BrandingPreferences
import org.assertj.core.api.Assertions.*
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import org.mockito.Mockito.*
import org.springframework.context.annotation.*
import org.springframework.mock.web.MockMultipartFile
import org.springframework.security.access.AccessDeniedException
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity
import org.springframework.security.core.authority.SimpleGrantedAuthority
import org.springframework.security.core.context.SecurityContextHolder

class BrandingAuthorizationTest {
    @Configuration @EnableMethodSecurity(proxyTargetClass = true)
    open class Config {
        @Bean open fun service() = mock(WorkspaceBrandingService::class.java)
        @Bean open fun controller(service: WorkspaceBrandingService) = AdminBrandingController(service)
    }
    @AfterEach fun clear() = SecurityContextHolder.clearContext()
    @Test fun `ordinary users cannot replace or remove public branding`() {
        SecurityContextHolder.getContext().authentication = UsernamePasswordAuthenticationToken("user@example.test", "", listOf(SimpleGrantedAuthority("ROLE_USER")))
        AnnotationConfigApplicationContext(Config::class.java).use { context ->
            val controller = context.getBean(AdminBrandingController::class.java)
            assertThatThrownBy { controller.upload(BrandingKind.LOGO, MockMultipartFile("file", byteArrayOf())) }.isInstanceOf(AccessDeniedException::class.java)
            assertThatThrownBy { controller.reset(BrandingKind.FAVICON) }.isInstanceOf(AccessDeniedException::class.java)
            assertThatThrownBy { controller.save(BrandingPreferences(applicationName = "Other business")) }.isInstanceOf(AccessDeniedException::class.java)
            assertThatThrownBy { controller.uploadBackground(0, MockMultipartFile("file", byteArrayOf())) }.isInstanceOf(AccessDeniedException::class.java)
            assertThatThrownBy { controller.removeBackground(0) }.isInstanceOf(AccessDeniedException::class.java)
            verifyNoInteractions(context.getBean(WorkspaceBrandingService::class.java))
        }
    }
}
