package com.starter.plugins.sourcecontrol

import com.starter.plugins.PluginActor
import com.starter.security.RateLimits
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.security.core.userdetails.UserDetails
import org.springframework.web.bind.annotation.*
import org.springframework.security.access.prepost.PreAuthorize

@RestController
class SourceControlController(private val service: SourceControlService, private val actors: PluginActor, private val rates: RateLimits) {
    @GetMapping("/api/admin/plugins/{id:github|gitlab|gitea}/repository-options") @PreAuthorize("hasRole('ADMIN')")
    fun discover(@PathVariable id:String,@RequestParam(defaultValue="1") page:Int,@AuthenticationPrincipal user:UserDetails):RepositoryOptions {
        rates.check("source-discovery-$id",user.username,30,60);return service.repositoryOptions(id,page)
    }
    @GetMapping("/api/plugins/{id:github|gitlab|gitea}/repository-options")
    fun options(@PathVariable id:String,@RequestParam(defaultValue="1") page:Int,@AuthenticationPrincipal user:UserDetails):RepositoryOptions {
        rates.check("source-repositories-$id",user.username,30,60);return service.repositoryOptions(id,page,actors.current(user.username).role)
    }
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
