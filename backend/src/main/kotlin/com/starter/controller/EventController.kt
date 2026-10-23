package com.starter.controller

import com.starter.dto.*
import com.starter.repository.mongo.EventRepository
import com.starter.repository.postgres.UserRepository
import com.starter.service.*
import org.springframework.data.domain.PageRequest
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.security.core.userdetails.UserDetails
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("/api/events")
class EventController(
    private val eventRepository: EventRepository,
    private val userRepository: UserRepository
) {
    @GetMapping
    fun list(
        @AuthenticationPrincipal principal: UserDetails,
        @RequestParam(defaultValue = "0") page: Int,
        @RequestParam(defaultValue = "20") size: Int
    ): PageResponse<EventResponse> {
        val p = eventRepository.findAllByOrderByCreatedAtDesc(PageRequest.of(page, size))
        return PageResponse(
            content = p.content.map { EventResponse(it.id, it.type, it.payload, it.createdAt.toString()) },
            page = p.number, size = p.size, totalElements = p.totalElements, totalPages = p.totalPages
        )
    }
}
