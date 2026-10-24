package com.starter.plugins

import org.springframework.boot.ApplicationArguments
import org.springframework.boot.ApplicationRunner
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional

/** Preserve existing installations without auto-enabling optional components on a clean database. */
@Component
class BundledModuleMigration(private val jdbc: JdbcTemplate) : ApplicationRunner {
    @Transactional
    override fun run(args: ApplicationArguments) {
        jdbc.update("""INSERT INTO plugin_settings (plugin_id, enabled, allowed_roles_csv, search_enabled, table_access_json, removed)
            SELECT 'documents', EXISTS(SELECT 1 FROM document_templates) OR EXISTS(SELECT 1 FROM generated_documents), 'ADMIN,USER', false, '{}', false
            ON CONFLICT (plugin_id) DO NOTHING""")
        jdbc.update("""INSERT INTO plugin_settings (plugin_id, enabled, allowed_roles_csv, search_enabled, table_access_json, removed)
            SELECT 'workflows', EXISTS(SELECT 1 FROM workflow_definitions) OR EXISTS(SELECT 1 FROM workflow_runs), 'ADMIN,USER', false, '{}', false
            ON CONFLICT (plugin_id) DO NOTHING""")
    }
}
