package com.starter.access

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.starter.exception.BadRequestException
import com.starter.exception.ForbiddenException
import com.starter.model.postgres.Item
import com.starter.model.postgres.User
import com.starter.repository.postgres.ItemRepository
import com.starter.repository.postgres.UserRepository
import org.assertj.core.api.Assertions.*
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import org.mockito.Mockito.*
import org.springframework.mock.web.MockHttpServletRequest
import org.springframework.mock.web.MockHttpServletResponse
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.core.context.SecurityContextHolder
import java.util.Optional
import java.util.UUID

class AccessControlTest {
    private val mapper = jacksonObjectMapper()
    private val policies = mock(AccessPolicyRepository::class.java)
    private val users = mock(UserRepository::class.java)
    private val items = mock(ItemRepository::class.java)
    private val catalogue = AccessCatalogue(emptyList())
    private val access = AccessControlService(policies, users, items, mapper, catalogue)
    private val external = User(email = "client@example.test", role = "EXTERNAL")
    private val folder = UUID.randomUUID()
    private val grant = StorageRule(folder, "client/", "FOLDER")
    private fun policy(value: AccessPolicy) { `when`(policies.findById(1)).thenReturn(Optional.of(AccessPolicyRow(policyJson = mapper.writeValueAsString(value)))) }
    private fun signIn(user: User) {
        `when`(users.findByEmail(user.email)).thenReturn(Optional.of(user))
        SecurityContextHolder.getContext().authentication = UsernamePasswordAuthenticationToken(user.email, null, emptyList())
    }
    @AfterEach fun clear() { SecurityContextHolder.clearContext() }

