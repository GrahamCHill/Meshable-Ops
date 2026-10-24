package com.starter.plugins.operations

import com.starter.plugins.PluginDataClearGuard
import com.starter.exception.BadRequestException
import org.springframework.stereotype.Component

@Component class BillingDocumentClearGuard(private val store:OperationsStore):PluginDataClearGuard {
    override val pluginId="documents"
    override fun check() {
        store.jdbc.execute("LOCK TABLE plugin_billing_records IN SHARE ROW EXCLUSIVE MODE")
        if(store.jdbc.queryForObject("SELECT EXISTS(SELECT 1 FROM plugin_billing_records WHERE kind='DOCUMENT' AND CAST(data_json AS jsonb)->>'generatedDocumentId' IS NOT NULL)",Boolean::class.java)==true)
            throw BadRequestException("Billing documents still reference generated PDFs. Export records and explicitly clear billing data before clearing the document generator.")
    }
}
@Component class TimesheetBillingClearGuard(private val store:OperationsStore):PluginDataClearGuard {
    override val pluginId="timesheets"
    override fun check() {
        store.jdbc.execute("LOCK TABLE plugin_billing_records IN SHARE ROW EXCLUSIVE MODE")
        if(store.jdbc.queryForObject("SELECT EXISTS(SELECT 1 FROM plugin_billing_records WHERE kind='CLAIM' AND CAST(data_json AS jsonb)->>'sourceKey' LIKE 'time:%')",Boolean::class.java)==true)
            throw BadRequestException("Invoices reserve timesheet entries. Export and explicitly clear billing records before clearing timesheets.")
    }
}
