package com.starter.controller

import com.fasterxml.jackson.databind.ObjectMapper
import com.starter.dto.AdminSettingsRequest
import com.starter.service.AdminSettingsService
import com.starter.service.AuditLogService
import com.starter.service.BackupExportService
import com.starter.service.StackInfoService
import com.starter.service.FolderStorageService
import jakarta.validation.Valid
import jakarta.servlet.http.HttpServletRequest
import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.security.core.userdetails.UserDetails
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import java.time.Instant

@RestController
@RequestMapping("/api/admin")
@PreAuthorize("hasRole('ADMIN')")
class AdminOperationsController(
    private val adminSettingsService: AdminSettingsService,
    private val auditLogService: AuditLogService,
    private val backupExportService: BackupExportService,
    private val objectMapper: ObjectMapper,
    private val stackInfoService: StackInfoService,
    private val folderStorageService: FolderStorageService,
) {
    @GetMapping("/settings")
    fun getSettings() = adminSettingsService.getSettings()

    @GetMapping("/stack")
    fun getStackInfo() = stackInfoService.getStackInfo()

    @PutMapping("/settings")
    fun updateSettings(
        @Valid @RequestBody req: AdminSettingsRequest,
        @AuthenticationPrincipal principal: UserDetails,
        request: HttpServletRequest,
    ): ResponseEntity<Any> {
        val response = adminSettingsService.updateSettings(req)
        auditLogService.record(
            actingEmail = principal.username,
            action = "ADMIN_SETTINGS_UPDATED",
            resourceType = "APP_SETTINGS",
            resourceId = response.id,
            ipAddress = request.remoteAddr,
            userAgent = request.getHeader("User-Agent"),
        )
        return ResponseEntity.ok(response)
    }

    @PostMapping("/settings/test-storage")
    fun testStorage(@Valid @RequestBody req: AdminSettingsRequest) = folderStorageService.testConnection(req)

    @GetMapping("/audit-logs")
    fun auditLogs(
        @RequestParam(defaultValue = "0") page: Int,
        @RequestParam(defaultValue = "20") size: Int,
    ) = auditLogService.list(page, size)

    @PostMapping("/backup")
    fun exportBackup(
        @AuthenticationPrincipal principal: UserDetails,
        request: HttpServletRequest,
    ): ResponseEntity<ByteArray> {
        val backup = backupExportService.export()
        val payload = objectMapper.writerWithDefaultPrettyPrinter().writeValueAsBytes(backup)
        val filename = "app-backup-${Instant.now().toString().replace(":", "-")}.json"

        auditLogService.record(
            actingEmail = principal.username,
            action = "BACKUP_EXPORTED",
            resourceType = "BACKUP",
            resourceId = filename,
            ipAddress = request.remoteAddr,
            userAgent = request.getHeader("User-Agent"),
        )

        return ResponseEntity.ok()
            .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"$filename\"")
            .contentType(MediaType.APPLICATION_JSON)
            .body(payload)
    }
}