    @Test fun `external members start with storage view but no files`() {
        policy(AccessPolicy())
        assertThat(access.features(external)).containsExactly("storage")
        assertThat(access.storage(external).allows(folder, "anything.pdf")).isFalse()
    }
    @Test fun `grants respect directory boundaries and case while file exclusions win`() {
        val acl = StorageAccess(false, listOf(grant), listOf(StorageRule(folder, "client/private.pdf", "FILE")))
        assertThat(acl.allows(folder, "client/public.pdf")).isTrue()
        assertThat(acl.allows(folder, "", true)).isTrue()
        assertThat(acl.allows(folder, "client/private.pdf")).isFalse()
        assertThat(acl.allows(folder, "client-other/public.pdf")).isFalse()
        assertThat(acl.allows(folder, "Client/public.pdf")).isFalse()
        assertThat(acl.allows(UUID.randomUUID(), "client/public.pdf")).isFalse()
    }
    @Test fun `file only grant permits ancestors without granting sibling files`() {
        val acl = StorageAccess(false, listOf(StorageRule(folder, "client/one.pdf", "FILE")))
        assertThat(acl.allows(folder, "client/", true)).isTrue()
        assertThat(acl.allows(folder, "client/one.pdf")).isTrue()
        assertThat(acl.allows(folder, "client/two.pdf")).isFalse()
    }
    @Test fun `delegated managers cannot manage broader scopes or remove their own exclusions through another member`() {
        val blocked = StorageRule(folder, "client/private/")
        val scope = StorageAccess(false, listOf(grant), listOf(blocked))
        assertThat(scope.contains(StorageAccess())).isFalse()
        assertThat(scope.contains(StorageAccess(false, listOf(grant)))).isFalse()
        assertThat(scope.contains(StorageAccess(false, listOf(StorageRule(folder, "client/public/"))))).isTrue()
        assertThat(scope.contains(StorageAccess(false, listOf(grant), listOf(blocked)))).isTrue()
    }
    @Test fun `exclusions follow objects through duplicate bucket mappings`() {
        val alias = UUID.randomUUID()
        val nested = UUID.randomUUID()
        val otherService = UUID.randomUUID()
        val locations = listOf(StorageLocation(folder, "S3", "docs", "http://minio", "clients/"),
            StorageLocation(alias, "S3", "docs", "http://minio", ""),
            StorageLocation(nested, "S3", "docs", "http://minio", "clients/private/"),
            StorageLocation(otherService, "S3", "docs", "http://another-service", ""))
        val rules = StorageRuleAliases.expand(listOf(StorageRule(folder, "private/")), locations)
        assertThat(rules).contains(StorageRule(alias, "clients/private/"), StorageRule(nested, ""))
        assertThat(rules.any { it.folderId == otherService }).isFalse()
    }
    @Test fun `business protections exclude externals and can also exclude internal administrators`() {
        val sensitive = StorageRule(folder, "client/internal/", "FOLDER")
        val protected = AccessPolicy(members = mapOf(external.id to MemberAccess(grants = listOf(grant))), protectedPaths = listOf(ProtectedPath(sensitive)))
        policy(protected)
        assertThat(access.storage(external).allows(folder, "client/internal/cost.pdf")).isFalse()
        assertThat(access.storage(User(role = "ADMIN")).allows(folder, "client/internal/cost.pdf")).isTrue()
        assertThat(access.externalShareAllowed(folder, "client/internal/cost.pdf")).isFalse()
        policy(protected.copy(protectedPaths = listOf(ProtectedPath(sensitive, false))))
        assertThat(access.storage(User(role = "ADMIN")).allows(folder, "client/internal/cost.pdf")).isFalse()
        assertThat(access.storage(User(role = "SUPER_ADMIN")).allows(folder, "client/internal/cost.pdf")).isTrue()
    }
    @Test fun `role caps and member view limits are intersected and never restrict super admins`() {
        val member = User(role = "ADMIN")
        val settings = AccessPolicy(roleFeatures = mapOf("ADMIN" to setOf("storage")), members = mapOf(member.id to MemberAccess(features = setOf("storage", "admin.users"))))
        assertThat(access.features(member, settings)).containsExactly("storage")
        assertThat(access.features(User(role = "SUPER_ADMIN"), settings)).contains("admin.access", "admin.users")
        assertThat(access.features(User(role = "USER"), AccessPolicy(roleFeatures = mapOf("USER" to setOf("admin.users"))))).isEmpty()
    }
    @Test fun `restricted member with disabled storage cannot retrieve known file paths`() {
        policy(AccessPolicy(members = mapOf(external.id to MemberAccess(features = emptySet(), grants = listOf(grant)))))
        signIn(external)
        assertThatThrownBy { access.requireStorage(folder, "client/public.pdf", false) }.isInstanceOf(ForbiddenException::class.java)
    }
    @Test fun `normal users and delegated admins cannot update access policies`() {
        listOf("USER", "ADMIN", "EXTERNAL").forEach { signIn(User(email = "$it@example.test", role = it)); assertThatThrownBy { access.save(AccessPolicyRequest(0, AccessPolicy())) }.isInstanceOf(ForbiddenException::class.java) }
    }
    @Test fun `first policy save advances the absent revision and rejects an outdated initial form`() {
        signIn(User(email = "super@example.test", role = "SUPER_ADMIN"))
        val row = AccessPolicyRow()
        `when`(policies.findById(1)).thenReturn(Optional.empty(), Optional.of(row))
        `when`(policies.saveAndFlush(any(AccessPolicyRow::class.java))).thenReturn(row)
        assertThat(access.save(AccessPolicyRequest(-1, AccessPolicy())).revision).isEqualTo(0)
        assertThatThrownBy { access.save(AccessPolicyRequest(-1, AccessPolicy())) }.isInstanceOf(com.starter.exception.ConflictException::class.java)
    }
    @Test fun `policy saves reject traversal and nonexistent roots before persistence`() {
        signIn(User(email = "super@example.test", role = "SUPER_ADMIN"))
        `when`(policies.findById(1)).thenReturn(Optional.empty())
        `when`(users.findAll()).thenReturn(listOf(external))
        `when`(items.findAll()).thenReturn(listOf(Item(id = folder)))
        listOf("client/../private/", "client/./").forEach { path ->
            assertThatThrownBy { access.save(AccessPolicyRequest(-1, AccessPolicy(members = mapOf(external.id to MemberAccess(grants = listOf(grant.copy(path = path))))))) }.isInstanceOf(BadRequestException::class.java)
        }
        val unknownRoot = MemberAccess(grants = listOf(grant.copy(folderId = UUID.randomUUID())))
        assertThatThrownBy { access.save(AccessPolicyRequest(-1, AccessPolicy(members = mapOf(external.id to unknownRoot)))) }.isInstanceOf(BadRequestException::class.java)
        verify(policies, never()).saveAndFlush(any())
    }
    @Test fun `API gates block direct endpoints and fail closed on unknown endpoints`() {
        policy(AccessPolicy())
        signIn(external)
        val guard = AccessRequestGuard(access)
        listOf("/api/admin/users", "/api/events", "/api/workflows", "/api/documents", "/api/search", "/api/new-unmapped-feature").forEach { path ->
            assertThatThrownBy { guard.preHandle(MockHttpServletRequest("GET", path), MockHttpServletResponse(), Any()) }.isInstanceOf(ForbiddenException::class.java)
        }
        assertThat(guard.preHandle(MockHttpServletRequest("GET", "/api/items"), MockHttpServletResponse(), Any())).isTrue()
        assertThatThrownBy { guard.preHandle(MockHttpServletRequest("POST", "/api/items/$folder/upload"), MockHttpServletResponse(), Any()) }.isInstanceOf(ForbiddenException::class.java)
    }
    @Test fun `super admin retains administrator authority for existing controllers`() {
        val details = com.starter.security.SessionUserDetails(User(email = "super@example.test", password = "encoded", role = "SUPER_ADMIN"), false)
        assertThat(details.authorities.map { it.authority }).contains("ROLE_SUPER_ADMIN", "ROLE_ADMIN")
        assertThat(Roles.plugin("SUPER_ADMIN")).isEqualTo("ADMIN")
    }
    @Test fun `event log is an admin feature and existing member event grants cannot expose it`() {
        val legacy=AccessPolicy(roleFeatures=mapOf("USER" to setOf("events")))
        assertThat(access.features(User(role="USER"),legacy)).doesNotContain("events")
        assertThat(access.features(User(role="EXTERNAL"))).doesNotContain("events")
        assertThat(access.features(User(role="ADMIN"))).contains("events")
        assertThat(access.features(User(role="ADMIN"),AccessPolicy(roleFeatures=mapOf("ADMIN" to setOf("storage"))))).doesNotContain("events")
        assertThat(catalogue.features.single {it.id=="events"}.admin).isTrue()
        assertThat(catalogue.features.single {it.id=="events"}.path).isEqualTo("/admin/events")
        assertThat(AccessRequestGuard.feature("/api/admin/events")).isEqualTo("events")
        assertThat(AccessRequestGuard.feature("/api/events")).isEqualTo("events")
        policy(legacy);signIn(User(email="super@example.test",role="SUPER_ADMIN"))
        assertThat(access.view().policy.roleFeatures.getValue("USER")).doesNotContain("events")
    }
}
