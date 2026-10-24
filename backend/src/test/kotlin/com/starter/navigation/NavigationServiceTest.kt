package com.starter.navigation

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.starter.exception.*
import com.starter.model.postgres.NavigationSettings
import com.starter.model.postgres.User
import com.starter.repository.postgres.NavigationSettingsRepository
import org.assertj.core.api.Assertions.*
import org.junit.jupiter.api.Test
import org.mockito.Mockito.*
import org.springframework.jdbc.core.JdbcTemplate
import java.util.Optional

class NavigationServiceTest {
    private val rows=mock(NavigationSettingsRepository::class.java)
    private val catalogue=mock(NavigationCatalogue::class.java)
    private val service=NavigationService(rows,catalogue,jacksonObjectMapper(),mock(JdbcTemplate::class.java))
    private val calendar=NavigationItem("plugin:calendar","Calendar","/plugins/calendar",children=listOf(NavigationChild("events","Events","/plugins/calendar/events")))
    init {
        `when`(rows.findById(1)).thenReturn(Optional.empty())
        `when`(catalogue.all()).thenReturn(listOf(NavigationItem("home","Home","/"),calendar))
        `when`(rows.saveAndFlush(org.mockito.ArgumentMatchers.any(NavigationSettings::class.java))).thenAnswer {it.arguments[0]}
    }
    @Test fun `order and submenu updates accept only declared destinations`() {
        assertThat(service.save(NavigationPreferences(listOf(calendar.id,"home"),mapOf(calendar.id to listOf("events")))).preferences.order).containsExactly(calendar.id,"home")
        listOf(NavigationPreferences(listOf("home","home")),NavigationPreferences(listOf("https://untrusted.test")),NavigationPreferences(submenus=mapOf(calendar.id to listOf("private")))).forEach {
            assertThatThrownBy {service.save(it)}.isInstanceOf(BadRequestException::class.java)
        }
    }
    @Test fun `removed plugin positions can be retained but stale saves conflict`() {
        `when`(rows.findById(1)).thenReturn(Optional.of(NavigationSettings(orderJson="[\"plugin:removed\",\"home\"]",revision=4)))
        assertThatThrownBy {service.save(NavigationPreferences(listOf("home")))}.isInstanceOf(ConflictException::class.java)
        assertThat(service.save(NavigationPreferences(listOf("plugin:removed","home"),revision=4)).preferences.order).contains("plugin:removed")
    }
    @Test fun `member output strips forbidden menu and child identifiers`() {
        val actor=User()
        `when`(rows.findById(1)).thenReturn(Optional.of(NavigationSettings(orderJson="[\"plugin:calendar\",\"home\"]",submenusJson="{\"plugin:calendar\":[\"private\",\"events\"]}")))
        `when`(catalogue.visible(actor)).thenReturn(listOf(NavigationItem("home","Home","/")))
        val member=service.member(actor)
        assertThat(member.preferences.order).containsExactly("home");assertThat(member.preferences.submenus).isEmpty()
    }
    @Test fun `admin responses hide disabled items while preserving saved positions through edits`() {
        val home=NavigationItem("home","Home","/")
        `when`(catalogue.configurable()).thenReturn(listOf(home))
        `when`(rows.findById(1)).thenReturn(Optional.of(NavigationSettings(orderJson="[\"plugin:calendar\",\"home\"]",revision=4)))
        assertThat(service.admin().items).containsExactly(home)
        assertThat(service.admin().preferences.order).containsExactly(calendar.id,"home")
        val saved=service.save(NavigationPreferences(listOf(calendar.id,"home"),revision=4))
        assertThat(saved.items).containsExactly(home)
        assertThat(saved.preferences.order).containsExactly(calendar.id,"home")
    }
}
