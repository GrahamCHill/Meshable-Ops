package com.starter.service

import com.starter.dto.*
import com.starter.exception.*
import com.starter.model.postgres.BrandingPreferencesRecord
import com.starter.repository.postgres.*
import com.starter.service.branding.BrandingPreferencesService
import org.assertj.core.api.Assertions.*
import org.junit.jupiter.api.Test
import org.mockito.ArgumentMatchers.any
import org.mockito.Mockito.*
import java.util.Optional

class BrandingPreferencesTest {
    private val repository = mock(BrandingPreferencesRepository::class.java)
    private val images = mock(BrandingLoginImageRepository::class.java)
    private val service = BrandingPreferencesService(repository, images)
    private var stored: BrandingPreferencesRecord? = null
    init {
        `when`(repository.findById(1)).thenAnswer { Optional.ofNullable(stored) }
        `when`(repository.saveAndFlush(any(BrandingPreferencesRecord::class.java))).thenAnswer { call ->
            call.getArgument<BrandingPreferencesRecord>(0).also { stored = it }
        }
    }
    @Test fun `defaults need no seed and validated names and colours persist`() {
        assertThat(service.current()).isEqualTo(BrandingPreferences())
        assertThat(stored).isNull()
        val value = service.save(BrandingPreferences(applicationName = "  A & B Trading  ", sidebarBackgroundColor = "#2A4B6C", sidebarTextColor = "#FFFFFF", linkColor = " "))
        assertThat(value.applicationName).isEqualTo("A & B Trading")
        assertThat(value.sidebarBackgroundColor).isEqualTo("#2a4b6c")
        assertThat(value.sidebarTextColor).isEqualTo("#ffffff")
        assertThat(value.linkColor).isNull()
        assertThat(service.current()).isEqualTo(value)
    }
    @Test fun `CSS injection controls empty names and invalid timing are rejected`() {
        listOf(
            BrandingPreferences(applicationName = " "), BrandingPreferences(applicationName = "a".repeat(81)),
            BrandingPreferences(applicationName = "name\nother"), BrandingPreferences(linkColor = "red;display:none"),
            BrandingPreferences(sidebarBackgroundColor = "url(https://example.com)"), BrandingPreferences(sidebarTextColor = "#123"),
            BrandingPreferences(slideshowSeconds = 4), BrandingPreferences(slideshowSeconds = 61), BrandingPreferences(loginBackgroundSlot = 5),
        ).forEach { request -> assertThatThrownBy { service.save(request) }.isInstanceOf(BadRequestException::class.java) }
        assertThat(stored).isNull()
    }
    @Test fun `backgrounds require an available image and single selection must exist`() {
        assertThatThrownBy { service.save(BrandingPreferences(loginBackgroundMode = LoginBackgroundMode.SLIDESHOW)) }.isInstanceOf(BadRequestException::class.java)
        `when`(images.count()).thenReturn(2)
        assertThatThrownBy { service.save(BrandingPreferences(loginBackgroundMode = LoginBackgroundMode.SINGLE, loginBackgroundSlot = 3)) }.isInstanceOf(BadRequestException::class.java)
        `when`(images.existsById(3)).thenReturn(true)
        val value = service.save(BrandingPreferences(loginBackgroundMode = LoginBackgroundMode.SINGLE, loginBackgroundSlot = 3, slideshowSeconds = 12))
        assertThat(value.loginBackgroundSlot).isEqualTo(3)
        assertThat(value.slideshowSeconds).isEqualTo(12)
        assertThat(service.save(value.copy(loginBackgroundMode = LoginBackgroundMode.NONE)).loginBackgroundMode).isEqualTo(LoginBackgroundMode.NONE)
    }
    @Test fun `stale edits do not replace saved business settings`() {
        stored = BrandingPreferencesRecord(applicationName = "Existing", revision = 3)
        assertThatThrownBy { service.save(BrandingPreferences(applicationName = "Stale", revision = 2)) }.isInstanceOf(ConflictException::class.java)
        assertThat(stored!!.applicationName).isEqualTo("Existing")
        verify(repository, never()).saveAndFlush(any(BrandingPreferencesRecord::class.java))
    }
}
