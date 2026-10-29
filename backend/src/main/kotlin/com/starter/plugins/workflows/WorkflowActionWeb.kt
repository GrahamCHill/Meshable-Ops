package com.starter.plugins.workflows

import com.starter.exception.BadRequestException
import jakarta.servlet.FilterChain
import jakarta.servlet.http.*
import org.springframework.core.MethodParameter
import org.springframework.core.Ordered
import org.springframework.core.annotation.Order
import org.springframework.http.HttpInputMessage
import org.springframework.http.converter.HttpMessageConverter
import org.springframework.stereotype.Component
import org.springframework.web.bind.annotation.*
import org.springframework.web.method.HandlerMethod
import org.springframework.web.servlet.*
import org.springframework.web.servlet.config.annotation.*
import org.springframework.web.servlet.mvc.method.annotation.RequestBodyAdviceAdapter
import org.springframework.web.context.request.*
import org.springframework.web.filter.OncePerRequestFilter
import org.springframework.web.util.ContentCachingRequestWrapper
import java.lang.reflect.Type
import java.util.UUID

@Component @Order(Ordered.LOWEST_PRECEDENCE-20)
class WorkflowRequestCapture : OncePerRequestFilter() {
    override fun doFilterInternal(request: HttpServletRequest,response: HttpServletResponse,chain: FilterChain) {
        if (request.contentType?.startsWith("multipart/",ignoreCase=true)==true) { chain.doFilter(request,response);return }
        // Multipart content is hashed from the existing bounded upload files rather than retained twice in memory.
        val wrapped=object : ContentCachingRequestWrapper(request,2_097_152) {
            override fun handleContentOverflow(contentCacheLimit: Int) { request.setAttribute(WorkflowActionGate.STREAM_ADAPTER_REQUIRED,true) }
        }
        chain.doFilter(wrapped,response)
    }
}

@Component
class WorkflowActionInterceptor(private val gate: WorkflowActionGate) : HandlerInterceptor {
    override fun preHandle(request: HttpServletRequest,response: HttpServletResponse,handler: Any): Boolean {
        if (handler is HandlerMethod && handler.methodParameters.none { it.hasParameterAnnotation(RequestBody::class.java) }) {
            val streamed=handler.methodParameters.any { HttpServletRequest::class.java.isAssignableFrom(it.parameterType) ||
                java.io.InputStream::class.java.isAssignableFrom(it.parameterType) || java.io.Reader::class.java.isAssignableFrom(it.parameterType) }
            if(streamed && request.method !in setOf("GET","HEAD","OPTIONS") && request.contentLengthLong!=0L &&
                request.contentType?.startsWith("multipart/",true)!=true && request.contentType?.startsWith("application/x-www-form-urlencoded",true)!=true)
                request.setAttribute(WorkflowActionGate.STREAM_ADAPTER_REQUIRED,true)
            gate.enforce(request)
        }
        return true
    }
}
@org.springframework.context.annotation.Configuration
class WorkflowActionWeb(private val interceptor: WorkflowActionInterceptor) : WebMvcConfigurer {
    override fun addInterceptors(registry: InterceptorRegistry) { registry.addInterceptor(interceptor).addPathPatterns("/api/**").order(1000) }
}
@RestControllerAdvice
class WorkflowActionBodyAdvice(private val gate: WorkflowActionGate) : RequestBodyAdviceAdapter() {
    override fun supports(parameter: MethodParameter,targetType: Type,converterType: Class<out HttpMessageConverter<*>>) = true
    override fun afterBodyRead(body: Any,inputMessage: HttpInputMessage,parameter: MethodParameter,targetType: Type,converterType: Class<out HttpMessageConverter<*>>): Any {
        val request=(RequestContextHolder.getRequestAttributes() as? ServletRequestAttributes)?.request ?: return body
        request.setAttribute(WorkflowActionGate.BODY,body)
        gate.enforce(request,body)
        return body
    }
    override fun handleEmptyBody(body: Any?,inputMessage: HttpInputMessage,parameter: MethodParameter,targetType: Type,converterType: Class<out HttpMessageConverter<*>>): Any? {
        (RequestContextHolder.getRequestAttributes() as? ServletRequestAttributes)?.request?.let { gate.enforce(it) }
        return body
    }
}
@RestController @RequestMapping("/api/admin/workflows") @org.springframework.security.access.prepost.PreAuthorize("hasRole('ADMIN')")
class WorkflowBindingController(private val service: WorkflowBindingService) {
    @GetMapping("/actions") fun actions(@org.springframework.security.core.annotation.AuthenticationPrincipal user: org.springframework.security.core.userdetails.UserDetails) = service.actions(user.username)
    @GetMapping("/bindings") fun bindings(@org.springframework.security.core.annotation.AuthenticationPrincipal user: org.springframework.security.core.userdetails.UserDetails) = service.list(user.username)
    @PostMapping("/bindings") fun create(@org.springframework.security.core.annotation.AuthenticationPrincipal user: org.springframework.security.core.userdetails.UserDetails,@jakarta.validation.Valid @RequestBody input: WorkflowBindingRequest) = service.save(user.username,null,input)
    @PutMapping("/bindings/{id}") fun update(@org.springframework.security.core.annotation.AuthenticationPrincipal user: org.springframework.security.core.userdetails.UserDetails,@PathVariable id: UUID,@jakarta.validation.Valid @RequestBody input: WorkflowBindingRequest) = service.save(user.username,id,input)
}
