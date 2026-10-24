package com.starter.plugins

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.starter.exception.BadRequestException
import com.starter.plugins.operations.*
import com.starter.plugins.integrations.IntegrationHttpClient
import com.starter.security.SecretEncryption
import org.assertj.core.api.Assertions.*
import org.junit.jupiter.api.Test
import org.mockito.Mockito.*
import java.time.*
import java.util.UUID

class CloudProviderControlsTest {
    private val config=mock(PluginConfigurationService::class.java)
    private val providers=CloudProviders(config)
    @Test fun `provider controls are independent and preserve existing installations by default`() {
        `when`(config.current("cloud-costs")).thenReturn(emptyMap())
        assertThat(providers.all().values).containsOnly(true)
        `when`(config.current("cloud-costs")).thenReturn(mapOf("awsEnabled" to "false","azureEnabled" to "true","gcpEnabled" to "false"))
        assertThat(providers.all()).containsEntry(CloudProvider.AWS,false).containsEntry(CloudProvider.AZURE,true).containsEntry(CloudProvider.GCP,false)
    }
    @Test fun `disabled providers reject direct connection and refresh calls before reading secrets or using network`() {
        `when`(config.current("cloud-costs")).thenReturn(mapOf("awsEnabled" to "false"))
        val store=mock(OperationsStore::class.java)
        val access=mock(OperationsAccess::class.java,RETURNS_DEEP_STUBS)
        val encryption=mock(SecretEncryption::class.java)
        val http=mock(IntegrationHttpClient::class.java)
        val service=CloudCosts(store,access,encryption,http,providers)
        val connection=CloudConnection(CloudProvider.AWS,"AWS","123456789012")
        val row=OpsRow(UUID.randomUUID(),UUID.randomUUID(),UUID.randomUUID(),"CONNECTION","{}",0,Instant.now())
        `when`(store.get("cloud-costs",row.id)).thenReturn(row)
        `when`(store.decode(row,CloudConnection::class.java)).thenReturn(connection)
        assertThatThrownBy {service.connect("admin@example.test",null,CloudConnectionInput(row.projectId!!,connection))}.isInstanceOf(BadRequestException::class.java)
        assertThatThrownBy {service.refresh("admin@example.test",row.id,CloudRefresh(LocalDate.of(2026,1,1),LocalDate.of(2026,1,2)))}.isInstanceOf(BadRequestException::class.java)
        verify(store,never()).secret("cloud-costs",row.id)
        verifyNoInteractions(encryption,http)
    }
}
