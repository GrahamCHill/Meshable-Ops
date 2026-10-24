package com.starter.plugins

import com.starter.exception.*
import com.starter.model.postgres.PluginSettings
import com.starter.repository.postgres.PluginSettingsRepository
import org.assertj.core.api.Assertions.*
import org.junit.jupiter.api.Test
import org.mockito.Mockito.*

class PluginActivationTest {
    private val settings=mock(PluginSettingsRepository::class.java)
    private val registry=mock(PluginRegistry::class.java)
    private val service=PluginActivationService(settings,registry)
    private val first=PluginSettings("first",allowedRolesCsv="ADMIN",searchEnabled=true,tableAccessJson="{\"retained\":true}",revision=2)
    private val second=PluginSettings("second",enabled=true,revision=4)
    private fun prepare() {
        `when`(settings.lockForActivation(listOf("first","second"))).thenReturn(listOf(first,second))
        listOf("first","second").forEach { `when`(registry.definition(it)).thenReturn(PluginManifest(it,it,"1.0.0-a","Alpha plugin")) }
        `when`(registry.installed()).thenReturn(emptyList())
    }
    @Test fun `batch activation patches only enabled flags and retains access search and configuration`() {
        prepare()
        service.apply(PluginActivationRequest(listOf(PluginActivationChange("first",true,2),PluginActivationChange("second",false,4))))
        assertThat(first.enabled).isTrue();assertThat(second.enabled).isFalse()
        assertThat(first.allowedRolesCsv).isEqualTo("ADMIN");assertThat(first.searchEnabled).isTrue();assertThat(first.tableAccessJson).contains("retained")
        verify(settings).saveAllAndFlush(listOf(first,second))
    }
    @Test fun `stale or removed choices reject the entire batch before any flags are changed`() {
        prepare()
        assertThatThrownBy { service.apply(PluginActivationRequest(listOf(PluginActivationChange("first",true,2),PluginActivationChange("second",false,3)))) }.isInstanceOf(ConflictException::class.java)
        assertThat(first.enabled).isFalse();assertThat(second.enabled).isTrue()
        second.removed=true
        assertThatThrownBy { service.apply(PluginActivationRequest(listOf(PluginActivationChange("first",true,2),PluginActivationChange("second",false,4)))) }.isInstanceOf(BadRequestException::class.java)
        assertThat(first.enabled).isFalse();verify(settings,never()).saveAllAndFlush(anyList())
    }
    @Test fun `empty and duplicate batches are rejected`() {
        listOf(emptyList(),listOf(PluginActivationChange("first",true,-1),PluginActivationChange("first",false,-1))).forEach {
            assertThatThrownBy { service.apply(PluginActivationRequest(it)) }.isInstanceOf(BadRequestException::class.java)
        }
        verifyNoInteractions(settings,registry)
    }
}
