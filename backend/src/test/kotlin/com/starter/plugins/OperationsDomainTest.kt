package com.starter.plugins

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.starter.plugins.operations.*
import com.starter.exception.BadRequestException
import org.assertj.core.api.Assertions.*
import org.junit.jupiter.api.Test
import java.math.BigDecimal
import java.time.*
import java.util.UUID

class OperationsDomainTest {
    @Test fun `new cloud connections can have no stored credentials`() {
        val store=OperationsStore(org.mockito.Mockito.mock(org.springframework.jdbc.core.JdbcTemplate::class.java),jacksonObjectMapper())
        assertThat(store.secret("cloud-costs",UUID.randomUUID())).isNull()
    }
    private fun line(price:String,gross:Boolean=false,qty:String="1")=BillingLine("x","Domain renewal",BigDecimal(qty),BigDecimal(price),gross)
    @Test fun `tax inclusive stored prices produce net invoice lines and separate VAT`() {
        val r=BillingMath.calculate(listOf(line("115",true)),"USD",true,BigDecimal("15"))
        assertThat(r.net).isEqualByComparingTo("100");assertThat(r.tax).isEqualByComparingTo("15");assertThat(r.total).isEqualByComparingTo("115")
        assertThat(r.lines.single().netUnitPrice).isEqualByComparingTo("100")
    }
    @Test fun `exclusive prices and quantities use line rounding and currency precision`() {
        val r=BillingMath.calculate(listOf(line("400",false,"1.5"),line("1200",false,"2")),"ZAR",true,BigDecimal("15"))
        assertThat(r.net).isEqualByComparingTo("3000");assertThat(r.tax).isEqualByComparingTo("450");assertThat(r.total).isEqualByComparingTo("3450")
        assertThat(BillingMath.calculate(listOf(line("100.5")),"JPY",false,BigDecimal.ZERO).total).isEqualByComparingTo("101")
    }
    @Test fun `unregistered billing never invents VAT or discounts gross prices`() {
        val r=BillingMath.calculate(listOf(line("115",true)),"USD",false,BigDecimal("15"))
        assertThat(r.net).isEqualByComparingTo("115");assertThat(r.tax).isEqualByComparingTo("0")
    }
    @Test fun `invalid money and tax values are rejected`() {
        assertThatThrownBy {BillingMath.calculate(listOf(line("1")),"USD",true,BigDecimal("101"))}.isInstanceOf(BadRequestException::class.java)
        assertThatThrownBy {BillingMath.calculate(listOf(line("-2")),"USD",false,BigDecimal.ZERO)}.isInstanceOf(BadRequestException::class.java)
        assertThatThrownBy {BillingMath.calculate(listOf(line("2",qty="0")),"USD",false,BigDecimal.ZERO)}.isInstanceOf(BadRequestException::class.java)
    }
    @Test fun `mixed inclusive and exclusive items reconcile gross equals net plus tax`() {
        val r=BillingMath.calculate(listOf(line("99.99",true,"3"),line("0.07",false,"7")),"USD",true,BigDecimal("15"))
        assertThat(r.total).isEqualByComparingTo(r.net+r.tax)
        assertThat(r.lines.first().total).isEqualByComparingTo("299.97")
    }
    @Test fun `ActivityWatch keeps durations but discards window URLs titles and AFK buckets`() {
        val json=jacksonObjectMapper().readTree("""{"buckets":{"aw-watcher-window":{"events":[{"id":1,"timestamp":"2026-01-01T10:00:00+02:00","duration":120,"data":{"app":"Editor","title":"private client secret","url":"https://private.example"}}]},"aw-watcher-afk":{"events":[{"timestamp":"2026-01-01T10:00:00Z","duration":120}]}}}""")
        val r=ActivityWatchService.parse(json)
        assertThat(r).hasSize(1);assertThat(r.single().description).isEqualTo("Editor");assertThat(r.single().start).isEqualTo(Instant.parse("2026-01-01T08:00:00Z"))
        assertThat(jacksonObjectMapper().findAndRegisterModules().writeValueAsString(r)).doesNotContain("private client secret","private.example")
    }
    @Test fun `ActivityWatch requires timezone and rejects negative or excessive duration`() {
        listOf("-10","90000").forEach {seconds->assertThatThrownBy {ActivityWatchService.parse(jacksonObjectMapper().readTree("""{"id":"window","events":[{"timestamp":"2026-01-01T10:00:00Z","duration":$seconds}]}"""))}.isInstanceOf(BadRequestException::class.java)}
        assertThatThrownBy {ActivityWatchService.parse(jacksonObjectMapper().readTree("""{"events":[{"timestamp":"2026-01-01T10:00:00","duration":1}]}"""))}.isInstanceOf(BadRequestException::class.java)
    }
    @Test fun `schedules remain disabled and handle DST and next month correctly`() {
        val s=BillingSchedule(UUID.randomUUID(),dayOfMonth=1,time="08:00",timeZone="Europe/London")
        assertThat(s.enabled).isFalse();assertThat(s.autoSend).isFalse()
        assertThat(BillingSchedules.next(s,Instant.parse("2026-03-15T12:00:00Z"))).isEqualTo(Instant.parse("2026-04-01T07:00:00Z"))
        assertThat(BillingSchedules.next(s,Instant.parse("2026-04-01T07:00:00Z"))).isEqualTo(Instant.parse("2026-05-01T07:00:00Z"))
    }
    @Test fun `billing roles use enabled state and bounded amounts`() {
        val rate=DeveloperRate("Developer",BigDecimal("450"))
        assertThat(rate.enabled).isTrue()
        BillingRates.validateAmount(rate.amount,"ZAR")
        assertThatThrownBy {BillingRates.validateAmount(BigDecimal("-1"),"ZAR")}.isInstanceOf(BadRequestException::class.java)
    }
    @Test fun `cloud scopes reject path and query injection`() {
        assertThatThrownBy {CloudCosts.validate(CloudConnection(CloudProvider.AWS,"AWS","123456789012/../"))}.isInstanceOf(BadRequestException::class.java)
        assertThatThrownBy {CloudCosts.validate(CloudConnection(CloudProvider.GCP,"GCP","my-project","my-project.billing.table`; SELECT 1","my-project"))}.isInstanceOf(BadRequestException::class.java)
    }
    @Test fun `operation plugins declare real dependencies and preserve optional composition`() {
        val plugins=listOf(TimesheetsPlugin(),ActivityWatchPlugin(),TimeCampPlugin(),CloudCostsPlugin(),BillingItemsPlugin(),BillingPlugin(),OnCallPlugin())
        plugins.forEach {PluginManifests.validateIdentity(it.manifest);PluginManifests.validate(it.manifest)}
        assertThat(ActivityWatchPlugin().manifest.requires).contains("timesheets")
        assertThat(BillingPlugin().manifest.requires).contains("documents")
        assertThat(BillingItemsPlugin().manifest.requires).isEmpty()
        assertThat(BillingPlugin().manifest.runtimes).contains(PluginRuntime.PYTHON)
        assertThat(TimeCampPlugin().manifest.defaultRoles).containsExactly("ADMIN")
    }
}
