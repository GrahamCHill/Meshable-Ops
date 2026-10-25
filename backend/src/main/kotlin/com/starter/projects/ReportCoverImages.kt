package com.starter.projects

import com.starter.exception.BadRequestException
import com.starter.service.branding.BrandingImages
import com.starter.service.branding.BrandingImageProfile
import java.awt.Color
import java.awt.image.BufferedImage
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.util.Base64
import java.util.Locale
import javax.imageio.ImageIO
import javax.imageio.IIOImage
import javax.imageio.ImageWriteParam

/** Uploaded cover resources stay embedded; no URL or filesystem resolution is performed. */
object ReportCoverImages {
    const val MAX_TOTAL=1024*1024
    private val filename=Regex("[A-Za-z0-9][A-Za-z0-9._ -]{0,119}")
    fun name(value: String): String {
        if(!filename.matches(value) || value!=value.trim() || value in setOf(".",".."))throw BadRequestException("Use image filenames of up to 120 letters, numbers, spaces, dots, underscores or hyphens")
        return value.lowercase(Locale.ROOT)
    }
    fun normalize(images: Map<String,String>,saved: Map<String,String> = emptyMap()): Map<String,String> {
        if(images.size>8 || images.values.sumOf {it.length.toLong()}>1_400_000)throw BadRequestException("Use up to eight cover images totalling at most 1 MiB")
        if(images.keys.map(::name).distinct().size!=images.size)throw BadRequestException("Cover image filenames must be unique, ignoring case")
        var total=0
        return images.map { (key,value)->
            if(!value.matches(Regex("data:image/(png|jpeg|webp);base64,[A-Za-z0-9+/=]+")))throw BadRequestException("Upload PNG, JPEG or WebP cover images")
            val raw=try {Base64.getDecoder().decode(value.substringAfter(','))}catch (_:IllegalArgumentException){throw BadRequestException("Invalid cover image")}
            total+=raw.size
            if(total>MAX_TOTAL)throw BadRequestException("Cover images must total at most 1 MiB")
            val name=name(key)
            name to if(saved[name]==value)value else encode(raw)
        }.toMap().also {result->if(result.values.sumOf {Base64.getDecoder().decode(it.substringAfter(',')).size.toLong()} > MAX_TOTAL)throw BadRequestException("Optimise the cover images to total at most 1 MiB")}
    }
    fun encode(raw: ByteArray): String {
        var bytes=BrandingImages.normalize(raw,BrandingImageProfile.REPORT_COVER)
        var type="png"
        if(bytes.size>262500) {
            val image=ImageIO.read(ByteArrayInputStream(bytes));val canvas=BufferedImage(image.width,image.height,BufferedImage.TYPE_INT_RGB)
            canvas.createGraphics().also {graphics->try {graphics.color=Color.WHITE;graphics.fillRect(0,0,image.width,image.height);graphics.drawImage(image,0,0,null)}finally{graphics.dispose()}}
            bytes=ByteArrayOutputStream().use {out->
                val writer=ImageIO.getImageWritersByFormatName("jpeg").next()
                try {ImageIO.createImageOutputStream(out).use {output->writer.output=output;val parameters=writer.defaultWriteParam.apply {compressionMode=ImageWriteParam.MODE_EXPLICIT;compressionQuality=.8f};writer.write(null,IIOImage(canvas,null,null),parameters)}}finally{writer.dispose()}
                out.toByteArray()
            };type="jpeg"
        }
        if(bytes.size>262500)throw BadRequestException("Optimise this cover image to at most 256 KiB")
        return "data:image/$type;base64,"+Base64.getEncoder().encodeToString(bytes)
    }
}
