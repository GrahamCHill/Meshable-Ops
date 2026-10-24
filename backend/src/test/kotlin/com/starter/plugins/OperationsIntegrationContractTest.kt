package com.starter.plugins

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.starter.plugins.operations.*
import com.starter.plugins.integrations.*
import com.starter.security.SecretEncryption
import org.assertj.core.api.Assertions.*
import org.junit.jupiter.api.Test
import org.mockito.Mockito.*
import org.springframework.test.util.ReflectionTestUtils
import org.springframework.transaction.support.AbstractPlatformTransactionManager
import org.springframework.transaction.support.DefaultTransactionStatus
import java.math.BigDecimal
import java.time.*
import java.util.UUID

class OperationsIntegrationContractTest {
    private val mapper=jacksonObjectMapper().findAndRegisterModules()
    private val crypto=SecretEncryption(java.util.Base64.getEncoder().encodeToString(ByteArray(32){3}))
    private class Transactions:AbstractPlatformTransactionManager() {
        override fun doGetTransaction():Any=Any()
        override fun doBegin(transaction:Any,definition:org.springframework.transaction.TransactionDefinition){}
        override fun doCommit(status:DefaultTransactionStatus){}
        override fun doRollback(status:DefaultTransactionStatus){}
    }
    private fun fixture(connection:CloudConnection,credentials:Map<String,String>,responses:List<JsonFixture>):Pair<CloudCosts,MutableList<CloudSpend>> {
        val id=UUID.fromString("11111111-1111-1111-1111-111111111111");val project=UUID.randomUUID();val row=OpsRow(id,UUID.randomUUID(),project,"CONNECTION",mapper.writeValueAsString(connection),0,Instant.now())
        val stored=crypto.encrypt(mapper.writeValueAsString(credentials));val saved=mutableListOf<CloudSpend>()
        val store=mock(OperationsStore::class.java){call->when(call.method.name){"getMapper"->mapper;"get"->row;"secret"->stored;"list"->emptyList<OpsRow>();"decode"->mapper.readValue((call.arguments[0] as OpsRow).dataJson,call.arguments[1] as Class<*>);"save"->{(call.arguments[4] as? CloudSpend)?.let(saved::add);row};"stable"->UUID.nameUUIDFromBytes((call.arguments[0] as String).toByteArray());else->null}}
        var index=0
        val http=mock(IntegrationHttpClient::class.java){call->if(call.method.name=="scoped"){val f=responses[index++];assertThat(call.arguments[0]).isEqualTo(f.method);assertThat(call.arguments[1]).isEqualTo(f.base);f.check(call.arguments[4] as String?);IntegrationResponse(200,mapper.readTree(f.response))}else null}
        val service=CloudCosts(store,mock(OperationsAccess::class.java,RETURNS_DEEP_STUBS),crypto,http)
        ReflectionTestUtils.setField(service,"transactionManager",Transactions())
        return service to saved
    }
    data class JsonFixture(val method:String,val base:String,val response:String,val check:(String?)->Unit={})
    @Test fun `AWS query signs the fixed endpoint and requests only the linked account`() {
        val c=CloudConnection(CloudProvider.AWS,"Cloud","123456789012")
        val (service,saved)=fixture(c,mapOf("accessKeyId" to "AKIATESTONLY","secretAccessKey" to "local-test-secret"),listOf(JsonFixture("POST","https://ce.us-east-1.amazonaws.com/","""{"ResultsByTime":[{"TimePeriod":{"Start":"2026-01-01","End":"2026-01-02"},"Estimated":true,"Groups":[{"Keys":["S3"],"Metrics":{"UnblendedCost":{"Amount":"12.34","Unit":"USD"}}}]}]}"""){body->val query=mapper.readTree(body);assertThat(query.path("Filter").path("Dimensions").path("Values").first().asText()).isEqualTo("123456789012");assertThat(query.path("TimePeriod").path("End").asText()).isEqualTo("2026-01-02")}))
        service.refresh("admin@example.test",UUID.fromString("11111111-1111-1111-1111-111111111111"),CloudRefresh(LocalDate.parse("2026-01-01"),LocalDate.parse("2026-01-01")))
        assertThat(saved.single().amount).isEqualByComparingTo(BigDecimal("12.34"));assertThat(saved.single().estimated).isTrue()
    }
    @Test fun `GCP uses parameterized project and date filters with a query billing cap`() {
        val c=CloudConnection(CloudProvider.GCP,"Cloud","my-project","my-project.billing.costs","my-project")
        val (service,saved)=fixture(c,mapOf("clientId" to "test","clientSecret" to "secret","refreshToken" to "refresh"),listOf(JsonFixture("POST","https://oauth2.googleapis.com/token","""{"access_token":"test-token"}"""),JsonFixture("POST","https://bigquery.googleapis.com","""{"jobComplete":true,"rows":[{"f":[{"v":"2026-01-01"},{"v":"Compute"},{"v":"USD"},{"v":"10.50"}]}]}"""){body->val q=mapper.readTree(body);assertThat(q.path("query").asText()).contains("project.id=@project","UNNEST(credits)");assertThat(q.path("maximumBytesBilled").asText()).isEqualTo("104857600");assertThat(q.path("queryParameters").size()).isEqualTo(3)}))
        service.refresh("admin@example.test",UUID.fromString("11111111-1111-1111-1111-111111111111"),CloudRefresh(LocalDate.parse("2026-01-01"),LocalDate.parse("2026-01-01")))
        assertThat(saved.single().service).isEqualTo("Compute");assertThat(saved.single().amount).isEqualByComparingTo("10.50")
    }
    @Test fun `billing MIME extraction ignores HTML and attachments`() {
        val multipart=jakarta.mail.internet.MimeMultipart("alternative")
        multipart.addBodyPart(jakarta.mail.internet.MimeBodyPart().apply {setContent("<img src='https://external.test/tracker'>","text/html")})
        multipart.addBodyPart(jakarta.mail.internet.MimeBodyPart().apply {setText("Please cancel this service.")})
        val message=jakarta.mail.internet.MimeMessage(jakarta.mail.Session.getInstance(java.util.Properties())).apply {setContent(multipart);saveChanges()}
        assertThat(BillingInbox.plain(message,0)).isEqualTo("Please cancel this service.")
    }
}
