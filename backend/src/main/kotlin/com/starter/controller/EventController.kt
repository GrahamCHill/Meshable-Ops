package com.starter.controller

import com.starter.dto.*
import com.starter.repository.mongo.EventRepository
import com.starter.service.*
import org.springframework.data.domain.PageRequest
import org.springframework.data.domain.Sort
import org.springframework.security.access.prepost.PreAuthorize
import com.starter.exception.BadRequestException
import org.springframework.web.bind.annotation.*

@RestController
@PreAuthorize("hasRole('ADMIN')")
class EventController(
    private val eventRepository: EventRepository,
    private val history:EventHistoryService
) {
    @GetMapping("/api/admin/events")
    fun history(@RequestParam(required=false) cursor:String?,@RequestParam(defaultValue="50") size:Int)=history.history(cursor,size)

    /** Existing integrations keep the page API, with the same administrator gate. */
    @GetMapping("/api/events")
    fun list(
        @RequestParam(defaultValue = "0") page: Int,
        @RequestParam(defaultValue = "20") size: Int
    ): PageResponse<EventResponse> {
        if(page<0||size !in 1..100)throw BadRequestException("Use a non-negative event page and a size between 1 and 100")
        val p = eventRepository.findAllByOrderByCreatedAtDesc(PageRequest.of(page, size,Sort.by(Sort.Direction.DESC,"id")))
        return PageResponse(
            content = p.content.map { EventResponse(it.id, it.type, it.payload, it.createdAt.toString()) },
            page = p.number, size = p.size, totalElements = p.totalElements, totalPages = p.totalPages
        )
    }
}
