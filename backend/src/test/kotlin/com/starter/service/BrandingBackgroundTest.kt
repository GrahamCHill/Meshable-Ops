package com.starter.service

import com.starter.dto.LoginImageView
import com.starter.exception.*
import com.starter.model.postgres.BrandingLoginImage
import com.starter.repository.postgres.BrandingLoginImageRepository
import com.starter.service.branding.BrandingBackgroundService
import org.assertj.core.api.Assertions.*
import org.junit.jupiter.api.Test
import org.mockito.ArgumentMatchers.any
import org.mockito.ArgumentMatchers.anyInt
import org.mockito.Mockito.*
import org.springframework.mock.web.MockMultipartFile
import java.awt.Color
import java.awt.image.BufferedImage
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.util.Optional
import javax.imageio.ImageIO

class BrandingBackgroundTest {
    private val repository = mock(BrandingLoginImageRepository::class.java)
    private val saved = mutableMapOf<Int, BrandingLoginImage>()
    private val service = BrandingBackgroundService(repository)
    init {
        `when`(repository.findById(anyInt())).thenAnswer { Optional.ofNullable(saved[it.getArgument(0)]) }
        `when`(repository.saveAndFlush(any(BrandingLoginImage::class.java))).thenAnswer { call -> call.getArgument<BrandingLoginImage>(0).also { saved[it.slot!!] = it } }
        `when`(repository.versions()).thenAnswer { saved.values.sortedBy { it.slot }.map { LoginImageView(it.slot!!, it.version) } }
        doAnswer { saved.remove(it.getArgument(0)); null }.`when`(repository).deleteById(anyInt())
    }
    private fun image(width: Int = 2048, height: Int = 1024, color: Color = Color.BLUE): ByteArray {
        val image = BufferedImage(width, height, BufferedImage.TYPE_INT_RGB)
        image.createGraphics().apply { this.color = color; fillRect(0, 0, width, height); dispose() }
        return ByteArrayOutputStream().use { ImageIO.write(image, "png", it); it.toByteArray() }
    }
    private fun file(bytes: ByteArray) = MockMultipartFile("file", "untrusted.svg", "text/html", bytes)
    @Test fun `backgrounds become bounded JPEG images with versioned metadata only`() {
        service.upload(4, file(image() + "private metadata".toByteArray()))
        val metadata = service.list().single()
        val decoded = ImageIO.read(ByteArrayInputStream(service.image(4, metadata.version)))
        assertThat(decoded.width).isEqualTo(1920); assertThat(decoded.height).isEqualTo(960)
        assertThat(saved[4]!!.jpeg.take(2)).containsExactly(0xff.toByte(), 0xd8.toByte())
        assertThat(String(saved[4]!!.jpeg)).doesNotContain("private metadata")
        service.upload(0, file(image(10, 10)))
        assertThat(service.list().map { it.slot }).containsExactly(0, 4)
    }
    @Test fun `invalid slots oversized files spoofed images and excessive dimensions are rejected`() {
        listOf(-1, 5).forEach { slot -> assertThatThrownBy { service.upload(slot, file(image(2, 2))) }.isInstanceOf(BadRequestException::class.java) }
        listOf(ByteArray(0), ByteArray(5 * 1024 * 1024 + 1), "<svg/>".toByteArray(), image(4097, 1)).forEach { bytes ->
            assertThatThrownBy { service.upload(0, file(bytes)) }.isInstanceOf(BadRequestException::class.java)
        }
        assertThat(saved).isEmpty()
    }
    @Test fun `replacing or removing an image invalidates its old version`() {
        service.upload(0, file(image(2, 2)))
        val first = service.list().single()
        service.upload(0, file(image(2, 2, Color.RED)))
        assertThatThrownBy { service.image(0, first.version) }.isInstanceOf(NotFoundException::class.java)
        val last = service.list().single()
        service.remove(0)
        assertThatThrownBy { service.image(0, last.version) }.isInstanceOf(NotFoundException::class.java)
    }
}
