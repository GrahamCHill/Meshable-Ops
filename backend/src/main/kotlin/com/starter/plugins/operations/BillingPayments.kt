package com.starter.plugins.operations

import com.starter.plugins.*
import com.starter.exception.BadRequestException
import org.springframework.stereotype.Component
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.net.URI

data class BillingPayment(val providerId:String,val label:String,val instructions:String,val paymentUrl:String="")
data class BillingPaymentDefault(val providerId:String="")
data class BillingPaymentDefaultInput(val providerId:String,val revision:Long = -1)

/** Add a reviewed provider bean to offer another payment method in Billing. */
interface BillingPaymentProvider {
    val id:String
    val label:String
    fun payment(values:Map<String,String>):BillingPayment?
}

abstract class PaymentProviderPlugin(override val id:String,override val label:String,fields:List<PluginSettingField>):WorkspacePlugin,BillingPaymentProvider {
    override val manifest=PluginManifest(id,label,"1.0.0","Payment instructions for invoices and quotes.",
        extensions=setOf(PluginExtension.API,PluginExtension.SETTINGS),settingsFields=fields,requires=setOf("billing"),
        categories=setOf("Finance"),capabilities=mapOf("billing.payment-provider" to "Invoice payment method"),
        adminMenu=PluginAdminMenu("operations","Operations",label,"briefcase"))
    override fun validateConfiguration(values:Map<String,String>) {
        values.values.forEach {OperationsValidation.text(it,"payment detail",2000,false)}
        values["paymentUrl"]?.takeIf {it.isNotBlank()}?.let(::validatePaymentUrl)
    }
    companion object {
        fun validatePaymentUrl(value:String) {
            val uri=runCatching {URI(value)}.getOrNull()
            if(uri==null||uri.scheme!="https"||uri.host.isNullOrBlank()||uri.userInfo!=null||value.length>2000)throw BadRequestException("Use a public HTTPS payment link without embedded credentials")
        }
    }
}

@Component class BankTransferPaymentPlugin:PaymentProviderPlugin("payment-bank-transfer","Bank transfer",listOf(
    PluginSettingField("bankName","Bank name",maxLength=200),PluginSettingField("accountName","Account holder",maxLength=200),
    PluginSettingField("accountNumber","Account number / IBAN",maxLength=100),PluginSettingField("branchCode","Branch / routing / SWIFT code",maxLength=100),
    PluginSettingField("instructions","Additional payment instructions",maxLength=1000))) {
    override fun payment(values:Map<String,String>):BillingPayment? {
        if(listOf("bankName","accountName","accountNumber").any {values[it].isNullOrBlank()})return null
        val text=listOf("Bank: ${values["bankName"]}","Account holder: ${values["accountName"]}","Account / IBAN: ${values["accountNumber"]}",
            values["branchCode"]?.takeIf {it.isNotBlank()}?.let {"Branch / routing / SWIFT: $it"},values["instructions"]?.takeIf {it.isNotBlank()},"Use the invoice number as your payment reference.").filterNotNull().joinToString("\n")
        return BillingPayment(id,label,text)
    }
}

@Component class PayfastPaymentPlugin:PaymentProviderPlugin("payment-payfast","Payfast",listOf(
    PluginSettingField("paymentUrl","Payfast hosted payment link",help="Paste the HTTPS payment link supplied by your Payfast account. Clients complete payment on the provider's site.",maxLength=2000),
    PluginSettingField("instructions","Payment instructions",maxLength=1000))) {
    override fun payment(values:Map<String,String>):BillingPayment?=hostedPayment(id,label,values)
}

@Component class HostedPaymentPlugin:PaymentProviderPlugin("payment-hosted","Other payment provider",listOf(
    PluginSettingField("providerName","Provider name",maxLength=100),PluginSettingField("paymentUrl","Hosted payment link",maxLength=2000),
    PluginSettingField("instructions","Payment instructions",maxLength=1000))) {
    override fun payment(values:Map<String,String>):BillingPayment?=hostedPayment(id,values["providerName"].orEmpty().ifBlank {label},values)
}

private fun hostedPayment(id:String,label:String,values:Map<String,String>):BillingPayment? {
    val url=values["paymentUrl"]?.takeIf {it.isNotBlank()} ?: return null
    PaymentProviderPlugin.validatePaymentUrl(url)
    return BillingPayment(id,label,values["instructions"].orEmpty(),url)
}

@Service class BillingPayments(private val providers:List<BillingPaymentProvider>,private val config:PluginConfigurationService,private val access:OperationsAccess,private val store:OperationsStore) {
    private fun defaultRow()=store.list("billing","kind='PAYMENT_DEFAULT'").firstOrNull()
    private fun available(provider:BillingPaymentProvider,role:String)=runCatching {access.registry.requireAvailable(provider.id,role)}.isSuccess
    fun options(email:String):Map<String,Any?> {
        val actor=access.actor(email,"billing");val row=defaultRow()
        return mapOf("providers" to providers.map {p->val enabled=available(p,actor.role);val payment=if(enabled)p.payment(config.current(p.id))else null
            mapOf("id" to p.id,"label" to p.label,"enabled" to enabled,"configured" to (payment!=null),"payment" to payment)},
            "defaultProviderId" to (row?.let {store.decode(it,BillingPaymentDefault::class.java).providerId} ?: ""),"revision" to (row?.revision ?: -1))
    }
    @Transactional fun saveDefault(email:String,input:BillingPaymentDefaultInput):Map<String,Any?> {
        val actor=access.admin(email,"billing",true)
        snapshot(email,input.providerId.takeIf {it.isNotBlank()},useDefault=false)
        store.lock("billing-payment-default");val row=defaultRow()
        store.save("billing",row?.ownerId ?: actor.id,null,"PAYMENT_DEFAULT",BillingPaymentDefault(input.providerId),store.stable("billing-payment-default"),input.revision)
        return options(email)
    }
    fun snapshot(email:String,selected:String?,useDefault:Boolean=true):BillingPayment? {
        val actor=access.actor(email,"billing")
        val id=selected ?: if(useDefault)defaultRow()?.let {store.decode(it,BillingPaymentDefault::class.java).providerId}else null
        if(id.isNullOrBlank())return null
        val provider=providers.firstOrNull {it.id==id} ?: throw BadRequestException("Choose an installed payment provider")
        if(!available(provider,actor.role))throw BadRequestException("The selected payment provider is disabled or unavailable")
        return provider.payment(config.current(id)) ?: throw BadRequestException("Configure the selected payment provider before generating a document")
    }
}
