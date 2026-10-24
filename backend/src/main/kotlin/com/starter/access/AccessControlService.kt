package com.starter.access

import com.fasterxml.jackson.databind.ObjectMapper
import com.starter.exception.BadRequestException
import com.starter.exception.ConflictException
import com.starter.exception.ForbiddenException
import com.starter.model.postgres.User
import com.starter.repository.postgres.ItemRepository
import com.starter.repository.postgres.UserRepository
import com.starter.service.storage.normalizeStorageFolder
import com.starter.service.storage.normalizeStorageObject
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.util.UUID

@Service
class AccessControlService(private val policies: AccessPolicyRepository, private val users: UserRepository,
    private val items: ItemRepository, private val mapper: ObjectMapper, val catalogue: AccessCatalogue,
    private val reader: AccessPolicyReader? = null) {
    fun actor(): User? {
        val auth = SecurityContextHolder.getContext().authentication ?: return null // Background indexing has no HTTP principal.
        if (!auth.isAuthenticated || auth.name == "anonymousUser") return null
        return (if (reader == null) users.findByEmail(auth.name).orElse(null)?.takeIf { it.active } else reader.user(auth.name)) ?: throw ForbiddenException("Account unavailable")
    }
    fun policy(): AccessPolicy = reader?.policy() ?: policies.findById(1).orElse(null)?.let { mapper.readValue(it.policyJson, AccessPolicy::class.java) } ?: AccessPolicy()
    fun features(actor: User, policy: AccessPolicy = policy()): Set<String> {
        if (!actor.active) return emptySet()
        if (actor.role == Roles.SUPER_ADMIN) return catalogue.defaults(actor.role)
        val cap = catalogue.maximum(actor.role)
        val configured = policy.roleFeatures[actor.role] ?: if (actor.role == Roles.BUSINESS_MANAGER) policy.roleFeatures["USER"] else null
        val role = configured?.intersect(cap) ?: catalogue.defaults(actor.role)
        return policy.members[actor.id]?.features?.intersect(role) ?: role
    }
    fun allows(feature: String): Boolean = actor()?.let { feature in features(it) } ?: true
    fun requireFeature(feature: String) { if (!allows(feature)) throw ForbiddenException("This feature is not available to your account") }
    fun me(): MyAccess = actor()?.let { MyAccess(features(it), it.role, catalogue.features) } ?: throw ForbiddenException("Authentication required")
    fun roots(): List<AccessRoot> { requireSuper(); return items.findAllByOrderByCreatedAtDesc().map { AccessRoot(it.id, it.title, it.storageConnection?.nickname, it.accessible && it.storageConnection?.enabled != false) } }
    fun externalShareAllowed(folder: UUID, path: String): Boolean = expanded(policy().protectedPaths.map { it.rule }).none { it.covers(folder, normalizeStorageObject(path)) }
    private fun expanded(rules: List<StorageRule>) = if (rules.isEmpty() || reader == null) rules else StorageRuleAliases.expand(rules, reader.locations())
    fun storage(actor: User? = actor(), policy: AccessPolicy = policy()): StorageAccess {
        if (actor == null || actor.role == Roles.SUPER_ADMIN) return StorageAccess()
        if (!actor.active || "storage" !in features(actor, policy)) return StorageAccess(false)
        val member = policy.members[actor.id] ?: MemberAccess()
        return StorageAccess(!member.restrictStorage && actor.role != "EXTERNAL", member.grants,
            expanded(member.blocks + policy.protectedPaths.filter { !it.externalOnly || actor.role == "EXTERNAL" }.map { it.rule }))
    }
    fun requireStorage(folder: UUID, path: String = "", directory: Boolean = true) {
        val value = if (directory) normalizeStorageFolder(path) else normalizeStorageObject(path)
        if (!storage().allows(folder, value, directory)) throw ForbiddenException("This file or folder is not available to your account")
    }
    fun sqlSnapshot(): String = mapper.writeValueAsString(storage())
    fun canManage(actor: User, target: User): Boolean = actor.role == Roles.SUPER_ADMIN ||
        !Roles.admin(target.role) && features(actor).containsAll(features(target)) && storage(actor).contains(storage(target))

    /** Delegated user managers cannot mint an unrestricted account to escape their own restrictions. */
    @Transactional
    fun inheritForCreated(actor: User, target: User) {
        if (actor.role == Roles.SUPER_ADMIN) return
        val existing = policies.findById(1).orElse(null)
        val policy = existing?.let { mapper.readValue(it.policyJson, AccessPolicy::class.java) } ?: AccessPolicy()
        val scope = storage(actor, policy)
        val inherited = MemberAccess(features(actor, policy).intersect(catalogue.defaults(target.role)),
            !scope.unrestricted || target.role == "EXTERNAL", if (target.role == "EXTERNAL") emptyList() else scope.grants, scope.blocks)
        val row = existing ?: AccessPolicyRow()
        row.policyJson = mapper.writeValueAsString(policy.copy(members = policy.members + (target.id to inherited)))
        try { policies.saveAndFlush(row) } catch (_: org.springframework.dao.DataIntegrityViolationException) {
            throw ConflictException("Access controls changed. Retry creating the account.")
        }
    }
    fun view(): AccessPolicyView {
        requireSuper()
        val row = policies.findById(1).orElse(null)
        val current = row?.let { mapper.readValue(it.policyJson, AccessPolicy::class.java) } ?: AccessPolicy()
        return AccessPolicyView(row?.revision ?: -1, current.copy(roleFeatures = listOf("ADMIN", Roles.BUSINESS_MANAGER, "USER", "EXTERNAL").associateWith {
            (current.roleFeatures[it] ?: if (it == Roles.BUSINESS_MANAGER) current.roleFeatures["USER"] else null)?.intersect(catalogue.maximum(it)) ?: catalogue.defaults(it)
        }), catalogue.features)
    }
    @Transactional
    fun save(request: AccessPolicyRequest): AccessPolicyView {
        requireSuper()
        val existing = policies.findById(1).orElse(null)
        if ((existing?.revision ?: -1) != request.revision) throw ConflictException("Access controls changed. Reload before saving.")
        val row = existing ?: AccessPolicyRow()
        val value = request.policy
        val known = catalogue.features.map { it.id }.toSet()
        if (value.roleFeatures.keys.any { it !in setOf("ADMIN", Roles.BUSINESS_MANAGER, "USER", "EXTERNAL") } || value.roleFeatures.any { (role, values) ->
            !catalogue.maximum(role).containsAll(values) }) throw BadRequestException("Invalid role features")
        if (value.members.size > 1000 || value.protectedPaths.size > 500) throw BadRequestException("Too many access rules")
        val allUsers = users.findAll().associateBy { it.id }
        val folders = items.findAll().map { it.id }.toSet()
        fun rule(rule: StorageRule): StorageRule {
            if (rule.folderId !in folders || rule.type !in setOf("FILE", "FOLDER") || rule.path.length > 2048) throw BadRequestException("Choose an existing storage root and a valid file or directory path")
            return rule.copy(path = if (rule.type == "FOLDER") normalizeStorageFolder(rule.path) else normalizeStorageObject(rule.path))
        }
        val members = value.members.mapValues { (id, entry) ->
            val target = allUsers[id] ?: throw BadRequestException("Unknown member")
            if (target.role == Roles.SUPER_ADMIN || entry.features?.any { it !in known } == true || entry.grants.size + entry.blocks.size > 500) throw BadRequestException("Invalid member policy; super admins always retain access")
            entry.copy(restrictStorage = entry.restrictStorage || target.role == "EXTERNAL", grants = entry.grants.map(::rule).distinct(), blocks = entry.blocks.map(::rule).distinct())
        }
        val normalized = value.copy(members = members, protectedPaths = value.protectedPaths.map { it.copy(rule = rule(it.rule)) }.distinct())
        row.policyJson = mapper.writeValueAsString(normalized)
        try { policies.saveAndFlush(row) } catch (_: org.springframework.dao.DataIntegrityViolationException) {
            throw ConflictException("Access controls changed. Reload before saving.")
        }
        return view()
    }
    private fun requireSuper() { if (actor()?.role != Roles.SUPER_ADMIN) throw ForbiddenException("Only a super admin can change access controls") }
}
