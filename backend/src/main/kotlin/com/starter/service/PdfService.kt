package com.starter.service

import com.starter.dto.PdfPreviewResponse
import com.starter.exception.BadRequestException
import org.apache.pdfbox.Loader
import org.apache.pdfbox.pdmodel.PDDocument
import org.apache.pdfbox.pdmodel.PDPageContentStream
import org.apache.pdfbox.pdmodel.font.PDType0Font
import org.apache.pdfbox.pdmodel.graphics.state.PDExtendedGraphicsState
import org.apache.pdfbox.rendering.ImageType
import org.apache.pdfbox.rendering.PDFRenderer
import org.apache.pdfbox.util.Matrix
import org.springframework.stereotype.Service
import java.io.ByteArrayOutputStream
import java.time.Instant
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.concurrent.Semaphore
import javax.imageio.ImageIO
import kotlin.math.min

/** Reads bounded copies only; originals in object storage are never modified. */
@Service
class PdfService {
    companion object { const val MAX_BYTES = 25 * 1024 * 1024; const val MAX_PAGES = 200 }
    private val workers = Semaphore(2)

    fun inspect(bytes: ByteArray, filename: String): PdfPreviewResponse = process(bytes) { PdfPreviewResponse(filename, it.numberOfPages) }

    fun page(bytes: ByteArray, page: Int, watermark: String? = null, actor: String = ""): ByteArray = process(bytes) { document ->
        if (page !in 1..document.numberOfPages) throw BadRequestException("Invalid PDF page")
        if (watermark != null) stamp(document, watermark, actor)
        val box = document.getPage(page - 1).cropBox
        val scale = min(1.5f, min(1800f / box.width, 2400f / box.height))
        val image = PDFRenderer(document).apply { isSubsamplingAllowed = true }.renderImage(page - 1, scale, ImageType.RGB)
        ByteArrayOutputStream().use { output -> ImageIO.write(image, "png", output); output.toByteArray() }
    }

    fun watermark(bytes: ByteArray, text: String, actor: String): ByteArray = process(bytes) { document ->
        if (!document.currentAccessPermission.canModify()) throw BadRequestException("This PDF does not allow watermarking")
        stamp(document, text, actor)
        ByteArrayOutputStream().use { output -> document.save(output); output.toByteArray() }
    }

    fun watermarkAndProtect(bytes: ByteArray, text: String, actor: String, password: String): ByteArray = process(bytes) { document ->
        if (!document.currentAccessPermission.canModify()) throw BadRequestException("This PDF does not allow watermarking")
        stamp(document, text, actor)
        protect(document, password)
        ByteArrayOutputStream().use { output -> document.save(output); output.toByteArray() }
    }

    fun protect(bytes: ByteArray, password: String): ByteArray = process(bytes) { document ->
        if (!document.currentAccessPermission.canModify()) throw BadRequestException("This PDF does not allow protection changes")
        protect(document, password)
        ByteArrayOutputStream().use { output -> document.save(output); output.toByteArray() }
    }

    private fun protect(document: PDDocument, password: String) {
        val permissions = org.apache.pdfbox.pdmodel.encryption.AccessPermission().apply {
            setCanModify(false); setCanModifyAnnotations(false); setCanExtractContent(false); setCanAssembleDocument(false)
        }
        document.protect(org.apache.pdfbox.pdmodel.encryption.StandardProtectionPolicy(java.util.UUID.randomUUID().toString() + java.util.UUID.randomUUID(), password, permissions).apply {
            encryptionKeyLength = 256; isPreferAES = true
        })
    }

    private fun <T> process(bytes: ByteArray, action: (PDDocument) -> T): T {
        if (bytes.size > MAX_BYTES) throw BadRequestException("PDFs must be 25 MB or smaller")
        if (!workers.tryAcquire()) throw BadRequestException("PDF processing is busy. Try again shortly.")
        try {
            Loader.loadPDF(bytes).use { document ->
                if (document.isEncrypted) throw BadRequestException("Encrypted PDFs cannot be previewed or watermarked")
                if (document.numberOfPages !in 1..MAX_PAGES) throw BadRequestException("PDFs must contain between 1 and 200 pages")
                document.pages.forEach { page ->
                    val box = page.cropBox
                    if (!box.width.isFinite() || !box.height.isFinite() || box.width !in 1f..14400f || box.height !in 1f..14400f)
                        throw BadRequestException("Unsupported PDF page dimensions")
                }
                return action(document)
            }
        } catch (failure: BadRequestException) { throw failure }
          catch (_: Exception) { throw BadRequestException("This PDF could not be processed. It may be damaged, encrypted, or unsupported.") }
        finally { workers.release() }
    }

    private fun stamp(document: PDDocument, text: String, actor: String) {
        val resource = "/org/apache/pdfbox/resources/ttf/LiberationSans-Regular.ttf"
        val font = requireNotNull(PdfService::class.java.getResourceAsStream(resource)).use { PDType0Font.load(document, it) }
        fun printable(value: String) = value.map { character ->
            if (character.isISOControl()) ' ' else try { font.encode(character.toString()); character } catch (_: IllegalArgumentException) { '?' }
        }.joinToString("")
        val label = printable(text)
        val timestamp = DateTimeFormatter.ofPattern("uuuu-MM-dd HH:mm 'UTC'").withZone(ZoneOffset.UTC).format(Instant.now())
        val identity = printable("$actor | $timestamp")
        document.pages.forEach { page ->
            val box = page.cropBox
            val rotation = ((page.rotation % 360) + 360) % 360
            val width = if (rotation == 90 || rotation == 270) box.height else box.width
            val height = if (rotation == 90 || rotation == 270) box.width else box.height
            val orientation = when (rotation) {
                90 -> Matrix(0f, 1f, -1f, 0f, box.lowerLeftX + box.width, box.lowerLeftY)
                180 -> Matrix(-1f, 0f, 0f, -1f, box.lowerLeftX + box.width, box.lowerLeftY + box.height)
                270 -> Matrix(0f, -1f, 1f, 0f, box.lowerLeftX, box.lowerLeftY + box.height)
                else -> Matrix.getTranslateInstance(box.lowerLeftX, box.lowerLeftY)
            }
            PDPageContentStream(document, page, PDPageContentStream.AppendMode.APPEND, true, true).use { stream ->
                stream.saveGraphicsState()
                stream.transform(orientation)
                stream.saveGraphicsState()
                stream.setGraphicsStateParameters(PDExtendedGraphicsState().apply { nonStrokingAlphaConstant = 0.22f })
                stream.setNonStrokingColor(0.275f, 0.275f, 0.275f)
                val textWidth = font.getStringWidth(label) / 1000f
                val size = min(44f, width * 0.8f / textWidth.coerceAtLeast(1f))
                stream.beginText()
                stream.setFont(font, size)
                stream.setTextMatrix(Matrix.getRotateInstance(Math.PI / 6, width * 0.12f, height * 0.35f))
                stream.showText(label)
                stream.endText()
                stream.restoreGraphicsState()
                stream.saveGraphicsState()
                stream.setNonStrokingColor(0.314f, 0.314f, 0.314f)
                stream.beginText()
                stream.setFont(font, min(9f, (width - 16).coerceAtLeast(1f) / (font.getStringWidth(identity) / 1000f).coerceAtLeast(1f)))
                stream.newLineAtOffset(8f, 12f)
                stream.showText(identity)
                stream.endText()
                stream.restoreGraphicsState()
                stream.restoreGraphicsState()
            }
        }
    }
}
