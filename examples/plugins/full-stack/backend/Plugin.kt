// SPDX-License-Identifier: LicenseRef-Meshable-Ops-Community-1.0
import com.meshable.plugins.PluginWorker

fun main() = PluginWorker.serve { _, host ->
    val records = host.list("plugin_zip_full_stack_notes")
    mapOf("runtime" to "Kotlin", "visibleNotes" to records.size(), "message" to "Only records allowed by your role and ownership are counted.")
}
