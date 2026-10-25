package com.starter.plugins

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.starter.plugins.operations.*
import com.starter.exception.*
import com.starter.repository.postgres.UserRepository
import org.assertj.core.api.Assertions.*
import org.junit.jupiter.api.Test
import org.mockito.Mockito.*
import java.math.BigDecimal
import java.time.*
import java.util.UUID

class ProjectBillingTest {
    private val mapper=jacksonObjectMapper().findAndRegisterModules()
    private fun snapshot():BillingSnapshot {
        val id=UUID.randomUUID()
        val profile=BillingProfile(id,"billing@example.test","Address",id)
        val totals=BillingMath.calculate(listOf(BillingLine("x","Work",BigDecimal.ONE,BigDecimal.TEN)),"USD",false,BigDecimal.ZERO)
        return BillingSnapshot("INV-TEST",BillingKind.INVOICE,id,id,"Client","Project",LocalDate.now(),LocalDate.now(),Instant.now(),LocalDate.now(),profile,"Issuer","Address","",totals)
    }
    @Test fun `unsigned and partly signed documents cannot be delivered`() {
        val d=snapshot()
        assertThatThrownBy {BillingDeliveryClaims.requireSignOff(d)}.isInstanceOf(ConflictException::class.java)
        assertThatThrownBy {BillingDeliveryClaims.requireSignOff(d.copy(signedOffBy=UUID.randomUUID()))}.isInstanceOf(ConflictException::class.java)
        BillingDeliveryClaims.requireSignOff(d.copy(signedOffBy=UUID.randomUUID(),signedOffAt=Instant.now()))
    }
    @Test fun `legacy catalog and rates load without exposing dates or recurrence`() {
        val item=mapper.readValue("""{"name":"Hosting","amount":100,"recurrence":"ONCE","activeFrom":"2020-01-01","activeTo":"2021-01-01","enabled":false}""",CatalogItem::class.java)
        assertThat(item.enabled).isFalse()
        assertThat(mapper.writeValueAsString(item)).doesNotContain("recurrence","activeFrom","activeTo")
        val r=mapper.readValue("""{"name":"Developer","amount":450,"effectiveFrom":"2020-01-01","effectiveTo":null}""",DeveloperRate::class.java)
        assertThat(r.enabled).isTrue()
        assertThat(mapper.writeValueAsString(r)).doesNotContain("effectiveFrom","effectiveTo")
    }
    @Test fun `project member rate wins and disabling it never falls back to a default`() {
        val user=UUID.randomUUID();val project=UUID.randomUUID()
        var rate=ProjectMemberRate(user,BigDecimal("750"),"ZAR")
        val row=OpsRow(UUID.randomUUID(),user,project,"PROJECT_RATE","{}",0,Instant.now())
        val store=mock(OperationsStore::class.java) {call->when(call.method.name){
            "list"->if((call.arguments[1] as String).contains("PROJECT_RATE"))listOf(row)else emptyList<OpsRow>()
            "decode"->rate
            else->null
        }}
        val access=mock(OperationsAccess::class.java);`when`(access.enabled("billing-items")).thenReturn(true)
        val rates=BillingRates(store,access,mock(UserRepository::class.java),mock(TimeVisibility::class.java))
        val resolved=rates.resolve(user,project)!!
        assertThat(resolved.amount).isEqualByComparingTo("750");assertThat(resolved.currency).isEqualTo("ZAR")
        rate=rate.copy(amount=BigDecimal("900"),enabled=false)
        assertThat(rates.resolve(user,project)).isNull()
        assertThat(resolved.amount).isEqualByComparingTo("750")
    }
    @Test fun `payment plugins validate manifests configuration and HTTPS links`() {
        val providers=listOf(BankTransferPaymentPlugin(),PayfastPaymentPlugin(),HostedPaymentPlugin())
        providers.forEach {PluginManifests.validateIdentity(it.manifest);PluginManifests.validate(it.manifest)}
        val bank=providers.first()
        assertThat(bank.payment(emptyMap())).isNull()
        assertThat(bank.payment(mapOf("bankName" to "Bank","accountName" to "Company","accountNumber" to "1234"))!!.instructions).contains("1234","invoice number")
        val payfast=providers[1]
        assertThatThrownBy {payfast.validateConfiguration(mapOf("paymentUrl" to "javascript:alert(1)"))}.isInstanceOf(BadRequestException::class.java)
        assertThatThrownBy {payfast.validateConfiguration(mapOf("paymentUrl" to "https://user:pass@pay.example"))}.isInstanceOf(BadRequestException::class.java)
        val payment=payfast.payment(mapOf("paymentUrl" to "https://pay.example/checkout"))!!
        assertThat(payment.paymentUrl).isEqualTo("https://pay.example/checkout")
    }
    @Test fun `CSV cells escape quotes newlines and spreadsheet formulas`() {
        assertThat(TimesheetExport.cell("=HYPERLINK(\"https://example.test\")")).startsWith("\"'=")
        assertThat(TimesheetExport.cell("  +SUM(1,2)")).startsWith("\"'  +")
        assertThat(TimesheetExport.cell("Work, \"quoted\"\nnext line")).isEqualTo("\"Work, \"\"quoted\"\"\nnext line\"")
    }
}
