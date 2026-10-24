package com.starter.plugins

import com.starter.model.postgres.PluginSettings
import com.starter.plugins.packages.PluginPackageRepository
import com.starter.repository.postgres.PluginSettingsRepository
import com.starter.exception.BadRequestException
import org.assertj.core.api.Assertions.*
import org.junit.jupiter.api.Test
import org.mockito.Mockito.*
import org.springframework.jdbc.core.JdbcTemplate
import java.util.Optional

class PluginLifecycleTest {
    private val registry=mock(PluginRegistry::class.java);private val settings=mock(PluginSettingsRepository::class.java)
    private val packages=mock(PluginPackageRepository::class.java);private val jdbc=mock(JdbcTemplate::class.java)
    private val row=PluginSettings("example",true,searchEnabled=true)
    private val service=PluginLifecycle(registry,settings,packages,jdbc)
    init { `when`(registry.definition("example")).thenReturn(PluginManifest("example","Example","1.0.0",""));`when`(settings.findById("example")).thenReturn(Optional.of(row)) }
    @Test fun `removal requires exact confirmation before mutation`() {assertThatThrownBy{service.remove("example",PluginClearRequest("wrong"))}.isInstanceOf(BadRequestException::class.java);verifyNoInteractions(settings,packages,jdbc)}
    @Test fun `uninstall removes uploaded package but retains business tables and configuration`() {
        `when`(packages.existsById("example")).thenReturn(true);service.remove("example",PluginClearRequest("example"))
        assertThat(row.removed).isTrue();assertThat(row.enabled).isFalse();assertThat(row.searchEnabled).isFalse()
        verify(packages).deleteById("example");verify(jdbc,never()).execute(anyString())
    }
    @Test fun `bundled restoration leaves activation opt in`() {row.removed=true;service.restore("example");assertThat(row.removed).isFalse();assertThat(row.enabled).isFalse()}
}
