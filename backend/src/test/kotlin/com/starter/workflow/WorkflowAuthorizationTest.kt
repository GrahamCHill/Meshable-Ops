package com.starter.workflow

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

class WorkflowAuthorizationTest {
    @Configuration @EnableMethodSecurity(proxyTargetClass=true)
    open class TestConfig {
        @Bean open fun service(): WorkflowService = mock(WorkflowService::class.java)
        @Bean open fun controller(service: WorkflowService) = WorkflowController(service)
        @Bean open fun legacy(service: WorkflowService) = LegacyWorkflowAuthoringController(service)
    }
    @AfterEach fun clear() = SecurityContextHolder.clearContext()
    @Test fun `both admin and legacy write routes reject regular users before calling authoring services`() {
        SecurityContextHolder.getContext().authentication=UsernamePasswordAuthenticationToken("member@example.test","",listOf(SimpleGrantedAuthority("ROLE_USER")))
        AnnotationConfigApplicationContext(TestConfig::class.java).use {context ->
            val user=org.springframework.security.core.userdetails.User("member@example.test","",emptyList())
            val request=WorkflowRequest("Test",graph=WorkflowGraph(emptyList(),emptyList()))
            val controller=context.getBean(WorkflowController::class.java)
            assertThatThrownBy {controller.list(user)}.isInstanceOf(AccessDeniedException::class.java)
            assertThatThrownBy {controller.create(user,request)}.isInstanceOf(AccessDeniedException::class.java)
            assertThatThrownBy {context.getBean(LegacyWorkflowAuthoringController::class.java).create(user,request)}.isInstanceOf(AccessDeniedException::class.java)
            verifyNoInteractions(context.getBean(WorkflowService::class.java))
        }
    }
}
