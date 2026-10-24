package com.starter.access

import jakarta.persistence.*
import org.springframework.data.jpa.repository.JpaRepository
import java.util.UUID

@Entity @Table(name = "access_policy")
class AccessPolicyRow(@Id val id: Int = 1, @Column(columnDefinition = "text", nullable = false) var policyJson: String = "{}",
    @Version var revision: Long = 0)
interface AccessPolicyRepository : JpaRepository<AccessPolicyRow, Int>

@Entity @Table(name = "account_role_migrations")
class AccountRoleMigration(@Id val id: Int = 1)
interface AccountRoleMigrationRepository : JpaRepository<AccountRoleMigration, Int>

data class StorageRule(val folderId: UUID, val path: String = "", val type: String = "FOLDER") {
    fun covers(folder: UUID, value: String) = folderId == folder && if (type == "FOLDER") value.startsWith(path) else value == path
}
data class ProtectedPath(val rule: StorageRule, val externalOnly: Boolean = true)
data class MemberAccess(val features: Set<String>? = null, val restrictStorage: Boolean = false,
    val grants: List<StorageRule> = emptyList(), val blocks: List<StorageRule> = emptyList())
data class AccessPolicy(val roleFeatures: Map<String, Set<String>> = emptyMap(), val members: Map<UUID, MemberAccess> = emptyMap(),
    val protectedPaths: List<ProtectedPath> = emptyList())
data class AccessPolicyRequest(val revision: Long, val policy: AccessPolicy)
data class AccessFeature(val id: String, val label: String, val path: String, val admin: Boolean = false)
data class AccessPolicyView(val revision: Long, val policy: AccessPolicy, val features: List<AccessFeature>)
data class MyAccess(val features: Set<String>, val role: String, val views: List<AccessFeature>)
data class AccessRoot(val id: UUID, val title: String, val connection: String?, val accessible: Boolean)

/** An immutable snapshot is also bound into SQL so inaccessible excerpts never reach retrieval LIMIT or an AI provider. */
data class StorageAccess(val unrestricted: Boolean = true, val grants: List<StorageRule> = emptyList(), val blocks: List<StorageRule> = emptyList()) {
    fun contains(other: StorageAccess): Boolean {
        if (!unrestricted && other.unrestricted) return false
        if (!unrestricted && other.grants.any { target -> grants.none { grant -> grant.covers(target.folderId, target.path) && (target.type == "FILE" || grant.type == "FOLDER") } }) return false
        fun overlaps(a: StorageRule, b: StorageRule) = a.covers(b.folderId, b.path) || b.covers(a.folderId, a.path)
        return blocks.none { block -> (other.unrestricted || other.grants.any { overlaps(it, block) }) && other.blocks.none { it.covers(block.folderId, block.path) && (block.type == "FILE" || it.type == "FOLDER") } }
    }
    fun allows(folder: UUID, path: String, directory: Boolean = false): Boolean {
        if (blocks.any { it.covers(folder, path) }) return false
        return unrestricted || grants.any { it.covers(folder, path) || directory && it.folderId == folder && it.path.startsWith(path) }
    }
}
