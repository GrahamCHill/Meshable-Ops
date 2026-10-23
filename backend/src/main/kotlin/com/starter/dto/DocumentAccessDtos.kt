package com.starter.dto

import com.starter.model.postgres.DownloadMode
import jakarta.validation.constraints.Pattern
import jakarta.validation.constraints.Size

data class DocumentAccessPolicy(
    val pdfPreviewEnabled: Boolean = true,
    val downloadMode: DownloadMode = DownloadMode.NORMAL,
    val watermarkText: String = "CONFIDENTIAL",
)

data class DocumentAccessRequest(
    val pdfPreviewEnabled: Boolean,
    val downloadMode: DownloadMode,
    @field:Size(min = 1, max = 100)
    @field:Pattern(regexp = "[^\\p{Cntrl}]+", message = "Use a single line without control characters")
    val watermarkText: String = "CONFIDENTIAL",
)

data class PdfPreviewResponse(val filename: String, val pageCount: Int)
