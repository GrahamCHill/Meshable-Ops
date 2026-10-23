package com.starter.model.postgres

import jakarta.persistence.*

enum class DownloadMode { NORMAL, WATERMARKED, DISABLED }

@Entity
@Table(name = "document_access_settings")
class DocumentAccessSettings(
    @Id val id: Long = 1,
    @Column(name = "pdf_preview_enabled", nullable = false) var pdfPreviewEnabled: Boolean = true,
    @Enumerated(EnumType.STRING) @Column(name = "download_mode", nullable = false) var downloadMode: DownloadMode = DownloadMode.NORMAL,
    @Column(name = "watermark_text", nullable = false, length = 100) var watermarkText: String = "CONFIDENTIAL",
)
