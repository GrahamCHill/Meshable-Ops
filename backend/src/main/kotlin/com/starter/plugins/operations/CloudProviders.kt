package com.starter.plugins.operations

import com.starter.plugins.PluginConfigurationService
import org.springframework.stereotype.Service

@Service
class CloudProviders(private val configuration:PluginConfigurationService) {
    fun enabled(provider:CloudProvider) = all().getValue(provider)
    fun all():Map<CloudProvider,Boolean> {
        val values=configuration.current("cloud-costs")
        // Existing installations keep their providers until an administrator disables one.
        return CloudProvider.entries.associateWith {values[it.name.lowercase()+"Enabled"]!="false"}
    }
}
