package com.starter.dto

import jakarta.validation.constraints.*

data class EventResponse(
    val id: String?,
    val type: String,
    val payload: Map<String, Any>,
    val createdAt: String?
)
