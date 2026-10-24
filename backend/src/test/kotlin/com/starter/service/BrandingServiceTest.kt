package com.starter.service

import com.starter.dto.BrandingVersion
import com.starter.exception.BadRequestException
import com.starter.exception.NotFoundException
import com.starter.model.postgres.BrandingAsset
import com.starter.model.postgres.BrandingKind
import com.starter.repository.postgres.BrandingAssetRepository
import org.assertj.core.api.Assertions.*
import org.junit.jupiter.api.Test
import org.mockito.ArgumentMatchers.any
import org.mockito.Mockito.*
import org.springframework.mock.web.MockMultipartFile
import java.awt.image.BufferedImage
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.util.Optional
import java.util.Base64
import javax.imageio.ImageIO

class BrandingServiceTest {
    private val repository = mock(BrandingAssetRepository::class.java)
    private val saved = mutableMapOf<BrandingKind, BrandingAsset>()
    private val service = BrandingService(repository)
    init {
        `when`(repository.findById(any(BrandingKind::class.java))).thenAnswer { Optional.ofNullable(saved[it.getArgument(0)]) }
        `when`(repository.saveAndFlush(any(BrandingAsset::class.java))).thenAnswer { call -> call.getArgument<BrandingAsset>(0).also { saved[it.kind] = it } }
        `when`(repository.versions()).thenAnswer { saved.values.map { BrandingVersion(it.kind, it.version) } }
        doAnswer { saved.remove(it.getArgument(0)); null }.`when`(repository).deleteById(any(BrandingKind::class.java))
    }
    private fun image(width: Int = 100, height: Int = 50): ByteArray = ByteArrayOutputStream().use {
        ImageIO.write(BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB), "png", it); it.toByteArray()
    }
    private fun upload(bytes: ByteArray) = MockMultipartFile("file", "ignored.svg", "text/html", bytes)

    @Test fun `untrusted names and content types do not bypass raster validation`() {
        listOf("<svg xmlns='http://www.w3.org/2000/svg'/>".toByteArray(), "<html>script</html>".toByteArray(), ByteArray(0), ByteArray(1024 * 1024 + 1)).forEach {
            assertThatThrownBy { service.upload(BrandingKind.LOGO, upload(it)) }.isInstanceOf(BadRequestException::class.java)
        }
        assertThat(saved).isEmpty()
        assertThatThrownBy { service.upload(BrandingKind.LOGO, upload(image(2049, 1))) }.isInstanceOf(BadRequestException::class.java)
    }
    @Test fun `uploads become bounded PNGs and favicon uses square transparent padding`() {
        val source = image(1024, 512) + "untrusted trailing metadata".toByteArray()
        val view = service.upload(BrandingKind.LOGO, upload(source))
        val png = service.asset(BrandingKind.LOGO, view.logoVersion!!)
        val decoded = ImageIO.read(ByteArrayInputStream(png))
        assertThat(decoded.width).isEqualTo(512); assertThat(decoded.height).isEqualTo(256)
        assertThat(png.takeLast(12).toByteArray()).isEqualTo(byteArrayOf(0, 0, 0, 0, 73, 69, 78, 68, -82, 66, 96, -126))
        val favicon = service.upload(BrandingKind.FAVICON, upload(source))
        val icon = ImageIO.read(ByteArrayInputStream(service.asset(BrandingKind.FAVICON, favicon.faviconVersion!!)))
        assertThat(icon.width).isEqualTo(64); assertThat(icon.height).isEqualTo(64)
        assertThat(icon.getRGB(0, 0)).isZero()
    }
    @Test fun `replacement invalidates old version and reset preserves other asset`() {
        val first = service.upload(BrandingKind.LOGO, upload(image()))
        service.upload(BrandingKind.FAVICON, upload(image()))
        val replacement = service.upload(BrandingKind.LOGO, upload(image(10, 10)))
        assertThat(replacement.logoVersion).isNotEqualTo(first.logoVersion)
        assertThatThrownBy { service.asset(BrandingKind.LOGO, first.logoVersion!!) }.isInstanceOf(NotFoundException::class.java)
        val reset = service.reset(BrandingKind.LOGO)
        assertThat(reset.logoVersion).isNull(); assertThat(reset.faviconVersion).isNotNull()
    }

    @Test fun `lossless and lossy WebP decode into PNG preserving color and transparency`() {
        // Synthetic 64x32 solid-color fixtures, generated with libwebp via Pillow.
        val fixtures = listOf(
            "UklGRiQAAABXRUJQVlA4TBcAAAAvP8AHEAdQuJZ0rLQBICH8P69G9D/1AQA=" to 180,
            "UklGRkQAAABXRUJQVlA4IDgAAAAQAwCdASpAACAAPm02l0ikIyIhJAgAgA2JZwAAI4zy1dAA/u7/kUIT//4Vr+tD+3nKuDBGEAAAAA==" to 255,
        )
        fixtures.forEach { (encoded, alpha) ->
            val view = service.upload(BrandingKind.LOGO, upload(Base64.getDecoder().decode(encoded)))
            val decoded = ImageIO.read(ByteArrayInputStream(service.asset(BrandingKind.LOGO, view.logoVersion!!)))
            assertThat(decoded.width).isEqualTo(64); assertThat(decoded.height).isEqualTo(32)
            val color = java.awt.Color(decoded.getRGB(32, 16), true)
            assertThat(color.alpha).isEqualTo(alpha)
            assertThat(color.red).isBetween(34, 40); assertThat(color.green).isBetween(109, 115); assertThat(color.blue).isBetween(96, 102)
        }
    }
}
