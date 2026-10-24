package com.starter.access

/** `payload` is the storage result JSON. All values, including rules, are bound parameters. */
object AccessSql {
    val predicate = """
        NOT EXISTS (SELECT 1 FROM jsonb_array_elements(acl.rules->'blocks') b WHERE b->>'folderId' = payload->>'folderId'
            AND ((b->>'type' = 'FILE' AND b->>'path' = payload->>'storagePath') OR
                 (b->>'type' = 'FOLDER' AND left(coalesce(payload->>'storagePath', ''), length(b->>'path')) = b->>'path')))
        AND ((acl.rules->>'unrestricted')::boolean OR EXISTS (
            SELECT 1 FROM jsonb_array_elements(acl.rules->'grants') g WHERE g->>'folderId' = payload->>'folderId'
            AND ((g->>'type' = 'FILE' AND g->>'path' = payload->>'storagePath') OR
                 (g->>'type' = 'FOLDER' AND left(coalesce(payload->>'storagePath', ''), length(g->>'path')) = g->>'path') OR
                 payload->>'entityType' = 'folder')))
    """.trimIndent()
    const val unrestricted = "{\"unrestricted\":true,\"grants\":[],\"blocks\":[]}"
}
