package com.starter.plugins.operations

import com.fasterxml.jackson.annotation.JsonProperty
import com.fasterxml.jackson.databind.JsonNode
import com.starter.exception.*
import com.starter.plugins.integrations.IntegrationHttpClient
import com.starter.security.SecretEncryption
import com.azure.identity.ClientSecretCredentialBuilder
import com.azure.core.credential.TokenRequestContext
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import software.amazon.awssdk.auth.credentials.*
import software.amazon.awssdk.auth.signer.Aws4Signer
import software.amazon.awssdk.auth.signer.params.Aws4SignerParams
import software.amazon.awssdk.http.*
import software.amazon.awssdk.regions.Region
import java.io.ByteArrayInputStream
import java.math.BigDecimal
import java.net.URI
import java.net.URLEncoder
import java.time.*
import java.util.UUID

enum class CloudProvider { AWS, AZURE, GCP }
data class CloudConnection(val provider:CloudProvider,val name:String,val scope:String,val billingTable:String="",val queryProject:String="",val enabled:Boolean=true)
data class CloudConnectionInput(val projectId:UUID,val connection:CloudConnection,
    @get:JsonProperty(access=JsonProperty.Access.WRITE_ONLY) val credentials:Map<String,String> = emptyMap(),val clearCredentials:Boolean=false,val revision:Long = -1) {override fun toString()="CloudConnectionInput(projectId=$projectId)"}
data class CloudSpend(val connectionId:UUID,val provider:CloudProvider,val date:LocalDate,val service:String,val amount:BigDecimal,val currency:String,val estimated:Boolean=true)
data class CloudRefresh(val from:LocalDate,val to:LocalDate)

