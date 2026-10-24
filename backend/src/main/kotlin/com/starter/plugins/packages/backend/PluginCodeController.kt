package com.starter.plugins.packages.backend

import com.starter.exception.NotFoundException
import com.starter.plugins.packages.InstalledPluginCatalogue
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.web.bind.annotation.*

@RestController @PreAuthorize("hasRole('ADMIN')")
class PluginCodeController(private val packages: InstalledPluginCatalogue, private val worker: PluginWorkerClient) {
    @GetMapping("/api/admin/plugins/{id}/code")
    fun info(@PathVariable id: String): Map<String,Any?> {
        val record = packages.find(id) ?: throw NotFoundException("Uploaded package unavailable")
        val descriptor = packages.descriptor(record)
        return mapOf("backend" to descriptor.backend, "runnerConfigured" to worker.configured, "sha256" to record.sha256)
    }
}
