package com.starter.controller

import com.starter.dto.*
import com.starter.service.*
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.security.core.userdetails.UserDetails
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("/api/stack")
class StackController(
    private val stackInfoService: StackInfoService,
) {
    @GetMapping
    fun stack(@AuthenticationPrincipal principal: UserDetails) = stackInfoService.getStackInfo()
}
