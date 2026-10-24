package com.starter.plugins.googledrive

import com.starter.plugins.PluginActor
import com.starter.security.RateLimits
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.security.core.userdetails.UserDetails
import org.springframework.web.bind.annotation.*

@RestController @RequestMapping("/api/plugins/google-drive")
class GoogleDriveController(private val service:GoogleDriveService,private val actors:PluginActor,private val rates:RateLimits) {
    @GetMapping("/files") fun files(@AuthenticationPrincipal user:UserDetails,@RequestParam(required=false) folder:String?,@RequestParam(required=false) pageToken:String?):GoogleDriveFiles {
        val actor=actors.current(user.username);rates.check("google-drive-files",actor.id.toString(),20,60)
        return service.files(actor.role,folder,pageToken)
    }
}
