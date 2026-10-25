package com.starter.ai

import org.assertj.core.api.Assertions.*
import org.junit.jupiter.api.Test
import org.springframework.http.*
import org.springframework.test.web.client.MockRestServiceServer
import org.springframework.test.web.client.match.MockRestRequestMatchers.*
import org.springframework.test.web.client.response.MockRestResponseCreators.*
import org.springframework.web.client.RestTemplate

class AiProviderClientTest {
    @Test fun `compatible Ollama and Azure use their configured routes and authentication`() {
        for (provider in listOf(AiProvider.OPENAI_COMPATIBLE, AiProvider.OLLAMA, AiProvider.AZURE_OPENAI)) {
            val http = RestTemplate(); val server = MockRestServiceServer.bindTo(http).build()
            val expectation = server.expect(requestTo("https://provider.test/v1/chat/completions")).andExpect(method(HttpMethod.POST))
                .andExpect(jsonPath("$.model").value("deployment"))
            if (provider == AiProvider.AZURE_OPENAI) expectation.andExpect(header("api-key", "private-key"))
            else expectation.andExpect(header("Authorization", "Bearer private-key"))
            expectation.andRespond(withSuccess("""{"choices":[{"message":{"content":"Answer [1]"}}]}""", MediaType.APPLICATION_JSON))
            assertThat(AiProviderClient(http).answer(AiEndpoint(provider, "https://provider.test/v1", "deployment", "private-key"), "instructions", "question")).isEqualTo("Answer [1]")
            server.verify()
        }
    }
    @Test fun `Anthropic uses Messages rather than a Chat Completions request`() {
        val http = RestTemplate(); val server = MockRestServiceServer.bindTo(http).build()
        server.expect(requestTo("https://provider.test/v1/messages")).andExpect(header("x-api-key", "private-key"))
            .andExpect(header("anthropic-version", "2023-06-01")).andExpect(jsonPath("$.system").value("instructions"))
            .andRespond(withSuccess("""{"content":[{"type":"text","text":"Grounded answer"}]}""", MediaType.APPLICATION_JSON))
        assertThat(AiProviderClient(http).answer(AiEndpoint(AiProvider.ANTHROPIC, "https://provider.test/v1", "model", "private-key"), "instructions", "question")).isEqualTo("Grounded answer")
        server.verify()
    }
    @Test fun `embedding responses are ordered by index and requests carry the fixed dimension`() {
        val http = RestTemplate(); val server = MockRestServiceServer.bindTo(http).build()
        val first = List(EmbeddingDimensions.SIZE) { if (it == 0) 1f else 0f }
        val second = List(EmbeddingDimensions.SIZE) { if (it == 1) 1f else 0f }
        server.expect(requestTo("https://provider.test/v1/embeddings")).andExpect(jsonPath("$.dimensions").value(1536))
            .andRespond(withSuccess("""{"data":[{"index":1,"embedding":$second},{"index":0,"embedding":$first}]}""", MediaType.APPLICATION_JSON))
        assertThat(AiProviderClient(http).embed(AiEndpoint(AiProvider.OPENAI_COMPATIBLE, "https://provider.test/v1", "model"), listOf("first", "second"), EmbeddingDimensions.SIZE)).containsExactly(first, second)
        server.verify()
    }
    @Test fun `models with native dimensions can omit the dimensions request parameter`() {
        val http = RestTemplate(); val server = MockRestServiceServer.bindTo(http).build()
        val vector = List(EmbeddingDimensions.SIZE) { if (it == 0) 1f else 0f }
        server.expect(requestTo("https://provider.test/v1/embeddings")).andExpect(jsonPath("$.dimensions").doesNotExist())
            .andRespond(withSuccess("""{"data":[{"index":0,"embedding":$vector}]}""", MediaType.APPLICATION_JSON))
        assertThat(AiProviderClient(http).embed(AiEndpoint(AiProvider.OLLAMA, "https://provider.test/v1", "model", sendDimensions = false), listOf("text"), EmbeddingDimensions.SIZE)).containsExactly(vector)
        server.verify()
    }
    @Test fun `provider errors and unsafe URLs cannot disclose credentials`() {
        val http = RestTemplate(); val server = MockRestServiceServer.bindTo(http).build()
        server.expect(anything()).andRespond(withStatus(HttpStatus.UNAUTHORIZED).body("private-key and private document text"))
        assertThatThrownBy { AiProviderClient(http).answer(AiEndpoint(AiProvider.OPENAI_COMPATIBLE, "https://provider.test/v1", "model", "private-key"), "", "") }
            .isInstanceOf(AiUnavailableException::class.java).hasMessageNotContaining("private-key").hasMessageNotContaining("private document")
        listOf("https://user:secret@provider.test/v1", "https://provider.test/v1?api-key=secret", "http://provider.test/v1").forEach { url ->
            assertThatThrownBy { validateEndpoint(AiEndpoint(AiProvider.OPENAI_COMPATIBLE, url, "model", "private-key")) }.hasMessageNotContaining("private-key")
        }
    }
}
