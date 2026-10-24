package com.starter.service

import com.fasterxml.jackson.databind.ObjectMapper
import com.starter.dto.EventResponse
import com.starter.exception.BadRequestException
import com.starter.model.mongo.Event
import org.bson.Document
import org.bson.types.ObjectId
import org.springframework.data.domain.Sort
import org.springframework.data.mongodb.core.MongoTemplate
import org.springframework.data.mongodb.core.query.BasicQuery
import org.springframework.stereotype.Service
import java.time.Instant
import java.util.Base64
import java.util.Date

data class EventHistoryPage(val content:List<EventResponse>,val nextCursor:String?)
internal data class EventHistoryCursor(val createdAt:String,val id:String,val kind:String)

/** Read-only keyset pagination keeps new arrivals from shifting older pages. */
@Service
class EventHistoryService(private val mongo:MongoTemplate,private val json:ObjectMapper) {
    fun history(cursor:String?,size:Int):EventHistoryPage {
        if(size !in 1..100)throw BadRequestException("Choose an event page size between 1 and 100")
        val filter=if(cursor==null)Document() else {
            val (time,id)=decode(cursor)
            // $expr compares BSON IDs in the same order as sorting, including older string IDs.
            Document("\$or",listOf(Document("createdAt",Document("\$lt",time)),
                Document("createdAt",time).append("\$expr",Document("\$lt",listOf("\$_id",Document("\$literal",id))))))
        }
        val query=BasicQuery(filter).with(Sort.by(Sort.Order.desc("createdAt"),Sort.Order.desc("_id"))).limit(size+1)
        val documents=mongo.find(query,Document::class.java,mongo.getCollectionName(Event::class.java))
        val rows=documents.take(size)
        val events=rows.map {mongo.converter.read(Event::class.java,it)}
        val next=if(documents.size>size)encode(events.last(),rows.last()["_id"])else null
        return EventHistoryPage(events.map {EventResponse(it.id,it.type,it.payload,it.createdAt.toString())},next)
    }
    private fun encode(event:Event,id:Any?):String {
        val cursor=when(id) {
            is ObjectId->EventHistoryCursor(event.createdAt.toString(),id.toHexString(),"OBJECT_ID")
            is String->EventHistoryCursor(event.createdAt.toString(),id,"STRING")
            else->throw IllegalStateException("Unsupported stored event identifier")
        }
        return Base64.getUrlEncoder().withoutPadding().encodeToString(json.writeValueAsBytes(cursor))
    }
    private fun decode(value:String):Pair<Date,Any> {
        try {
            require(value.length in 1..2048)
            val cursor=json.readValue(Base64.getUrlDecoder().decode(value),EventHistoryCursor::class.java)
            require(cursor.id.length in 1..512)
            val id=when(cursor.kind) {"OBJECT_ID"->ObjectId(cursor.id);"STRING"->cursor.id;else->throw IllegalArgumentException()}
            return Date.from(Instant.parse(cursor.createdAt)) to id
        } catch (_:Exception) {throw BadRequestException("Invalid event cursor. Refresh the event history.")}
    }
}