@Service
class CloudCosts(private val store:OperationsStore,private val access:OperationsAccess,private val encryption:SecretEncryption,private val http:IntegrationHttpClient,
    private val providers:CloudProviders?=null) {
    fun providerEnabled(provider:CloudProvider)=providers?.enabled(provider) ?: true
    private fun requireProvider(provider:CloudProvider) {if(!providerEnabled(provider))throw BadRequestException("Enable ${provider.name} in Cloud costs settings before using this provider")}
    fun list(email:String,project:UUID):Map<String,Any> {
        val (actor,p)=access.project(email,"cloud-costs",project)
        val manage=com.starter.access.Roles.admin(actor.role)
        val connections=store.list("cloud-costs","project_id=? AND kind='CONNECTION'",project).map {mapOf("id" to it.id,"connection" to store.decode(it,CloudConnection::class.java),"revision" to it.revision,"credentialsConfigured" to !store.secret("cloud-costs",it.id).isNullOrBlank())}
        return mapOf("connections" to connections,"canConfigure" to manage,"projectName" to p.name,"enabledProviders" to (providers?.all() ?: CloudProvider.entries.associateWith {true}))
    }
    fun spend(email:String,project:UUID,from:LocalDate,to:LocalDate):List<Map<String,Any>> {
        access.project(email,"cloud-costs",project);OperationsValidation.dates(from,to)
        return store.list("cloud-costs","project_id=? AND kind='SPEND' AND CAST(data_json AS jsonb)->>'date'>=? AND CAST(data_json AS jsonb)->>'date'<=?",project,from.toString(),to.toString(),limit=10000).map {mapOf("id" to it.id,"spend" to store.decode(it,CloudSpend::class.java))}
    }
    @Transactional fun connect(email:String,id:UUID?,input:CloudConnectionInput):OpsRow {
        val actor=access.admin(email,"cloud-costs",true);access.project(email,"cloud-costs",input.projectId,true,true)
        requireProvider(input.connection.provider)
        validate(input.connection)
        val old=id?.let {store.get("cloud-costs",it).also {r->if(r.kind!="CONNECTION"||r.projectId!=input.projectId)throw NotFoundException("Connection unavailable")}}
        val previous=old?.let {store.decode(it,CloudConnection::class.java)}
        val changed=previous?.let {it.provider!=input.connection.provider||it.scope!=input.connection.scope||it.queryProject!=input.connection.queryProject||it.billingTable!=input.connection.billingTable}==true
        if(input.credentials.size>6||input.credentials.values.any {it.length>8192||it.any(Char::isISOControl)})throw BadRequestException("Use bounded cloud credentials without control characters")
        val allowed=when(input.connection.provider){CloudProvider.AWS->setOf("accessKeyId","secretAccessKey","sessionToken");CloudProvider.AZURE->setOf("tenantId","clientId","clientSecret");CloudProvider.GCP->setOf("clientId","clientSecret","refreshToken")}
        if(input.credentials.keys.any {it !in allowed})throw BadRequestException("Use this provider's declared credential fields")
        val row=store.save("cloud-costs",old?.ownerId ?: actor.id,input.projectId,"CONNECTION",input.connection,id ?: UUID.randomUUID(),if(id==null)-1 else input.revision)
        if(input.clearCredentials||changed)store.setSecret("cloud-costs",row.id,null)
        if(input.credentials.isNotEmpty()) {
            encryption.requireConfigured()
            val previousCredentials=if(!input.clearCredentials&&!changed)encryption.decrypt(store.secret("cloud-costs",row.id))?.let {store.mapper.readValue(it,object:com.fasterxml.jackson.core.type.TypeReference<Map<String,String>>() {})}.orEmpty()else emptyMap()
            store.setSecret("cloud-costs",row.id,encryption.encrypt(store.mapper.writeValueAsString(previousCredentials+input.credentials.filterValues {it.isNotBlank()})))
        }
        return row
    }
    fun refresh(email:String,id:UUID,input:CloudRefresh):ImportResult {
        access.admin(email,"cloud-costs",true);OperationsValidation.dates(input.from,input.to,31)
        val row=store.get("cloud-costs",id);if(row.kind!="CONNECTION")throw NotFoundException("Connection unavailable")
        access.project(email,"cloud-costs",row.projectId!!,true,true)
        val connection=store.decode(row,CloudConnection::class.java);if(!connection.enabled)throw BadRequestException("Enable this connection first")
        requireProvider(connection.provider)
        val encrypted=store.secret("cloud-costs",id)
        val credentials=encryption.decrypt(encrypted)?.let {store.mapper.readTree(it)} ?: store.mapper.createObjectNode()
        val result=when(connection.provider){CloudProvider.AWS->aws(id,connection,credentials,input);CloudProvider.AZURE->azure(id,connection,credentials,input);CloudProvider.GCP->google(id,connection,credentials,input)}
        access.admin(email,"cloud-costs",true)
        requireProvider(connection.provider)
        if(store.get("cloud-costs",id).revision!=row.revision||store.secret("cloud-costs",id)!=encrypted)throw ConflictException("Cloud connection changed while refreshing")
        persist(row,result,input)
        return ImportResult(result.size,0)
    }
    private fun persist(row:OpsRow,result:List<CloudSpend>,period:CloudRefresh) {
        if(result.size>5000)throw BadRequestException("Too many cloud cost rows; narrow the period")
        val tx=org.springframework.transaction.support.TransactionTemplate(transactionManager!!)
        tx.executeWithoutResult {
            store.lock("cloud-refresh:${row.id}")
            if(store.get("cloud-costs",row.id).revision!=row.revision)throw ConflictException("Cloud connection changed")
            // Replace the whole successful window atomically, including disappeared or adjusted cost rows.
            val old=store.list("cloud-costs","project_id=? AND kind='SPEND' AND CAST(data_json AS jsonb)->>'connectionId'=? AND CAST(data_json AS jsonb)->>'date'>=? AND CAST(data_json AS jsonb)->>'date'<=?",row.projectId!!,row.id.toString(),period.from.toString(),period.to.toString(),limit=10000)
            old.forEach {store.remove("cloud-costs",it)}
            result.forEach {value->OperationsValidation.currency(value.currency);if(value.date<period.from||value.date>period.to||value.amount.abs()>BigDecimal("1000000000000"))throw BadRequestException("Cloud cost response contains out-of-range values");store.save("cloud-costs",row.ownerId,row.projectId,"SPEND",value,store.stable("cloud:${value.connectionId}:${value.date}:${value.service}:${value.currency}"))}
        }
    }
    @org.springframework.beans.factory.annotation.Autowired private var transactionManager:org.springframework.transaction.PlatformTransactionManager?=null
    private fun aws(id:UUID,c:CloudConnection,secret:JsonNode,p:CloudRefresh):List<CloudSpend> {
        val creds=if(secret.path("accessKeyId").asText().isNotBlank()) {
            if(secret.path("sessionToken").asText().isNotBlank())AwsSessionCredentials.create(secret.path("accessKeyId").asText(),secret.path("secretAccessKey").asText(),secret.path("sessionToken").asText())
            else AwsBasicCredentials.create(secret.path("accessKeyId").asText(),secret.path("secretAccessKey").asText())
        } else DefaultCredentialsProvider.create().use {it.resolveCredentials()}
        val result=mutableListOf<CloudSpend>();var next:String?=null;val seen=mutableSetOf<String>()
        repeat(30) {
            val body=mutableMapOf<String,Any>("TimePeriod" to mapOf("Start" to p.from.toString(),"End" to p.to.plusDays(1).toString()),"Granularity" to "DAILY","Metrics" to listOf("UnblendedCost"),"Filter" to mapOf("Dimensions" to mapOf("Key" to "LINKED_ACCOUNT","Values" to listOf(c.scope))),"GroupBy" to listOf(mapOf("Type" to "DIMENSION","Key" to "SERVICE")))
            next?.let {body["NextPageToken"]=it}
            val json=store.mapper.writeValueAsString(body);val bytes=json.toByteArray(Charsets.UTF_8)
            val request=SdkHttpFullRequest.builder().uri(URI("https://ce.us-east-1.amazonaws.com/")).method(SdkHttpMethod.POST).putHeader("Content-Type","application/x-amz-json-1.1").putHeader("X-Amz-Target","AWSInsightsIndexService.GetCostAndUsage").contentStreamProvider {ByteArrayInputStream(bytes)}.build()
            val signed=Aws4Signer.create().sign(request,Aws4SignerParams.builder().awsCredentials(creds).signingName("ce").signingRegion(Region.US_EAST_1).build())
            val data=http.scoped("POST","https://ce.us-east-1.amazonaws.com/",headers=signed.headers().mapValues {it.value.joinToString(",")},body=json,contentType="application/x-amz-json-1.1").body ?: throw BadRequestException("AWS returned no cost data")
            if(!data.path("ResultsByTime").isArray)throw BadRequestException("Unsupported AWS cost response")
            data.path("ResultsByTime").forEach {day->day.path("Groups").forEach {g->val metric=g.path("Metrics").path("UnblendedCost");result+=CloudSpend(id,c.provider,LocalDate.parse(day.path("TimePeriod").path("Start").asText()),g.path("Keys").first().asText().take(200),metric.path("Amount").asText().toBigDecimal(),metric.path("Unit").asText(),day.path("Estimated").asBoolean(true))}}
            next=data.path("NextPageToken").asText().takeIf {it.isNotBlank()}
            if(next==null)return result
            if(!seen.add(next!!))throw BadRequestException("AWS pagination did not advance")
        }
        throw BadRequestException("AWS cost result exceeded 30 pages")
    }
    private fun azure(id:UUID,c:CloudConnection,s:JsonNode,p:CloudRefresh):List<CloudSpend> {
        listOf("tenantId","clientId").forEach {if(runCatching {UUID.fromString(s.path(it).asText())}.isFailure)throw BadRequestException("Configure Azure tenant and application UUIDs")}
        val token=ClientSecretCredentialBuilder().tenantId(s.path("tenantId").asText()).clientId(s.path("clientId").asText()).clientSecret(s.path("clientSecret").asText()).build().getToken(TokenRequestContext().addScopes("https://management.azure.com/.default")).block(Duration.ofSeconds(20))?.token ?: throw BadRequestException("Azure authentication unavailable")
        val body=store.mapper.writeValueAsString(mapOf("type" to "ActualCost","timeframe" to "Custom","timePeriod" to mapOf("from" to "${p.from}T00:00:00Z","to" to "${p.to}T23:59:59Z"),"dataset" to mapOf("granularity" to "Daily","aggregation" to mapOf("totalCost" to mapOf("name" to "PreTaxCost","function" to "Sum")),"grouping" to listOf(mapOf("type" to "Dimension","name" to "ServiceName")))))
        val data=http.scoped("POST","https://management.azure.com","subscriptions/${c.scope}/providers/Microsoft.CostManagement/query?api-version=2025-03-01",mapOf("Authorization" to "Bearer $token"),body).body?.path("properties") ?: throw BadRequestException("Azure returned no cost data")
        if(data.path("nextLink").asText().isNotBlank())throw BadRequestException("Azure cost result is paginated. Use a narrower period; partial totals were not saved.")
        val columns=data.path("columns").map {it.path("name").asText()};fun at(row:JsonNode,name:String)=row.path(columns.indexOfFirst {it.equals(name,true)})
        return data.path("rows").map {row->CloudSpend(id,c.provider,LocalDate.parse(at(row,"UsageDate").asText(),java.time.format.DateTimeFormatter.BASIC_ISO_DATE),at(row,"ServiceName").asText().take(200),at(row,"PreTaxCost").asText().toBigDecimal(),at(row,"Currency").asText())}
    }
    private fun google(id:UUID,c:CloudConnection,s:JsonNode,p:CloudRefresh):List<CloudSpend> {
        val form=mapOf("grant_type" to "refresh_token","client_id" to s.path("clientId").asText(),"client_secret" to s.path("clientSecret").asText(),"refresh_token" to s.path("refreshToken").asText()).entries.joinToString("&"){URLEncoder.encode(it.key,Charsets.UTF_8)+"="+URLEncoder.encode(it.value,Charsets.UTF_8)}
        val token=http.scoped("POST","https://oauth2.googleapis.com/token",body=form,contentType="application/x-www-form-urlencoded").body?.path("access_token")?.asText()?.takeIf {it.isNotBlank()} ?: throw BadRequestException("Google authentication unavailable")
        val query="SELECT CAST(DATE(usage_start_time) AS STRING) AS day, service.description, currency, CAST(SUM(cost + IFNULL((SELECT SUM(cr.amount) FROM UNNEST(credits) cr),0)) AS STRING) AS amount FROM `${c.billingTable}` WHERE project.id=@project AND DATE(usage_start_time) BETWEEN @from AND @to GROUP BY day, service.description, currency ORDER BY day LIMIT 5001"
        val parameters=mapOf("project" to c.scope,"from" to p.from.toString(),"to" to p.to.toString()).map {(name,value)->mapOf("name" to name,"parameterType" to mapOf("type" to if(name=="project")"STRING" else "DATE"),"parameterValue" to mapOf("value" to value))}
        val body=store.mapper.writeValueAsString(mapOf("query" to query,"useLegacySql" to false,"parameterMode" to "NAMED","queryParameters" to parameters,"maximumBytesBilled" to "104857600","timeoutMs" to 10000,"maxResults" to 5001))
        val data=http.scoped("POST","https://bigquery.googleapis.com","bigquery/v2/projects/${c.queryProject}/queries",mapOf("Authorization" to "Bearer $token"),body).body ?: throw BadRequestException("Google returned no query data")
        if(!data.path("jobComplete").asBoolean()||data.has("pageToken")||data.path("errors").size()>0)throw BadRequestException("BigQuery did not return a complete successful result; no partial totals saved")
        return data.path("rows").map {row->val cells=row.path("f").map {it.path("v").asText()};CloudSpend(id,c.provider,LocalDate.parse(cells[0]),cells[1].take(200),cells[3].toBigDecimal(),cells[2])}
    }
    companion object {fun validate(c:CloudConnection) {
        OperationsValidation.text(c.name,"connection name",120)
        val valid=when(c.provider){CloudProvider.AWS->c.scope.matches(Regex("[0-9]{12}"));CloudProvider.AZURE->runCatching {UUID.fromString(c.scope)}.isSuccess;CloudProvider.GCP->c.scope.matches(Regex("[a-z][a-z0-9-]{4,61}[a-z0-9]"))&&c.queryProject.matches(Regex("[a-z][a-z0-9-]{4,61}[a-z0-9]"))&&c.billingTable.matches(Regex("[a-z][a-z0-9-]{4,61}[a-z0-9]\\.[A-Za-z_][A-Za-z0-9_]{0,100}\\.[A-Za-z_][A-Za-z0-9_]{0,150}"))}
        if(!valid)throw BadRequestException("Use a valid AWS account, Azure subscription, or GCP project and billing export table")
    }}
}
