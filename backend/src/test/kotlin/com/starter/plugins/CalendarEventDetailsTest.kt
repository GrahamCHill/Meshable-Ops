package com.starter.plugins

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.starter.exception.BadRequestException
import com.starter.plugins.calendar.*
import org.assertj.core.api.Assertions.*
import org.junit.jupiter.api.Test
import java.time.Instant

class CalendarEventDetailsTest {
    @Test fun `local notes references and participants round trip without inviting anyone`() {
        val details=CalendarEventExtras(listOf(CalendarDocumentLink("Brief","/storage?folder=sample&file=brief.pdf")),listOf(CalendarParticipant("Pat","pat@example.test")))
        CalendarEventDetails.validate(details.documents,details.participants)
        val row=CalendarEvent(title="Review",description="Meeting notes",startsAt=Instant.parse("2026-10-24T10:00:00Z"),endsAt=Instant.parse("2026-10-24T11:00:00Z"),detailsJson=CalendarEventDetails.write(details))
        val view=CalendarService.view(row)
        assertThat(view.documents).isEqualTo(details.documents);assertThat(view.participants).isEqualTo(details.participants)
        assertThat(view.calendarName).isEqualTo("Local calendar")
        assertThat(IcalendarCodec.export(listOf(view))).doesNotContain("ATTENDEE:","ATTACH:")
        assertThat(CalendarEventDetails.read(CalendarEvent()).documents).isEmpty()
    }
    @Test fun `unsafe links oversized metadata and invalid participants are rejected`() {
        listOf("javascript:alert(1)","data:text/html,hi","http://example.test/doc","https://user:password@example.test/doc","//example.test/doc","/admin/users","/storage\\evil","/storage\n").forEach { url ->
            assertThatThrownBy { CalendarEventDetails.validate(listOf(CalendarDocumentLink("Doc",url)),emptyList()) }.isInstanceOf(BadRequestException::class.java)
        }
        assertThatThrownBy { CalendarEventDetails.validate(List(21){CalendarDocumentLink("Doc","https://example.test/doc")},emptyList()) }.isInstanceOf(BadRequestException::class.java)
        assertThatThrownBy { CalendarEventDetails.validate(emptyList(),listOf(CalendarParticipant(email="invalid"))) }.isInstanceOf(BadRequestException::class.java)
        assertThatThrownBy { CalendarEventDetails.validate(emptyList(),List(101){CalendarParticipant("Pat")}) }.isInstanceOf(BadRequestException::class.java)
    }
    @Test fun `Google and Microsoft attendees are bounded and retain their source`() {
        val mapper=jacksonObjectMapper()
        val google=remoteCalendarEvent(mapper.readTree("""{"id":"one","summary":"Review","description":"Notes","start":{"dateTime":"2026-10-24T10:00:00Z"},"end":{"dateTime":"2026-10-24T11:00:00Z"},"attendees":[{"displayName":"Pat","email":"pat@example.test","responseStatus":"accepted"}],"attachments":[{"title":"Brief","fileUrl":"https://example.test/brief"},{"fileUrl":"javascript:bad"}]}"""),"calendar-google",false)!!
        assertThat(google.participants.single().response).isEqualTo("accepted");assertThat(google.documents).hasSize(1);assertThat(google.calendarName).isEqualTo("Google calendar")
        val microsoft=remoteCalendarEvent(mapper.readTree("""{"id":"two","subject":"Review","start":{"dateTime":"2026-10-24T10:00:00Z"},"end":{"dateTime":"2026-10-24T11:00:00Z"},"attendees":[{"emailAddress":{"name":"Sam","address":"sam@example.test"},"status":{"response":"tentativelyAccepted"}}]}"""),"calendar-microsoft",true)!!
        assertThat(microsoft.participants.single().response).isEqualTo("tentative");assertThat(microsoft.calendarName).isEqualTo("Microsoft calendar")
    }
    @Test fun `CalDAV reads attendee and document metadata without executing resources`() {
        val ics="BEGIN:VCALENDAR\r\nBEGIN:VEVENT\r\nUID:one\r\nDTSTART:20261024T100000Z\r\nDTEND:20261024T110000Z\r\nATTENDEE;CN=Pat:mailto:pat@example.test\r\nATTACH:https://example.test/brief.pdf\r\nATTACH:javascript:bad\r\nEND:VEVENT\r\nEND:VCALENDAR\r\n"
        val event=IcalendarCodec.parse(ics,"calendar-caldav").events.single()
        assertThat(event.participants.single().name).isEqualTo("Pat");assertThat(event.documents).hasSize(1);assertThat(event.calendarName).isEqualTo("CalDAV calendar")
    }
}
