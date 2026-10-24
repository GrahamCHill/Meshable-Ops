package com.starter.controller

import com.starter.dto.*
import com.starter.service.*
import com.starter.ai.AiAnswerService
import com.starter.ai.AiSettingsService
import com.starter.dto.AiQuestionRequest
import jakarta.validation.Valid
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.security.core.userdetails.UserDetails
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("/api/search")
class SearchController(
    private val searchService: SearchService,
    private val answers: AiAnswerService,
    private val aiSettings: AiSettingsService,
) {

    @GetMapping("/status") fun status() = aiSettings.status()
    @PostMapping("/ask") fun ask(@Valid @RequestBody req: AiQuestionRequest) = answers.ask(req)

    @PostMapping
    fun search(
        @AuthenticationPrincipal user: UserDetails,
        @Valid @RequestBody req: VectorSearchRequest
    ): SearchResponse = searchService.search(req, user.username)
}
