package com.starter.notification

import com.starter.plugins.PluginActor
import com.starter.security.RateLimits
import jakarta.validation.Valid
import org.springframework.data.domain.PageRequest
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.security.core.userdetails.UserDetails
import org.springframework.web.bind.annotation.*

@RestController @RequestMapping("/api/admin/notifications") @PreAuthorize("hasRole('ADMIN')")
class AdminNotificationController(private val settings: NotificationSettingsService,private val catalogue: NotificationCatalogue,
    private val transports: NotificationTransports,private val renderer: NotificationRenderer,private val deliveries: NotificationDeliveryRepository,
    private val notifications: NotificationService,private val publisher: NotificationPublisher,private val actors: PluginActor,private val rates: RateLimits) {
    @GetMapping fun settings(@AuthenticationPrincipal user: UserDetails): NotificationSettingsView {actors.fresh(user.username);return settings.view()}
    @GetMapping("/members") fun members(@AuthenticationPrincipal user: UserDetails): List<Map<String, Any>> { actors.fresh(user.username); return publisher.internalMembers() }
    @PutMapping fun save(@AuthenticationPrincipal user: UserDetails,@Valid @RequestBody request: NotificationSettingsRequest): NotificationSettingsView {actors.fresh(user.username);return settings.save(request)}
    @PostMapping("/preview") fun preview(@AuthenticationPrincipal user: UserDetails,@Valid @RequestBody request: NotificationPreviewRequest): NotificationPreview {
        actors.fresh(user.username);return renderer.preview(catalogue.get(request.notificationId),transports.get(request.methodId),request.template)
    }
    @GetMapping("/deliveries") fun history(@AuthenticationPrincipal user: UserDetails): List<NotificationDelivery> {actors.fresh(user.username);return deliveries.findAllByOrderByCreatedAtDesc(PageRequest.of(0,200))}
    @PostMapping("/send") fun send(@AuthenticationPrincipal user: UserDetails,@Valid @RequestBody request: NotificationSendRequest): Map<String,com.starter.dto.EmailDeliveryResult> {
        val actor=actors.fresh(user.username);rates.check("notification-manual",actor.id.toString(),10,300)
        return if(request.userId!=null) publisher.publish("general.message",request.userId,mapOf("subject" to request.subject,"message" to request.message),selectedMethods=setOf(request.methodId))
        else mapOf(request.methodId to notifications.deliver("general.message",request.methodId,NotificationTarget(email=request.recipient,phone=request.recipient),mapOf("subject" to request.subject,"message" to request.message)))
    }
}
@RestController @RequestMapping("/api/account/notifications")
class NotificationPreferencesController(private val preferences: NotificationPreferencesService) {
    @GetMapping fun get(@AuthenticationPrincipal user: UserDetails)=preferences.view(user.username)
    @PutMapping fun save(@AuthenticationPrincipal user: UserDetails,@Valid @RequestBody request: NotificationPreferenceRequest)=preferences.save(user.username,request)
}
@RestController @RequestMapping("/api/notifications")
class NotificationAvailabilityController(private val notifications: NotificationService,private val actors: PluginActor) {
    @GetMapping("/availability") fun availability(@AuthenticationPrincipal user: UserDetails): Map<String,Boolean> {
        actors.fresh(user.username)
        return mapOf("documentsEmail" to notifications.availableFor("documents.email","email"),"workflowEmail" to notifications.availableFor("workflow.email","email"),"sharesEmail" to notifications.availableFor("shares.email","email"))
    }
}
