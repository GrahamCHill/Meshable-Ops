package com.starter.service

import com.starter.exception.BadRequestException
import org.apache.pdfbox.Loader
import org.apache.pdfbox.pdmodel.PDDocument
import org.apache.pdfbox.pdmodel.PDPage
import org.apache.pdfbox.pdmodel.PDPageContentStream
import org.apache.pdfbox.pdmodel.common.PDRectangle
import org.apache.pdfbox.pdmodel.font.PDType1Font
import org.apache.pdfbox.pdmodel.font.Standard14Fonts
import org.apache.pdfbox.pdmodel.encryption.AccessPermission
import org.apache.pdfbox.pdmodel.encryption.StandardProtectionPolicy
import org.apache.pdfbox.text.PDFTextStripper
import org.assertj.core.api.Assertions.*
import org.junit.jupiter.api.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.nio.file.Files
import java.nio.file.Path
import javax.imageio.ImageIO

class PdfServiceTest {
    private val service = PdfService()

    private fun sample(pages: Int = 2, encrypted: Boolean = false): ByteArray = PDDocument().use { document ->
        repeat(pages) { index ->
            val page = PDPage(PDRectangle.A4)
            if (index == 1) page.rotation = 90
            document.addPage(page)
            PDPageContentStream(document, page).use { stream ->
                stream.beginText(); stream.setFont(PDType1Font(Standard14Fonts.FontName.HELVETICA), 18f)
                stream.newLineAtOffset(60f, 740f); stream.showText("Sample agreement - page ${index + 1}"); stream.endText()
            }
        }
        if (encrypted) document.protect(StandardProtectionPolicy("owner", "", AccessPermission()))
        ByteArrayOutputStream().use { document.save(it); it.toByteArray() }
    }

    @Test fun `every page receives a visible watermark and identity while the original stays intact`() {
        val original = sample()
        val unchanged = original.copyOf()
        val stamped = service.watermark(original, "CONFIDENTIAL", "reader@example.test")
        assertThat(original).isEqualTo(unchanged)
        Loader.loadPDF(stamped).use { document ->
            assertThat(document.numberOfPages).isEqualTo(2)
            repeat(2) { index ->
                val text = PDFTextStripper().apply { startPage = index + 1; endPage = index + 1 }.getText(document)
                // Text extraction splits rotated/diagonal glyph runs; verify their content independently of layout.
                assertThat(text.replace(Regex("\\s+"), "")).contains("CONFIDENTIAL", "reader@example.test", "UTC", "Sampleagreement")
            }
        }
        Loader.loadPDF(original).use { assertThat(PDFTextStripper().getText(it)).doesNotContain("CONFIDENTIAL") }
        val output = Path.of("target", "pdf-qa"); Files.createDirectories(output)
        Files.write(output.resolve("sample.pdf"), original)
        Files.write(output.resolve("watermarked.pdf"), stamped)
        for (page in 1..2) Files.write(output.resolve("watermarked-page-$page.png"), service.page(stamped, page))
    }

    @Test fun `preview produces bounded raster pages with no original PDF payload`() {
        val bytes = sample()
        assertThat(service.inspect(bytes, "agreement.pdf").pageCount).isEqualTo(2)
        val page = service.page(bytes, 2)
        val image = ImageIO.read(ByteArrayInputStream(page))
        assertThat(image.width.toLong() * image.height).isLessThanOrEqualTo(4_320_000)
        assertThat(page.take(4)).containsExactly(0x89.toByte(), 0x50.toByte(), 0x4e.toByte(), 0x47.toByte())
        assertThatThrownBy { service.page(bytes, 0) }.isInstanceOf(BadRequestException::class.java)
        assertThatThrownBy { service.page(bytes, 3) }.isInstanceOf(BadRequestException::class.java)
    }

    @Test fun `encrypted damaged oversized and excessive page documents fail closed`() {
        listOf("damaged".toByteArray(), sample(encrypted = true), ByteArray(PdfService.MAX_BYTES + 1), sample(201)).forEach { bytes ->
            assertThatThrownBy { service.watermark(bytes, "CONFIDENTIAL", "reader@example.test") }.isInstanceOf(BadRequestException::class.java)
            assertThatThrownBy { service.inspect(bytes, "agreement.pdf") }.isInstanceOf(BadRequestException::class.java)
        }
    }
}
