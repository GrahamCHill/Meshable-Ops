package com.starter.plugins.workflows

import com.starter.exception.ForbiddenException
import com.starter.dto.SendEmailRequest
import com.starter.email.*
import org.assertj.core.api.Assertions.*
import org.junit.jupiter.api.Test
import org.mockito.Mockito.*
import org.springframework.beans.factory.ObjectProvider
import org.springframework.web.context.request.*

class WorkflowEgressGuardTest {
    @Test fun `a configured outbound policy fails closed without an interactive approval and OTP scope is bounded`() {
        val gate=mock(WorkflowActionGate::class.java)
        @Suppress("UNCHECKED_CAST") val provider=mock(ObjectProvider::class.java) as ObjectProvider<WorkflowActionGate>
        `when`(provider.getObject()).thenReturn(gate)
        `when`(gate.hasExternalPolicy()).thenReturn(true)
        val guard=WorkflowEgressGuard(provider)
        RequestContextHolder.resetRequestAttributes()
        assertThatThrownBy {guard.beforeSend()}.isInstanceOf(ForbiddenException::class.java)
        WorkflowEgressGuard.securityCode { guard.beforeSend() }
        assertThatThrownBy {guard.beforeSend()}.isInstanceOf(ForbiddenException::class.java)
    }
    @Test fun `an email guard denies before provider dispatch rather than claiming an uncertain send`() {
        val guard=mock(WorkflowEgressGuard::class.java)
        val settings=mock(EmailSettingsService::class.java)
        val provider=mock(EmailProviderClient::class.java)
        doThrow(ForbiddenException("Approval required")).`when`(guard).beforeSend()
        assertThatThrownBy {EmailDeliveryService(settings,provider,guard).send(SendEmailRequest("recipient@example.test","Subject","Message"))}
            .isInstanceOf(ForbiddenException::class.java)
        verifyNoInteractions(provider,settings)
    }
}
