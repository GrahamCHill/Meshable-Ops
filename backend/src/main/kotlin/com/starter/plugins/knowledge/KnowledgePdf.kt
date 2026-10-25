package com.starter.plugins.knowledge

import com.starter.exception.BadRequestException
import org.apache.pdfbox.Loader
import org.apache.pdfbox.pdmodel.PDPageContentStream
import org.apache.pdfbox.pdmodel.PageMode
import org.apache.pdfbox.pdmodel.font.PDType1Font
import org.apache.pdfbox.pdmodel.font.Standard14Fonts
import org.apache.pdfbox.pdmodel.interactive.documentnavigation.outline.PDDocumentOutline
import org.apache.pdfbox.pdmodel.interactive.documentnavigation.outline.PDOutlineItem
import java.io.ByteArrayOutputStream
import java.awt.Color

object KnowledgePdf {
    /** Number physical pages, including the cover/index, and provide a destination for every page. */
    fun finish(bytes: ByteArray, title: String): ByteArray {
        if (bytes.size > 10 * 1024 * 1024) throw BadRequestException("The exported book exceeds 10 MiB")
        return Loader.loadPDF(bytes).use { pdf ->
            if (pdf.numberOfPages !in 1..200) throw BadRequestException("Select fewer documents for a book of at most 200 pages")
            val outline = pdf.documentCatalog.documentOutline ?: PDDocumentOutline().also { pdf.documentCatalog.documentOutline = it }
            val pageBookmarks = PDOutlineItem().apply { this.title = "Pages"; setDestination(pdf.getPage(0)) }
            outline.addLast(pageBookmarks)
            val font = PDType1Font(Standard14Fonts.FontName.HELVETICA)
            pdf.pages.forEachIndexed { index, page ->
                val label = "Page ${index + 1} of ${pdf.numberOfPages}"
                PDPageContentStream(pdf, page, PDPageContentStream.AppendMode.APPEND, true, true).use { content ->
                    content.beginText(); content.setFont(font, 9f); content.setNonStrokingColor(Color(70, 87, 103))
                    content.newLineAtOffset((page.mediaBox.width - font.getStringWidth(label) * 9f / 1000) / 2, 18f)
                    content.showText(label); content.endText()
                }
                pageBookmarks.addLast(PDOutlineItem().apply { this.title = "Page ${index + 1}"; setDestination(page) })
            }
            pageBookmarks.closeNode(); outline.openNode(); pdf.documentCatalog.pageMode = PageMode.USE_OUTLINES
            pdf.documentInformation.title = title
            ByteArrayOutputStream().use { output -> pdf.save(output); output.toByteArray() }
        }
    }
}
