package com.starter.plugins.operations

import com.starter.repository.postgres.UserRepository
import org.springframework.stereotype.Service
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.LocalDate
import java.util.UUID

@Service class TimesheetExport(private val timesheets:TimesheetService,private val users:UserRepository) {
    fun csv(email:String,project:UUID,from:LocalDate,to:LocalDate):ByteArray {
        // The same project and senior-role checks as the detailed team review apply to exports.
        val entries=timesheets.list(email,from,to,project,true).filter {it.entry.status==TimeStatus.APPROVED&&it.entry.billable}
        val names=entries.map {it.entry.userId}.distinct().associateWith {users.findById(it).orElse(null)?.name ?: it.toString()}
        val rows=entries.sortedBy {it.entry.start}.map {row->val e=row.entry
            val hours=BigDecimal(e.seconds).divide(BigDecimal(3600),6,RoundingMode.HALF_UP)
            listOf(names[e.userId].orEmpty(),e.start.toString(),e.end.toString(),e.seconds.toString(),hours.toPlainString(),e.description,e.status.name,e.rate?.toPlainString().orEmpty(),e.currency,e.rate?.multiply(hours)?.setScale(java.util.Currency.getInstance(e.currency).defaultFractionDigits.coerceAtLeast(0),RoundingMode.HALF_UP)?.toPlainString().orEmpty())
        }
        return ("\uFEFF"+(listOf(listOf("Member","Start (UTC)","End (UTC)","Seconds","Hours","Work description","Status","Hourly rate (excl. tax)","Currency","Amount (excl. tax)"))+rows).joinToString("\r\n") {row->row.joinToString(",",transform=::cell)}+"\r\n").toByteArray(Charsets.UTF_8)
    }
    companion object {
        fun cell(value:String):String {
            val safe=if(value.trimStart().firstOrNull() in listOf('=','+','-','@')||value.startsWith('\t')||value.startsWith('\r')||value.startsWith('\n'))"'$value" else value
            return "\""+safe.replace("\"","\"\"")+"\""
        }
    }
}
