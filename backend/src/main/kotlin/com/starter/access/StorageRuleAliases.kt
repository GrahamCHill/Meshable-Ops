package com.starter.access

import java.util.UUID

data class StorageLocation(val id: UUID, val provider: String, val bucket: String, val endpoint: String, val prefix: String)

/** An exclusion follows the physical object if the same bucket is mapped through another connection/root. */
object StorageRuleAliases {
    fun expand(rules: List<StorageRule>, locations: List<StorageLocation>): List<StorageRule> = rules.flatMap { rule ->
        val source = locations.find { it.id == rule.folderId }
        if (source == null) listOf(rule) else {
            val absolute = source.prefix + rule.path
            listOf(rule) + locations.filter { it.provider == source.provider && it.bucket == source.bucket && it.endpoint == source.endpoint }.mapNotNull { target ->
                when {
                    absolute.startsWith(target.prefix) -> rule.copy(folderId = target.id, path = absolute.removePrefix(target.prefix))
                    rule.type == "FOLDER" && target.prefix.startsWith(absolute) -> rule.copy(folderId = target.id, path = "")
                    else -> null
                }
            }
        }
    }.distinct()
}
