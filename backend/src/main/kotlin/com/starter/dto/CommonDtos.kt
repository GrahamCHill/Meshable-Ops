package com.starter.dto

import jakarta.validation.constraints.*

data class MessageResponse(val message: String)

data class PageResponse<T>(
    val content: List<T>,
    val page: Int,
    val size: Int,
    val totalElements: Long,
    val totalPages: Int
)
