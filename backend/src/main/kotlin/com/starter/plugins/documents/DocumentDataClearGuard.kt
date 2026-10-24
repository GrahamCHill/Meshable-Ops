package com.starter.plugins.documents

import com.starter.exception.BadRequestException
import com.starter.plugins.PluginDataClearGuard
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.stereotype.Component

@Component
class DocumentDataClearGuard(private val jdbc: JdbcTemplate) : PluginDataClearGuard {
    override val pluginId = "documents"
    override fun check() {
        // Coordinate the clear with saves/starts, including disabled or removed workflow modules.
        jdbc.execute("LOCK TABLE workflow_definitions, workflow_runs IN SHARE ROW EXCLUSIVE MODE")
        val referenced = jdbc.queryForObject("""SELECT EXISTS(SELECT 1 FROM workflow_definitions WHERE document_ids_json::jsonb <> '[]'::jsonb)
            OR EXISTS(SELECT 1 FROM workflow_runs WHERE jsonb_path_exists(graph_json::jsonb, '$.nodes[*].documentId ? (@ != null)'))""", Boolean::class.java) == true
        if (referenced) throw BadRequestException("Workflow designs or saved runs still reference generated PDFs. Remove those references or explicitly clear workflow data before clearing Documents.")
    }
}
