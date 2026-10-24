package com.starter.projects

import com.starter.exception.BadRequestException
import org.assertj.core.api.Assertions.*
import org.junit.jupiter.api.Test
import java.awt.image.BufferedImage
import java.io.ByteArrayOutputStream
import java.util.Base64
import javax.imageio.ImageIO

class ReportCoverImagesTest {
    private fun image(): String {
        val bytes=ByteArrayOutputStream().also {ImageIO.write(BufferedImage(4,3,BufferedImage.TYPE_INT_ARGB),"png",it)}.toByteArray()
        return "data:image/png;base64,"+Base64.getEncoder().encodeToString(bytes)
    }
    @Test fun `images are normalized and names match without case while duplicate and unsafe names fail`() {
        val value=image()
        assertThat(ReportCoverImages.normalize(mapOf("Banner.PNG" to value)).keys).containsExactly("banner.png")
        assertThatThrownBy {ReportCoverImages.normalize(mapOf("Banner.png" to value,"banner.PNG" to value))}.isInstanceOf(BadRequestException::class.java)
        assertThatThrownBy {ReportCoverImages.normalize(mapOf("../banner.png" to value))}.isInstanceOf(BadRequestException::class.java)
        assertThatThrownBy {ReportCoverImages.normalize(mapOf("banner.png" to "https://example.test/a.png"))}.isInstanceOf(BadRequestException::class.java)
        assertThatThrownBy {ReportCoverImages.normalize(mapOf("banner.png" to "data:image/png;base64,c2NyaXB0"))}.isInstanceOf(BadRequestException::class.java)
    }
    @Test fun `image count and encoded size are bounded before image decoding`() {
        assertThatThrownBy {ReportCoverImages.normalize((1..9).associate {"image$it.png" to image()})}.isInstanceOf(BadRequestException::class.java)
        assertThatThrownBy {ReportCoverImages.normalize(mapOf("banner.png" to "x".repeat(1_400_001)))}.isInstanceOf(BadRequestException::class.java)
    }
}
