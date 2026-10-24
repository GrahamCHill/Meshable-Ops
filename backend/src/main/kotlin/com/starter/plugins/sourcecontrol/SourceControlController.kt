package com.starter.plugins.sourcecontrol

import com.starter.plugins.PluginActor
import com.starter.security.RateLimits
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.security.core.userdetails.UserDetails
import org.springframework.web.bind.annotation.*

@RestController
class SourceControlController(private val service: SourceControlService, private val actors: PluginActor, private val rates: RateLimits) {
    @GetMapping("/api/plugins/{id:github|gitlab|gitea}/repositories")
    fun repositories(@PathVariable id: String, @AuthenticationPrincipal user: UserDetails) = service.repositories(id, actors.current(user.username).role)
    @GetMapping("/api/plugins/{id:github|gitlab|gitea}/pulls")
    fun pulls(@PathVariable id: String, @RequestParam repository: String, @AuthenticationPrincipal user: UserDetails): List<DevelopmentResource> {
        rates.check("source-pulls-$id", user.username, 30, 60)
        val actor = actors.current(user.username)
        val result = service.pulls(id, repository, actor.role)
        val current = actors.current(user.username)
        if (current.id != actor.id || current.role != actor.role) throw com.starter.exception.ForbiddenException("Access changed. Try again.")
        return result
    }
}
