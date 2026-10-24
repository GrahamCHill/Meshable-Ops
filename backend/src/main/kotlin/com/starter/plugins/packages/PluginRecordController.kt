package com.starter.plugins.packages

import com.starter.plugins.PluginActor
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.security.core.userdetails.UserDetails
import org.springframework.web.bind.annotation.*
import java.util.UUID

@RestController @RequestMapping("/api/plugins/{id}/data/{table}")
class PluginRecordController(private val records: PluginRecordService, private val actors: PluginActor) {
    @GetMapping fun list(@PathVariable id: String, @PathVariable table: String, @RequestParam(defaultValue = "0") offset: Int, @AuthenticationPrincipal user: UserDetails) = records.list(id, table, actors.current(user.username), offset)
    @GetMapping("/{record}") fun get(@PathVariable id: String, @PathVariable table: String, @PathVariable record: UUID, @AuthenticationPrincipal user: UserDetails) = records.get(id, table, record, actors.current(user.username))
    @PostMapping fun create(@PathVariable id: String, @PathVariable table: String, @RequestBody input: PluginRecordInput, @AuthenticationPrincipal user: UserDetails) = records.create(id, table, input, actors.current(user.username))
    @PutMapping("/{record}") fun update(@PathVariable id: String, @PathVariable table: String, @PathVariable record: UUID, @RequestBody input: PluginRecordInput, @AuthenticationPrincipal user: UserDetails) = records.update(id, table, record, input, actors.current(user.username))
    @DeleteMapping("/{record}") fun delete(@PathVariable id: String, @PathVariable table: String, @PathVariable record: UUID, @RequestParam revision: Long, @AuthenticationPrincipal user: UserDetails) = records.delete(id, table, record, revision, actors.current(user.username))
}
