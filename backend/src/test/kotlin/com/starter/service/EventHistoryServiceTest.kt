package com.starter.service

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.starter.exception.BadRequestException
import com.starter.model.mongo.Event
import org.assertj.core.api.Assertions.*
import org.bson.Document
import org.bson.types.ObjectId
import org.junit.jupiter.api.Test
import org.mockito.ArgumentMatchers.*
import org.mockito.Mockito.*
import org.springframework.data.mongodb.core.MongoTemplate
import org.springframework.data.mongodb.core.convert.MongoConverter
import org.springframework.data.mongodb.core.query.Query
import java.time.Instant
import java.util.Base64
import java.util.Date

class EventHistoryServiceTest {
    private val mongo=mock(MongoTemplate::class.java)
    private val converter=mock(MongoConverter::class.java)
    private val mapper=jacksonObjectMapper()
    private val service=EventHistoryService(mongo,mapper)
    private val time=Date.from(Instant.parse("2026-10-24T10:00:00Z"))
    private val queries=mutableListOf<Query>()
    private var documents=listOf<Document>()
    init {
        `when`(mongo.getCollectionName(Event::class.java)).thenReturn("events")
        `when`(mongo.converter).thenReturn(converter)
        `when`(mongo.find(any(Query::class.java),eq(Document::class.java),eq("events"))).thenAnswer {queries+=it.arguments[0] as Query;documents}
        `when`(converter.read(eq(Event::class.java),any(Document::class.java))).thenAnswer {
            val row=it.arguments[1] as Document
            Event(id=row["_id"].toString(),type="ITEM_CREATED",payload=mapOf("title" to "Example"),createdAt=row.getDate("createdAt").toInstant())
        }
    }
    private fun row(id:Any)=Document("_id",id).append("createdAt",time)
    @Test fun `history requests one lookahead row with stable descending order and no offsets`() {
        documents=listOf(row(ObjectId()),row(ObjectId()),row(ObjectId()))
        val page=service.history(null,2)
        assertThat(page.content).hasSize(2);assertThat(page.nextCursor).isNotBlank()
        assertThat(queries.single().limit).isEqualTo(3);assertThat(queries.single().skip).isZero()
        assertThat(queries.single().sortObject).isEqualTo(Document("createdAt",-1).append("_id",-1))
        assertThat(queries.single().queryObject).isEmpty()
    }
    @Test fun `cursor handles tied timestamps and preserves both ObjectId and legacy string identity types`() {
        listOf(ObjectId(),"legacy-event-id").forEach {id->
            documents=listOf(row(id),row(ObjectId()))
            val next=service.history(null,1).nextCursor!!
            documents=emptyList();service.history(next,1)
            val alternatives=queries.last().queryObject["\$or"] as List<*>
            assertThat((alternatives[0] as Document)["createdAt"]).isEqualTo(Document("\$lt",time))
            val tied=alternatives[1] as Document
            assertThat(tied["createdAt"]).isEqualTo(time)
            assertThat(tied["\$expr"]).isEqualTo(Document("\$lt",listOf("\$_id",Document("\$literal",id))))
            assertThat(queries.last().skip).isZero()
        }
    }
    @Test fun `history ends when no lookahead exists and returns a valid empty state`() {
        documents=listOf(row(ObjectId()))
        assertThat(service.history(null,10).nextCursor).isNull()
        documents=emptyList();val result=service.history(null,10)
        assertThat(result.content).isEmpty();assertThat(result.nextCursor).isNull()
    }
    @Test fun `malformed cursors and invalid sizes are rejected before database access`() {
        listOf(0,101,-1).forEach {assertThatThrownBy {service.history(null,it)}.isInstanceOf(BadRequestException::class.java)}
        val invalid=Base64.getUrlEncoder().encodeToString(mapper.writeValueAsBytes(EventHistoryCursor("invalid-time","x","STRING")))
        listOf("", "!invalid", "x".repeat(2049),invalid).forEach {assertThatThrownBy {service.history(it,50)}.isInstanceOf(BadRequestException::class.java)}
        assertThat(queries).isEmpty()
    }
}
