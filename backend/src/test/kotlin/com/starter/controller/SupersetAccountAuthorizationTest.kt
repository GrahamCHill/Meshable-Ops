package com.starter.controller

import com.starter.service.SupersetAccountService
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
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.springframework.test.web.servlet.setup.MockMvcBuilders

class SupersetAccountAuthorizationTest {
    @Configuration @EnableMethodSecurity(proxyTargetClass = true)
    open class TestConfig {
        @Bean open fun accounts() = mock(SupersetAccountService::class.java)
        @Bean open fun controller(accounts: SupersetAccountService) = SupersetAccountController(accounts)
    }
    @AfterEach fun clear() = SecurityContextHolder.clearContext()
    @Test fun `non administrators cannot call the password controller`() {
        AnnotationConfigApplicationContext(TestConfig::class.java).use { context ->
            val controller = context.getBean(SupersetAccountController::class.java)
            listOf("USER", "EXTERNAL", "BUSINESS_MANAGER").forEach { role ->
                SecurityContextHolder.getContext().authentication = UsernamePasswordAuthenticationToken(
                    "member@example.test", "", listOf(SimpleGrantedAuthority("ROLE_$role")))
                assertThatThrownBy { controller.setPassword(SupersetPasswordRequest("A new analytics password")) }.isInstanceOf(AccessDeniedException::class.java)
            }
            verifyNoInteractions(context.getBean(SupersetAccountService::class.java))
        }
    }
    @Test fun `invalid passwords are rejected by HTTP validation before account mutation`() {
        val accounts = mock(SupersetAccountService::class.java)
        val mvc = MockMvcBuilders.standaloneSetup(SupersetAccountController(accounts)).build()
        listOf("{}", """{"password":"short"}""", """{"password":"              "}""").forEach { body ->
            mvc.perform(post("/api/superset/account/password").contentType("application/json").content(body)).andExpect(status().isBadRequest)
        }
        verifyNoInteractions(accounts)
    }
}
