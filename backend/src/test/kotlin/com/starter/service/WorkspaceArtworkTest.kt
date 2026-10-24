package com.starter.service

import com.starter.exception.*
import com.starter.model.postgres.WorkspaceArtwork
import com.starter.repository.postgres.WorkspaceArtworkRepository
import org.assertj.core.api.Assertions.*
import org.junit.jupiter.api.Test
import org.mockito.Mockito.*
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.mock.web.MockMultipartFile
import java.awt.image.BufferedImage
import java.io.ByteArrayOutputStream
import java.util.Optional
import javax.imageio.ImageIO

class WorkspaceArtworkTest {
    private val rows=mock(WorkspaceArtworkRepository::class.java)
    private val service=WorkspaceArtworkService(rows,mock(JdbcTemplate::class.java))
    init {`when`(rows.findById("project:x")).thenReturn(Optional.empty());`when`(rows.saveAndFlush(org.mockito.ArgumentMatchers.any(WorkspaceArtwork::class.java))).thenAnswer {it.arguments[0]}}
    @Test fun `raster uploads are decoded and normalized instead of trusting filename or content type`() {
        val data=ByteArrayOutputStream().also {ImageIO.write(BufferedImage(120,90,BufferedImage.TYPE_INT_RGB),"png",it)}.toByteArray()
        val result=service.upload("project:x",-1,MockMultipartFile("file","icon.webp","image/webp",data))
        assertThat(result.imageVersion).isNotBlank()
        assertThatThrownBy {service.upload("project:x",-1,MockMultipartFile("file","image.webp","image/webp","<svg/>".toByteArray()))}.isInstanceOf(BadRequestException::class.java)
    }
    @Test fun `unknown presets stale revisions and unavailable image versions are rejected`() {
        assertThatThrownBy {service.preset("project:x",ArtworkInput("script"))}.isInstanceOf(BadRequestException::class.java)
        `when`(rows.findById("project:x")).thenReturn(Optional.of(WorkspaceArtwork("project:x",revision=2)))
        assertThatThrownBy {service.preset("project:x",ArtworkInput("code",1))}.isInstanceOf(ConflictException::class.java)
        assertThatThrownBy {service.image("project:x","missing")}.isInstanceOf(NotFoundException::class.java)
    }
    @Test fun `choosing a preset removes prior uploaded bytes`() {
        val row=WorkspaceArtwork("project:x",image=byteArrayOf(1),imageVersion="old",revision=3)
        `when`(rows.findById("project:x")).thenReturn(Optional.of(row))
        assertThat(service.preset("project:x",ArtworkInput("code",3)).icon).isEqualTo("code")
        assertThat(row.image).isNull();assertThat(row.imageVersion).isNull()
    }
}
