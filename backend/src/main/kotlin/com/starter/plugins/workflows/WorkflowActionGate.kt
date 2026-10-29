package com.starter.plugins.workflows

import com.starter.exception.ForbiddenException
import jakarta.servlet.http.HttpServletRequest
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.stereotype.Service
import org.springframework.web.servlet.HandlerMapping
import org.springframework.web.util.ContentCachingRequestWrapper
import org.springframework.web.util.WebUtils
import java.security.MessageDigest

@Service
class WorkflowActionGate(private val bindings: WorkflowActionBindingRepository, private val catalogue: WorkflowActionCatalogue,
    private val providers: List<WorkflowActionContextProvider>, private val store: WorkflowActionStore) {
    fun enforce(request: HttpServletRequest, body: Any? = null, external: Boolean = false) {
        // Match MVC's decoded path segments, excluding matrix parameters. Raw URL aliases must not bypass policies.
        val rawPath=request.requestURI.removePrefix(request.contextPath)
        val path=org.springframework.http.server.PathContainer.parsePath(rawPath).elements().joinToString("") {
            if(it is org.springframework.http.server.PathContainer.PathSegment) it.valueToMatch() else it.value()
        }
        if (!catalogue.eligible(path) && !external) return
        val configured=bindings.findByEnabledTrueOrderById()
        if (configured.isEmpty()) return
        val pattern=request.getAttribute(HandlerMapping.BEST_MATCHING_PATTERN_ATTRIBUTE)?.toString() ?: path
        val supported=providers.filter { it.supports(path) }
        val possible=supported.flatMap { it.actionsFor(request.method,path) }
        val isExternal=external || catalogue.external(path) || possible.any { it.external }
        val selectors=mutableSetOf("route:${request.method} $pattern")
        if (request.method !in setOf("GET","HEAD","OPTIONS") || isExternal) {
            selectors += "plugin:${catalogue.plugin(path)}"
            selectors += "plugin:${catalogue.plugin(pattern)}"
        }
        if (isExternal) selectors += "category:external"
        val logical=possible.map { it.id }.toSet()
        if (configured.none { it.selector in selectors || it.selector in logical }) return
        val email=SecurityContextHolder.getContext().authentication?.takeIf { it.isAuthenticated && it.name!="anonymousUser" }?.name
            ?: throw ForbiddenException("Authentication is required for this action")
        val values=mutableMapOf("trigger.action" to "${request.method} $pattern", "trigger.path" to path, "trigger.plugin" to catalogue.plugin(path))
        // Provider context is derived from current, authorized server records, never browser-supplied approval flags.
        if (configured.any { it.selector in selectors || it.selector in logical }) supported.forEach {
            val context=it.context(email,path,body ?: request.getAttribute(BODY))
            selectors += context.selectors;values += context.values
        }
        val matches=configured.filter { it.selector in selectors && (it.contextField.isEmpty() || values[it.contextField]==it.contextEquals ||
            it.contextField=="trigger.kanban.targetColumnIds" && values[it.contextField]?.split(',')?.contains(it.contextEquals)==true) }
        @Suppress("UNCHECKED_CAST")
        val authorized=request.getAttribute(AUTHORIZED) as? Set<java.util.UUID> ?: emptySet()
        val needed=matches.filter { it.id !in authorized }
        if (needed.isEmpty()) return
        if (values.size>60 || values.any { it.value.length>500 }) throw com.starter.exception.BadRequestException("This action exceeds workflow context limits. Split bulk changes into smaller actions before review.")
        if(request.getAttribute(STREAM_ADAPTER_REQUIRED)==true) throw com.starter.exception.BadRequestException("This streamed action needs a reviewed JSON or multipart workflow payload adapter before it can be bound")
        val fingerprint=fingerprint(request)
        val approvals=needed.map { store.prepare(email,it.id,fingerprint,values) }
        approvals.firstOrNull { !store.completed(email,it) }?.let { approval ->
            throw WorkflowRequiredException(WorkflowActionRequired("Complete the required workflow before this action continues",workflowRequestId=approval.id,
                runId=approval.runId,workflowName="Required workflow"))
        }
        val outputs=store.consume(email,approvals,fingerprint)
        @Suppress("UNCHECKED_CAST")
        val previous=request.getAttribute(EXPORT_OPTIONS) as? Map<String,String> ?: emptyMap()
        if (outputs.any { (key,value) -> previous.containsKey(key) && previous[key]!=value }) throw com.starter.exception.ConflictException("Required workflows disagree on export options")
        request.setAttribute(EXPORT_OPTIONS,previous + outputs)
        request.setAttribute(AUTHORIZED,authorized + needed.map { it.id })
    }
    fun hasExternalPolicy() = bindings.findByEnabledTrueOrderById().any { it.selector=="category:external" }

    companion object {
        const val BODY="meshable.workflow.body"
        const val EXPORT_OPTIONS="meshable.workflow.export-options"
        const val STREAM_ADAPTER_REQUIRED="meshable.workflow.stream-adapter-required"
        const val AUTHORIZED="meshable.workflow.authorized"
        fun fingerprint(request: HttpServletRequest): String {
            val digest=MessageDigest.getInstance("SHA-256")
            fun add(bytes: ByteArray) { digest.update(bytes.size.toString().toByteArray());digest.update(0.toByte());digest.update(bytes) }
            add(request.method.toByteArray());add(request.requestURI.toByteArray())
            add(request.contentType.orEmpty().substringBefore(';').lowercase().toByteArray())
            add(request.characterEncoding.orEmpty().toByteArray())
            request.parameterMap.toSortedMap().forEach { (key, values) -> add(key.toByteArray());values.forEach { add(it.toByteArray()) } }
            val cache=WebUtils.getNativeRequest(request,ContentCachingRequestWrapper::class.java)
            add(cache?.contentAsByteArray ?: byteArrayOf())
            if (request is org.springframework.web.multipart.MultipartHttpServletRequest) request.parts.sortedBy { it.name }.forEach { part ->
                    add(part.name.toByteArray());add(part.submittedFileName.orEmpty().toByteArray());add(part.contentType.orEmpty().toByteArray())
                    val hash=MessageDigest.getInstance("SHA-256")
                    part.inputStream.use { input -> val buffer=ByteArray(8192);var size=input.read(buffer);while(size!=-1) { hash.update(buffer,0,size);size=input.read(buffer) } }
                    add(hash.digest())
            }
            return java.util.HexFormat.of().formatHex(digest.digest())
        }
    }
}
