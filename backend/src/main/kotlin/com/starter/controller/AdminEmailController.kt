package com.starter.controller

import com.starter.dto.*
import com.starter.email.EmailDeliveryException
import com.starter.email.EmailDeliveryService
import com.starter.email.EmailSettingsService
import com.starter.exception.ForbiddenException
import jakarta.validation.Valid
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.security.core.userdetails.UserDetails
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("/api/admin/email")
@PreAuthorize("hasRole('ADMIN')")
class AdminEmailController(private val settings: EmailSettingsService, private val delivery: EmailDeliveryService,private val notifications: com.starter.notification.NotificationService) {
    @GetMapping fun settings() = settings.settings()
    @PutMapping fun save(@Valid @RequestBody request: EmailSettingsRequest, @AuthenticationPrincipal user: UserDetails) = settings.save(request, user.username)
    @PostMapping("/test") fun test(@Valid @RequestBody request: EmailTestRequest) = checked(delivery.send(
        SendEmailRequest(request.recipient, "Email delivery test", "This is a test message from your application's email settings. No storage documents or credentials are included.")))
    @PostMapping("/send") fun send(@Valid @RequestBody request: SendEmailRequest): EmailDeliveryResult {
        return checked(notifications.sendEmail("general.message",request))
    }
    private fun checked(result: EmailDeliveryResult): EmailDeliveryResult {
        if (result.status == "DISABLED") throw ForbiddenException(result.message)
        if (result.status != "ACCEPTED") throw EmailDeliveryException(result.message)
        return result
    }
}
