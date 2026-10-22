package com.starter.config

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.starter.model.postgres.User
import com.starter.repository.postgres.DashboardEmbedConfigRepository
import com.starter.repository.postgres.UserRepository
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.mockito.ArgumentCaptor
import org.mockito.Mockito.mock
import org.mockito.Mockito.any
import org.mockito.Mockito.`when`
import org.mockito.Mockito.verify
import org.mockito.Mockito.verifyNoInteractions
import org.springframework.boot.DefaultApplicationArguments
import org.springframework.mock.env.MockEnvironment
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder

class AdminSeedConfigTest {
    private val users = mock(UserRepository::class.java)
    private val dashboards = mock(DashboardEmbedConfigRepository::class.java)
    private val passwordEncoder = BCryptPasswordEncoder(4)

    @Test
    fun `explicit bootstrap users are normalized and passwords are encoded in production`() {
        val settings = AdminSeedProperties().apply {
            bootstrapUsersJson = """[{
                "email": " OWNER@Example.com ", "username": " owner ",
                "name": " Owner ", "contactNumber": " 123 ",
                "password": "test-password", "role": " admin "
            }]"""
        }
        val environment = MockEnvironment().apply { setActiveProfiles("prod") }
        `when`(users.save(any(User::class.java))).thenAnswer { it.arguments[0] }

        AdminSeedConfig().seedUserRunner(
            environment, jacksonObjectMapper(), users, dashboards, passwordEncoder, settings,
        ).run(DefaultApplicationArguments())

        val saved = ArgumentCaptor.forClass(User::class.java)
        verify(users).save(saved.capture())
        assertThat(saved.value.email).isEqualTo("owner@example.com")
        assertThat(saved.value.username).isEqualTo("owner")
        assertThat(saved.value.name).isEqualTo("Owner")
        assertThat(saved.value.contactNumber).isEqualTo("123")
        assertThat(saved.value.role).isEqualTo("ADMIN")
        assertThat(passwordEncoder.matches("test-password", saved.value.password)).isTrue()
        assertThat(saved.value.canUploadDocuments).isFalse()
    }

    @Test
    fun `production without explicit bootstrap users never creates default accounts`() {
        val environment = MockEnvironment().apply { setActiveProfiles("prod") }

        AdminSeedConfig().seedUserRunner(
            environment, jacksonObjectMapper(), users, dashboards, passwordEncoder, AdminSeedProperties(),
        ).run(DefaultApplicationArguments())

        verifyNoInteractions(users)
    }
}
