package com.starter.controller

import com.starter.exception.*
import com.starter.model.postgres.UserProfile
import com.starter.plugins.PluginActor
import com.starter.repository.postgres.*
import com.starter.service.branding.*
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.security.core.userdetails.UserDetails
import org.springframework.web.bind.annotation.*
import org.springframework.web.multipart.MultipartFile
import org.springframework.http.*

data class ProfileInput(val name: String, val contactNumber: String = "", val timeZone: String = "UTC", val revision: Long = -1)
data class ProfileView(val id: java.util.UUID, val name: String, val username: String, val email: String, val contactNumber: String,
    val role: String, val timeZone: String, val avatarVersion: String?, val revision: Long)

@Service
class AccountProfiles(private val actors: PluginActor, private val users: UserRepository, private val profiles: UserProfileRepository) {
    fun view(email: String): ProfileView { val user = actors.fresh(email); val row = profiles.findById(user.id).orElse(null)
        return ProfileView(user.id,user.name.orEmpty(),user.username,user.email,user.contactNumber.orEmpty(),user.role,row?.timeZone ?: "UTC",row?.avatarVersion,row?.revision ?: -1) }
    @Transactional fun save(email: String, input: ProfileInput): ProfileView {
        val contactNumber = com.starter.service.ContactNumbers.normalize(input.contactNumber)
        if (input.name.length > 120 || input.name.any(Char::isISOControl) || runCatching { java.time.ZoneId.of(input.timeZone) }.isFailure) throw BadRequestException("Use a valid name and timezone")
        val user = actors.fresh(email); val row = profiles.findById(user.id).orElse(null)
        if ((row?.revision ?: -1) != input.revision) throw ConflictException("Your profile changed. Reload before saving.")
        val updated = users.saveAndFlush(user.copy(name = input.name.trim().ifBlank { null }, contactNumber = contactNumber))
        val saved = row ?: UserProfile(user.id); saved.timeZone = input.timeZone; profiles.saveAndFlush(saved)
        return ProfileView(updated.id,updated.name.orEmpty(),updated.username,updated.email,updated.contactNumber.orEmpty(),updated.role,saved.timeZone,saved.avatarVersion,saved.revision)
    }
    @Transactional fun avatar(email: String, bytes: ByteArray?): ProfileView {
        val user = actors.fresh(email); val row = profiles.findById(user.id).orElseGet { UserProfile(user.id) }
        row.avatar = bytes?.let { BrandingImages.normalize(it, BrandingImageProfile.FAVICON) }
        row.avatarVersion = row.avatar?.let(BrandingImages::version); profiles.saveAndFlush(row); return view(email)
    }
    fun image(email: String): ByteArray = profiles.findById(actors.fresh(email).id).orElse(null)?.avatar ?: throw NotFoundException("Profile image unavailable")
}
@RestController @RequestMapping("/api/account/profile")
class AccountProfileController(private val profiles: AccountProfiles) {
    @GetMapping fun get(@AuthenticationPrincipal user: UserDetails) = profiles.view(user.username)
    @PutMapping fun save(@AuthenticationPrincipal user: UserDetails, @RequestBody input: ProfileInput) = profiles.save(user.username,input)
    @PostMapping("/avatar", consumes = ["multipart/form-data"]) fun upload(@AuthenticationPrincipal user: UserDetails, @RequestParam("file") file: MultipartFile): ProfileView {
        if (file.size > 1024 * 1024) throw BadRequestException("Choose a PNG, JPEG, or WebP up to 1 MiB")
        return profiles.avatar(user.username,file.inputStream.use { it.readNBytes(1024 * 1024 + 1) })
    }
    @DeleteMapping("/avatar") fun remove(@AuthenticationPrincipal user: UserDetails) = profiles.avatar(user.username,null)
    @GetMapping("/avatar") fun avatar(@AuthenticationPrincipal user: UserDetails) = ResponseEntity.ok().contentType(MediaType.IMAGE_PNG).header(HttpHeaders.CACHE_CONTROL,"private, no-store").header("X-Content-Type-Options","nosniff").body(profiles.image(user.username))
}
