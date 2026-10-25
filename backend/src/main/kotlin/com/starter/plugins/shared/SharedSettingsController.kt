package com.starter.plugins.shared

import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.web.bind.annotation.*
import java.util.UUID

@RestController @PreAuthorize("hasRole('ADMIN')")
@RequestMapping(value=["/api/admin/email/shared-settings","/api/admin/plugins/shared-settings"])
class SharedSettingsController(private val service:SharedSettingsService) {
    @GetMapping("/schemas") fun schemas()=service.schemas()
    @GetMapping("/schemas/{schemaId}") fun schema(@PathVariable schemaId:String)=service.schema(schemaId)
    @GetMapping("/schemas/{schemaId}/profiles") fun profiles(@PathVariable schemaId:String)=service.profiles(schemaId)
    @PostMapping("/schemas/{schemaId}/profiles") fun create(@PathVariable schemaId:String,@RequestBody input:SharedProfileInput)=service.save(schemaId,null,input)
    @PutMapping("/schemas/{schemaId}/profiles/{id}") fun save(@PathVariable schemaId:String,@PathVariable id:UUID,@RequestBody input:SharedProfileInput)=service.save(schemaId,id,input)
}
@RestController @PreAuthorize("hasRole('ADMIN')") @RequestMapping("/api/admin/plugins/{id}/shared-settings")
class PluginSharedSettingsController(private val service:PluginSharedSettingsService) {
    @GetMapping fun get(@PathVariable id:String)=service.views(id)
    @PutMapping("/{reference}") fun save(@PathVariable id:String,@PathVariable reference:String,@RequestBody input:SharedBindingInput)=service.save(id,reference,input)
}
