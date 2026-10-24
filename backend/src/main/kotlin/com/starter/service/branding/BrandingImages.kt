package com.starter.service.branding

import com.starter.exception.BadRequestException
import java.awt.Color
import java.awt.RenderingHints
import java.awt.image.BufferedImage
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.security.MessageDigest
import javax.imageio.IIOImage
import javax.imageio.ImageIO
import javax.imageio.ImageWriteParam

enum class BrandingImageProfile(val maxBytes: Int, val maxDimension: Int, val outputSize: Int) {
    LOGO(1024 * 1024, 2048, 512), FAVICON(1024 * 1024, 2048, 64), LOGIN(5 * 1024 * 1024, 4096, 1920), REPORT_COVER(1024 * 1024, 4096, 1200)
}

object BrandingImages {
    fun version(bytes: ByteArray) = MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }

    fun normalize(bytes: ByteArray, profile: BrandingImageProfile): ByteArray {
        if (bytes.isEmpty() || bytes.size > profile.maxBytes) throw BadRequestException("Choose a PNG, JPEG, or WebP up to ${profile.maxBytes / 1024 / 1024} MiB")
        try {
            ImageIO.createImageInputStream(ByteArrayInputStream(bytes)).use { input ->
                if (input == null) throw BadRequestException("Choose a valid PNG, JPEG, or WebP image")
                val readers = ImageIO.getImageReaders(input)
                if (!readers.hasNext()) throw BadRequestException("Choose a valid PNG, JPEG, or WebP image")
                val reader = readers.next()
                try {
                    if (reader.formatName.lowercase() !in setOf("png", "jpeg", "jpg", "webp")) throw BadRequestException("Only PNG, JPEG, and WebP images are supported")
                    reader.input = input
                    val width = reader.getWidth(0); val height = reader.getHeight(0)
                    if (width !in 1..profile.maxDimension || height !in 1..profile.maxDimension) throw BadRequestException("Image dimensions must be at most ${profile.maxDimension} × ${profile.maxDimension} pixels")
                    val image = reader.read(0)
                    val scale = minOf(1.0, profile.outputSize.toDouble() / maxOf(width, height))
                    val scaledWidth = maxOf(1, (width * scale).toInt()); val scaledHeight = maxOf(1, (height * scale).toInt())
                    val square = profile == BrandingImageProfile.FAVICON
                    val canvasWidth = if (square) profile.outputSize else scaledWidth
                    val canvasHeight = if (square) profile.outputSize else scaledHeight
                    val canvas = BufferedImage(canvasWidth, canvasHeight, if (profile == BrandingImageProfile.LOGIN) BufferedImage.TYPE_INT_RGB else BufferedImage.TYPE_INT_ARGB)
                    val graphics = canvas.createGraphics()
                    try {
                        if (profile == BrandingImageProfile.LOGIN) { graphics.color = Color.WHITE; graphics.fillRect(0, 0, canvasWidth, canvasHeight) }
                        graphics.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC)
                        graphics.drawImage(image, (canvasWidth - scaledWidth) / 2, (canvasHeight - scaledHeight) / 2, scaledWidth, scaledHeight, null)
                    } finally { graphics.dispose() }
                    return encode(canvas, profile)
                } finally { reader.dispose() }
            }
        } catch (error: BadRequestException) { throw error }
        catch (_: Exception) { throw BadRequestException("Unable to read this image; choose a valid PNG, JPEG, or WebP") }
    }

    private fun encode(canvas: BufferedImage, profile: BrandingImageProfile): ByteArray = ByteArrayOutputStream().use { out ->
        if (profile != BrandingImageProfile.LOGIN) ImageIO.write(canvas, "png", out)
        else {
            val writer = ImageIO.getImageWritersByFormatName("jpeg").next()
            try {
                ImageIO.createImageOutputStream(out).use { output ->
                    writer.output = output
                    val parameters = writer.defaultWriteParam.apply { compressionMode = ImageWriteParam.MODE_EXPLICIT; compressionQuality = .85f }
                    writer.write(null, IIOImage(canvas, null, null), parameters)
                }
            } finally { writer.dispose() }
        }
        out.toByteArray()
    }
}
