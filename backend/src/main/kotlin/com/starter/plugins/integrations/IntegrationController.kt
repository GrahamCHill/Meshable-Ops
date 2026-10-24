package com.starter.plugins.integrations

import com.starter.notification.ChannelMessage
import com.starter.security.RateLimits
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.security.core.userdetails.UserDetails
import org.springframework.web.bind.annotation.*

@RestController @PreAuthorize("hasRole('ADMIN')")
class IntegrationController(private val messaging: PluginMessagingService, private val drives: MicrosoftDriveService,
    private val rates: RateLimits) {
    @PostMapping("/api/plugins/{id:telegram|teams|whatsapp}/send")
    fun send(@PathVariable id: String, @RequestBody request: ChannelMessage, @AuthenticationPrincipal user: UserDetails): com.starter.dto.EmailDeliveryResult {
        rates.check("plugin-send-$id", user.username, 10, 60)
        return messaging.send(id, request)
    }
    @GetMapping("/api/plugins/{id:sharepoint|onedrive}/files")
    fun files(@PathVariable id: String, @AuthenticationPrincipal user: UserDetails): ExternalDrivePage {
        rates.check("plugin-files-$id", user.username, 20, 60)
        return drives.files(id)
    }
}
