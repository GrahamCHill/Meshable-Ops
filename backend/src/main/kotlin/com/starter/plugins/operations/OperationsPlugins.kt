package com.starter.plugins.operations

import com.starter.plugins.*
import com.starter.exception.BadRequestException
import org.springframework.stereotype.Component

private fun field(key:String,label:String,help:String="",default:String="")=PluginSettingField(key,label,help=help,maxLength=2000,defaultValue=default)
private fun secret(key:String,label:String)=PluginSettingField(key,label,PluginFieldType.SECRET,maxLength=4096)
private fun flag(key:String,label:String,help:String="")=PluginSettingField(key,label,PluginFieldType.BOOLEAN,help=help)
abstract class OperationsPlugin:WorkspacePlugin {
    override fun validateConfiguration(values:Map<String,String>) {
        values["dailyTime"]?.let {if(runCatching {java.time.LocalTime.parse(it)}.isFailure)throw BadRequestException("Choose a valid daily time")}
        values["timeZone"]?.let {if(runCatching {java.time.ZoneId.of(it)}.isFailure)throw BadRequestException("Choose a valid timezone")}
    }
}
private fun manifest(id:String,name:String,description:String,category:String,settings:List<PluginSettingField> = emptyList(),requires:Set<String> = emptySet(),extends:Set<String> = emptySet(),capabilities:Map<String,String> = emptyMap(),menus:List<PluginMenuItem> = emptyList(),notify:Boolean=false)=PluginManifest(
    id,name,"1.0.0",description,compatibleVersions=listOf("1.0.0"),
    extensions=setOf(PluginExtension.PAGE,PluginExtension.API,PluginExtension.DATABASE,PluginExtension.SETTINGS)+(if(notify)setOf(PluginExtension.NOTIFICATIONS)else emptySet()),
    tables=listOf(PluginTable(OperationsStore.table(id),"$name records")),settingsFields=settings,categories=setOf(category),requires=requires,extends=extends,
    capabilities=capabilities,menuItems=menus,adminMenu=PluginAdminMenu("operations","Operations",name,if(category=="Finance")"briefcase" else "calendar"),
    contextTargets=if(id in setOf("timesheets","cloud-costs","billing","billing-items","on-call")) (if(id=="billing")setOf("PROJECT","CLIENT")else setOf("PROJECT")) else emptySet(),
    runtimes=setOf(PluginRuntime.REACT,PluginRuntime.KOTLIN)+(if(id=="billing")setOf(PluginRuntime.PYTHON)else emptySet()))

@Component class TimesheetsPlugin:OperationsPlugin() {
    override val manifest=manifest("timesheets","Time tracking & timesheets","Internal timers, daily timesheets, project approvals and a shared, reconciled work ledger.","Productivity",
        listOf(flag("internalEnabled","Allow internal time tracking","Administrators enable manual entries and timers. Imports have their own controls.")),
        capabilities=mapOf("time.entries" to "Approved project time"),menus=listOf(PluginMenuItem("approvals","Project approvals")))
}
@Component class ActivityWatchPlugin:OperationsPlugin() {
    override val manifest=manifest("activitywatch","ActivityWatch","Privately import an ActivityWatch JSON export and allocate selected activity to your projects.","Productivity",requires=setOf("timesheets"))
}
@Component class TimeCampPlugin:OperationsPlugin() {
    override val manifest=manifest("timecamp","TimeCamp","Administrator-managed daily imports with explicit provider-user and task-to-project mappings.","Productivity",
        listOf(secret("apiToken","TimeCamp API token"),flag("dailyEnabled","Enable daily sync"),field("dailyTime","Daily sync time","HH:mm","00:00"),field("timeZone","Timezone","IANA timezone","UTC")),requires=setOf("timesheets")).copy(defaultRoles=setOf("ADMIN"))
}
@Component class CloudCostsPlugin:OperationsPlugin() {
    override val manifest=manifest("cloud-costs","Cloud costs","Track AWS Cost Explorer, Azure subscription usage and Google Cloud billing exports against projects.","Finance",
        listOf(flag("dailyEnabled","Enable daily refresh"),field("dailyTime","Daily refresh time","HH:mm","01:00"),field("timeZone","Timezone","IANA timezone","UTC")),capabilities=mapOf("cloud.costs" to "Project cloud spend"))
}
@Component class BillingItemsPlugin:OperationsPlugin() {
    override val manifest=manifest("billing-items","Billing items & rates","Recurring flat charges, effective-dated developer rates and approved hours for invoice generation.","Finance",extends=setOf("timesheets","billing"),capabilities=mapOf("billing.items" to "Invoice source items"))
}
@Component class BillingPlugin:OperationsPlugin() {
    override val manifest=manifest("billing","Invoices & quotes","Client billing profiles, quotes, invoices, opt-in schedules and routed billing replies. Duplicate source charges require confirmation.","Finance",
        listOf(field("defaultCurrency","Default currency","Three-letter ISO currency","USD"),flag("vatRegistered","Business is VAT registered"),field("vatNumber","VAT registration number"),field("issuerName","Registered business name"),field("issuerAddress","Business address"),field("defaultTaxPercent","Default tax percentage","Used only when VAT is enabled","0"),flag("chargeTax","Include VAT by default"),
            flag("inboxEnabled","Read billing replies via IMAPS"),field("imapHost","IMAPS server"),field("imapPort","IMAPS port","TLS only","993"),field("imapUsername","IMAPS username"),secret("imapPassword","IMAPS app password"),field("imapFolder","Billing inbox folder","Use a dedicated billing mailbox/folder","INBOX")),
        requires=setOf("documents"),extends=setOf("billing-items","timesheets","cloud-costs"),menus=listOf(PluginMenuItem("clients","Client billing"),PluginMenuItem("schedules","Schedules"),PluginMenuItem("replies","Billing replies")),notify=true)
    override fun validateConfiguration(values:Map<String,String>) {
        super.validateConfiguration(values)
        values["defaultCurrency"]?.let(OperationsValidation::currency)
        values["defaultTaxPercent"]?.let {if(it.toBigDecimalOrNull()?.let {n->n.signum()>=0 && n<=java.math.BigDecimal(100)}!=true) throw BadRequestException("Use a tax percentage from 0 to 100")}
        values["imapPort"]?.let {if(it.toIntOrNull() !in 1..65535) throw BadRequestException("Choose a valid IMAPS port")}
        values["imapHost"]?.let {if(!it.matches(Regex("[A-Za-z0-9][A-Za-z0-9.-]{0,252}"))) throw BadRequestException("Use a mail server hostname")}
    }
}
@Component class OnCallPlugin:OperationsPlugin() {
    override val manifest=manifest("on-call","Alerts & on-call","Weekly project rotations, service alerts, member reports, severity review and acknowledged escalation through configured channels.","Operations",
        listOf(flag("aiSeverity","Use the configured AI for severity suggestions","Optional. Disabled or unavailable AI uses the submitted severity."),flag("autoConfirmAi","Allow AI-classified SEV1/SEV2 reports to page immediately","Other user reports require a project leader to confirm.")),extends=setOf("discord","teams","telegram","cloud-costs"),notify=true)
}
