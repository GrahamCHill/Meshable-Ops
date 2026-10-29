package com.starter.plugins.workflows

import com.starter.exception.ForbiddenException
import org.springframework.beans.factory.ObjectProvider
import org.springframework.stereotype.Component
import org.springframework.web.context.request.RequestContextHolder
import org.springframework.web.context.request.ServletRequestAttributes

/** Final transport boundary. Background sends cannot silently bypass a configured external-action policy. */
@Component
class WorkflowEgressGuard(private val gates: ObjectProvider<WorkflowActionGate>) {
    fun beforeSend() {
        if (securityDelivery.get() == true) return
        val gate=gates.getObject()
        if (!gate.hasExternalPolicy()) return
        val request=(RequestContextHolder.getRequestAttributes() as? ServletRequestAttributes)?.request
            ?: throw ForbiddenException("External delivery requires a workflow-approved interactive action")
        gate.enforce(request,external=true)
    }
    companion object {
        private val securityDelivery=ThreadLocal<Boolean>()
        /** Only trusted OTP delivery sets this scope; an approval flow must never block authentication itself. */
        fun <T> securityCode(block: () -> T): T {
            val previous=securityDelivery.get();securityDelivery.set(true)
            return try { block() } finally { if(previous==null) securityDelivery.remove() else securityDelivery.set(previous) }
        }
    }
}
