package com.starter.controller

import com.starter.ai.AiSettingsService
import com.starter.dto.AiSettingsRequest
import com.starter.dto.AiEndpointCheckRequest
import com.starter.dto.AiModelCheckResponse
import com.starter.dto.AiModelCheckStatus
import com.starter.ai.AiProviderClient
import com.starter.ai.EmbeddingService
import com.starter.dto.MessageResponse
import com.starter.exception.BadRequestException
import jakarta.validation.Valid
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.security.core.userdetails.UserDetails
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("/api/admin/ai")
@PreAuthorize("hasRole('ADMIN')")
class AdminAiController(private val settings: AiSettingsService, private val provider: AiProviderClient, private val embeddings: EmbeddingService) {
    @GetMapping fun settings() = settings.settings()
    @PutMapping fun save(@Valid @RequestBody req: AiSettingsRequest, @AuthenticationPrincipal user: UserDetails) = settings.save(req, user.username)
    @PostMapping("/models/{kind}") fun models(@PathVariable kind: String, @Valid @RequestBody req: AiEndpointCheckRequest) =
        provider.models(settings.endpointForCheck(kind, req))
    @PostMapping("/check/{kind}") fun check(@PathVariable kind: String, @Valid @RequestBody req: AiEndpointCheckRequest): AiModelCheckResponse {
        val endpoint = settings.endpointForCheck(kind, req)
        if (kind == "embeddings") return embeddings.check(endpoint)
        provider.answer(endpoint, "This is a connectivity check. Reply with OK.", "Reply with OK.")
        return AiModelCheckResponse(AiModelCheckStatus.COMPATIBLE, endpoint.model, "Verified: this model answered a short chat test. No storage documents were sent.")
    }
    @PostMapping("/test/{kind}") fun test(@PathVariable kind: String): MessageResponse {
        when (kind) {
            "chat" -> provider.answer(settings.current().chat, "This is a connectivity check. Reply with OK.", "Reply with OK.")
            "embeddings" -> embeddings.embed(listOf("Connectivity check."))
            else -> throw BadRequestException("Choose chat or embeddings")
        }
        return MessageResponse("${kind.replaceFirstChar { it.uppercase() }} connection verified. No storage documents were sent.")
    }
}
