package com.starter.projects

import com.starter.exception.*
import com.starter.service.*
import org.assertj.core.api.Assertions.*
import org.junit.jupiter.api.Test
import org.mockito.Mockito.*
import org.springframework.security.core.userdetails.User
import org.springframework.mock.web.MockMultipartFile
import java.util.UUID

class ClientLogoTest {
    private val clients = mock(ProjectService::class.java)
    private val artwork = mock(WorkspaceArtworkService::class.java)
    private val controller = ClientLogoController(clients, artwork)
    private val actor = User("member@example.test", "", emptyList())
    private val id = UUID.randomUUID()
    @Test fun `client membership is checked before reading logo bytes`() {
        doThrow(NotFoundException("Unavailable")).`when`(clients).requireClient(actor.username, id, false)
        assertThatThrownBy { controller.image(id, actor, "version") }.isInstanceOf(NotFoundException::class.java)
        verifyNoInteractions(artwork)
    }
    @Test fun `read-only client members cannot upload or replace logos`() {
        doThrow(ForbiddenException("Read only")).`when`(clients).requireClient(actor.username, id, true)
        val file = MockMultipartFile("file", "logo.webp", "image/webp", byteArrayOf(1))
        assertThatThrownBy { controller.upload(id, actor, -1, file) }.isInstanceOf(ForbiddenException::class.java)
        assertThatThrownBy { controller.preset(id, actor, ArtworkInput("building")) }.isInstanceOf(ForbiddenException::class.java)
        verifyNoInteractions(artwork)
    }
    @Test fun `authorized logo responses are private and cannot be content-sniffed`() {
        `when`(artwork.image("client:$id", "version")).thenReturn(byteArrayOf(1, 2))
        val response = controller.image(id, actor, "version")
        assertThat(response.headers.cacheControl).isEqualTo("no-store")
        assertThat(response.headers.getFirst("X-Content-Type-Options")).isEqualTo("nosniff")
        assertThat(response.headers.contentType.toString()).isEqualTo("image/png")
        verify(clients).requireClient(actor.username, id, false)
    }
}
