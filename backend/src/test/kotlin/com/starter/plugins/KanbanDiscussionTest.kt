package com.starter.plugins

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.starter.exception.*
import com.starter.plugins.kanban.*
import com.starter.projects.ProjectBoardAccess
import com.starter.repository.postgres.UserRepository
import com.starter.model.postgres.User
import com.starter.notification.NotificationPublisher
import org.springframework.context.ApplicationEventPublisher
import org.assertj.core.api.Assertions.*
import org.junit.jupiter.api.Test
import org.mockito.Mockito.*
import java.util.UUID
import java.util.Optional

class KanbanDiscussionTest {
    private val mapper=jacksonObjectMapper().findAndRegisterModules()
    private val comments=mock(KanbanCommentRepository::class.java);private val boards=mock(KanbanRepository::class.java)
    private val actors=mock(PluginActor::class.java);private val registry=mock(PluginRegistry::class.java)
    private val access=mock(ProjectBoardAccess::class.java);private val users=mock(UserRepository::class.java)
    private val events=mock(ApplicationEventPublisher::class.java)
    private val author=User(email="author@example.test",username="Author")
    private val recipient=User(email="reader@example.test",username="Reader")
    private val column=KanbanColumn(UUID.randomUUID(),"To do")
    private val card=KanbanCard(UUID.randomUUID(),column.id,"Review work",reference="OPS-1")
    private val board=KanbanBoard(ownerId=author.id,name="Delivery",contentJson=mapper.writeValueAsString(KanbanContent(listOf(column),listOf(card))))
    private val service=KanbanCommentService(comments,boards,actors,registry,access,mapper,users,events)
    init {
        `when`(actors.current(author.email)).thenReturn(author)
        `when`(boards.locked(board.id)).thenReturn(Optional.of(board));`when`(boards.findById(board.id)).thenReturn(Optional.of(board))
        `when`(comments.findTop200ByBoardIdAndCardIdOrderByCreatedAtAsc(board.id,card.id)).thenReturn(emptyList())
        `when`(comments.saveAndFlush(any(KanbanComment::class.java))).thenAnswer { it.arguments[0] }
        `when`(users.findById(author.id)).thenReturn(Optional.of(author));`when`(users.findById(recipient.id)).thenReturn(Optional.of(recipient))
    }
    @Test fun `typed mentions identify recipients and replies retain their exact parent`() {
        val parent=KanbanComment(boardId=board.id,cardId=card.id,authorId=author.id,text="Question")
        `when`(comments.findTop200ByBoardIdAndCardIdOrderByCreatedAtAsc(board.id,card.id)).thenReturn(listOf(parent))
        val text="Please review @[Reader](user:${recipient.id})"
        val saved=service.add(author.email,board.id,card.id,KanbanCommentInput(text,parentId=parent.id))
        assertThat(saved.parentId).isEqualTo(parent.id);assertThat(saved.mentionIds).containsExactly(recipient.id)
        verify(access).validatePeople(author,board,setOf(recipient.id))
        val event=mockingDetails(events).invocations.single { it.method.name=="publishEvent" }.arguments[0] as KanbanMentionEvent
        assertThat(event.text).isEqualTo(text);assertThat(event.recipients).containsExactly(recipient.id)
    }
    @Test fun `cross ticket reply targets and unauthorized mentions are rejected before saving`() {
        assertThatThrownBy { service.add(author.email,board.id,card.id,KanbanCommentInput("Reply",parentId=UUID.randomUUID())) }.isInstanceOf(BadRequestException::class.java)
        doThrow(BadRequestException("Unavailable member")).`when`(access).validatePeople(author,board,setOf(recipient.id))
        assertThatThrownBy { service.add(author.email,board.id,card.id,KanbanCommentInput("@[Reader](user:${recipient.id})")) }.isInstanceOf(BadRequestException::class.java)
        verify(comments,never()).saveAndFlush(any(KanbanComment::class.java));verifyNoInteractions(events)
    }
    @Test fun `mention notification contains comment content and exact reply link and rechecks visibility`() {
        val publisher=mock(NotificationPublisher::class.java);val id=UUID.randomUUID()
        `when`(access.readable(recipient,board)).thenReturn(true)
        val listener=KanbanMentions(publisher,users,boards,access,registry,mapper,"https://workspace.example.test")
        listener.notify(KanbanMentionEvent(id,board.id,card.id,author.id,setOf(recipient.id),"@[Reader](user:${recipient.id}) please check the acceptance criteria."))
        val call=mockingDetails(publisher).invocations.single { it.method.name=="publish" }
        @Suppress("UNCHECKED_CAST") val values=call.arguments[2] as Map<String,String>
        assertThat(values.getValue("message")).contains("@Reader please check the acceptance criteria.")
        assertThat(values.getValue("link")).startsWith("https://workspace.example.test/").contains("card=${card.id}","comment=$id","#comment-$id")
        assertThat(call.arguments.last()).isEqualTo(true)
        clearInvocations(publisher);`when`(access.readable(recipient,board)).thenReturn(false)
        listener.notify(KanbanMentionEvent(id,board.id,card.id,author.id,setOf(recipient.id),"Private content"))
        verifyNoInteractions(publisher)
    }
    @Test fun `replies notify the parent author once even when the same author is mentioned`() {
        val publisher=mock(NotificationPublisher::class.java)
        `when`(access.readable(recipient,board)).thenReturn(true)
        val listener=KanbanMentions(publisher,users,boards,access,registry,mapper,"https://workspace.example.test")
        listener.notify(KanbanMentionEvent(UUID.randomUUID(),board.id,card.id,author.id,emptySet(),"A threaded reply",recipient.id))
        assertThat(mockingDetails(publisher).invocations.single { it.method.name=="publish" }.arguments[0]).isEqualTo("kanban.reply")
        clearInvocations(publisher)
        listener.notify(KanbanMentionEvent(UUID.randomUUID(),board.id,card.id,author.id,setOf(recipient.id),"A reply with a mention",recipient.id))
        assertThat(mockingDetails(publisher).invocations.filter { it.method.name=="publish" }).hasSize(1)
    }
}
