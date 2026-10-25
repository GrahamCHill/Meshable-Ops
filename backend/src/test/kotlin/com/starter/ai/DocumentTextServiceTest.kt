package com.starter.ai

import org.assertj.core.api.Assertions.*
import org.junit.jupiter.api.Test
import org.springframework.http.HttpMethod
import org.springframework.http.MediaType
import org.springframework.test.web.client.MockRestServiceServer
import org.springframework.test.web.client.match.MockRestRequestMatchers.*
import org.springframework.test.web.client.response.MockRestResponseCreators.*
import org.springframework.web.client.RestTemplate

class DocumentTextServiceTest {
    @Test fun `unconfigured scans require OCR without contacting any service`() {
        val http = RestTemplate()
        val server = MockRestServiceServer.createServer(http)
        val service = DocumentTextService(http, "http://native", 52428800)
        assertThat(service.extract("scan.png", byteArrayOf(1)).ocrRequired).isTrue()
        server.verify()
    }

    @Test fun `existing PDF text layers retain page numbers and avoid external OCR`() {
        val http = RestTemplate()
        val server = MockRestServiceServer.createServer(http)
        server.expect(requestTo("http://native/api/v1/ml/extract")).andExpect(method(HttpMethod.POST))
            .andRespond(withSuccess("""{"pages":[{"pageNumber":3,"text":"Renewal notice"}],"needsOcr":false}""", MediaType.APPLICATION_JSON))
        val service = DocumentTextService(http, "http://native", 52428800, "http://ocr", "secret")
        val result = service.extract("contract.pdf", byteArrayOf(1))
        assertThat(result.pages.single().pageNumber).isEqualTo(3)
        assertThat(result.ocrRequired).isFalse()
        server.verify()
    }

    @Test fun `external adapter receives original bytes and its bearer credential only`() {
        val http = RestTemplate()
        val server = MockRestServiceServer.createServer(http)
        server.expect(requestTo("http://ocr")).andExpect(header("Authorization", "Bearer private-token"))
            .andExpect(content().json("""{"filename":"scan.tiff","content":"AQID"}"""))
            .andRespond(withSuccess("""{"engine":"paddle-adapter","pages":[{"pageNumber":1,"text":"Invoice total","confidence":98.4}]}""", MediaType.APPLICATION_JSON))
        val result = DocumentTextService(http, "http://native", 52428800, "http://ocr", "private-token").extract("scan.tiff", byteArrayOf(1,2,3))
        assertThat(result.engine).isEqualTo("paddle-adapter")
        assertThat(result.pages.single().method).isEqualTo("OCR")
        assertThat(result.pages.single().confidence).isEqualTo(98.4)
        server.verify()
    }

    @Test fun `invalid or missing OCR pages are retryable failures`() {
        listOf("""{"text":"missing provenance"}""",
            """{"pages":[{"pageNumber":1,"text":"a"},{"pageNumber":1,"text":"b"}]}""",
            """{"pages":[{"pageNumber":1,"text":"a","confidence":101}]}""").forEach { response ->
            val http = RestTemplate()
            val server = MockRestServiceServer.createServer(http)
            server.expect(requestTo("http://ocr")).andRespond(withSuccess(response, MediaType.APPLICATION_JSON))
            assertThatThrownBy { DocumentTextService(http, "http://native", 52428800, "http://ocr").extract("scan.png", byteArrayOf(1)) }
                .isInstanceOf(AiUnavailableException::class.java)
            server.verify()
        }
    }
}
