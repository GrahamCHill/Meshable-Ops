package com.starter.plugins.calendar

import com.starter.plugins.integrations.IntegrationUnavailable
import java.time.*
import java.time.format.DateTimeFormatter

/** A bounded interchange subset. Recurrences must be expanded by the CalDAV server. */
object IcalendarCodec {
    private val stamp = DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss'Z'").withZone(ZoneOffset.UTC)
    private val localTime = DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss")
    fun export(events: List<CalendarEventView>,uidOverrides: Map<String,String> = emptyMap()): String {
        val lines = mutableListOf("BEGIN:VCALENDAR","VERSION:2.0","PRODID:-//Meshable Ops//Calendar//EN","CALSCALE:GREGORIAN")
        events.forEach { event ->
            lines += listOf("BEGIN:VEVENT","UID:${escape(uidOverrides[event.id] ?: "${event.id}@weaveops")}","DTSTAMP:${stamp.format(Instant.now())}","SUMMARY:${escape(event.title)}","DESCRIPTION:${escape(event.description)}")
            listOf("DTSTART" to event.start,"DTEND" to event.end).forEach { (key,value) -> lines += if (event.allDay) "$key;VALUE=DATE:${LocalDate.parse(value).format(DateTimeFormatter.BASIC_ISO_DATE)}" else "$key:${stamp.format(Instant.parse(value))}" }
            lines += "END:VEVENT"
        }
        lines += "END:VCALENDAR"
        return lines.joinToString("\r\n",postfix="\r\n",transform=::fold)
    }
    fun parse(text: String, source: String): CalendarWindow {
        if (text.length > 1_048_576) throw IntegrationUnavailable("Calendar response too large")
        val lines = text.replace("\r\n","\n").replace(Regex("\n[ \t]"),"").split('\n')
        var current: MutableList<String>? = null
        val events = mutableListOf<CalendarEventView>(); var skipped = false
        for (line in lines) {
            if (line == "BEGIN:VEVENT") { current = mutableListOf(); continue }
            if (line == "END:VEVENT") {
                val result = current?.let { runCatching { event(it,source) }.getOrNull() }
                if (result == null) skipped = true else if (events.size < 100) events += result else skipped = true
                current = null
            } else current?.add(line)
        }
        return CalendarWindow(events,skipped,if (skipped) "Some events were omitted: unsupported recurrence, time zone, malformed data, or the 100-event limit. Export or view them in your calendar provider." else null)
    }
    fun uid(text: String): String? = text.replace("\r\n","\n").replace(Regex("\n[ \t]"),"").lineSequence().find { it.startsWith("UID:",ignoreCase=true) }?.substringAfter(':')?.let(::unescape)
    /** Preserve provider-only fields, UID, alarms and time zones when CalDAV requires a whole-resource PUT. */
    fun update(original: String,event: CalendarEventView): String {
        fun lines(value: String)=value.replace("\r\n","\n").replace(Regex("\n[ \t]"),"").split('\n').filter { it.isNotEmpty() }
        val managed=setOf("SUMMARY","DESCRIPTION","DTSTART","DTEND","DURATION","DTSTAMP","LAST-MODIFIED","SEQUENCE")
        val fresh=lines(export(listOf(event))).filter { it.substringBefore(':').substringBefore(';').uppercase() in managed }
        val old=lines(original)
        val sequence=old.find { it.startsWith("SEQUENCE:") }?.substringAfter(':')?.toLongOrNull()?.coerceIn(0,Int.MAX_VALUE.toLong()-1) ?: 0
        val out=mutableListOf<String>();var depth=0
        old.forEach { line ->
            if (line.equals("BEGIN:VEVENT",true)) {depth=1;out+=line;out+=fresh;out+="LAST-MODIFIED:${stamp.format(Instant.now())}";out+="SEQUENCE:${sequence+1}"}
            else {
                if (depth!=1 || line.substringBefore(':').substringBefore(';').uppercase() !in managed) out+=line
                if (depth>0 && line.startsWith("BEGIN:",true)) depth++
                if (depth>0 && line.startsWith("END:",true)) depth--
            }
        }
        return out.joinToString("\r\n",postfix="\r\n",transform=::fold)
    }
    private fun event(lines: List<String>, source: String): CalendarEventView? {
        val values = lines.filter { ':' in it }.groupBy { it.substringBefore(':').substringBefore(';').uppercase() }
        if (values.containsKey("RRULE") || values.containsKey("RDATE") || values.containsKey("EXDATE") || values["STATUS"]?.first()?.substringAfter(':') == "CANCELLED") return null
        fun raw(key: String) = values[key]?.first()
        fun value(key: String) = raw(key)?.substringAfter(':')?.let(::unescape).orEmpty()
        val startLine = raw("DTSTART") ?: return null
        val allDay = startLine.substringAfter(':').length == 8
        fun time(line: String): String {
            val value = line.substringAfter(':')
            if (allDay) return LocalDate.parse(value,DateTimeFormatter.BASIC_ISO_DATE).toString()
            val zone = line.substringBefore(':').split(';').find { it.startsWith("TZID=") }?.substringAfter('=')?.trim('"')
            return if (value.endsWith('Z')) LocalDateTime.parse(value.dropLast(1),localTime).toInstant(ZoneOffset.UTC).toString()
                else if (zone != null) LocalDateTime.parse(value,localTime).atZone(ZoneId.of(zone)).toInstant().toString()
                else throw IllegalArgumentException("Floating calendar time")
        }
        val start = time(startLine)
        val end = raw("DTEND")?.let(::time) ?: if (allDay) LocalDate.parse(start).plusDays(1).toString() else return null
        if (end <= start || value("UID").isBlank()) return null
        val recurrence = value("RECURRENCE-ID")
        return CalendarEventView("$source:${value("UID").take(800)}:$recurrence",value("SUMMARY").ifBlank { "Untitled event" }.take(300),value("DESCRIPTION").take(4000),start,end,allDay,source)
    }
    private fun escape(value: String) = value.replace("\\","\\\\").replace("\r\n","\n").replace("\r","\n").replace("\n","\\n").replace(";","\\;").replace(",","\\,")
    private fun unescape(value: String): String {
        val result = StringBuilder(); var index = 0
        while (index < value.length) { val char = value[index++]; if (char == '\\' && index < value.length) { val next = value[index++]; result.append(if (next == 'n' || next == 'N') '\n' else next) } else result.append(char) }
        return result.toString()
    }
    private fun fold(value: String): String {
        val result = StringBuilder(); var bytes = 0
        value.codePoints().forEach { point ->
            val char = String(Character.toChars(point)); val size = char.toByteArray(Charsets.UTF_8).size
            if (bytes + size > 75) { result.append("\r\n "); bytes = 1 }
            result.append(char); bytes += size
        }
        return result.toString()
    }
}
