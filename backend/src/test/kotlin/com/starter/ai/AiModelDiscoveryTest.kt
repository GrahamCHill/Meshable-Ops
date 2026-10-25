package com.starter.ai

import org.assertj.core.api.Assertions.*
import org.junit.jupiter.api.Test
import org.springframework.http.*
import org.springframework.test.web.client.MockRestServiceServer
import org.springframework.test.web.client.match.MockRestRequestMatchers.*
import org.springframework.test.web.client.response.MockRestResponseCreators.*
import org.springframework.web.client.RestTemplate

class AiModelDiscoveryTest {
    @Test fun `compatible providers list models before a model is selected using their authentication`() {
        for (provider in listOf(AiProvider.OPENAI_COMPATIBLE, AiProvider.OLLAMA, AiProvider.AZURE_OPENAI)) {
            val http = RestTemplate(); val server = MockRestServiceServer.bindTo(http).build()
            val request = server.expect(requestTo("https://provider.test/v1/models")).andExpect(method(HttpMethod.GET))
            if (provider == AiProvider.AZURE_OPENAI) request.andExpect(header("api-key", "private-key"))
            else request.andExpect(header("Authorization", "Bearer private-key"))
            request.andRespond(withSuccess("""{"data":[{"id":"z-model"},{"id":"a-model"},{"id":"a-model"},{"id":""}]}""", MediaType.APPLICATION_JSON))
            val result = AiProviderClient(http).models(AiEndpoint(provider, "https://provider.test/v1", "", "private-key"))
            assertThat(result.models.map { it.id }).containsExactly("a-model", "z-model")
            assertThat(result.truncated).isFalse()
            if (provider == AiProvider.AZURE_OPENAI) assertThat(result.message).contains("deployment name")
            server.verify()
        }
    }
    @Test fun `Anthropic uses authenticated bounded cursor pagination and display names`() {
        val http = RestTemplate(); val server = MockRestServiceServer.bindTo(http).build()
        server.expect(requestTo("https://provider.test/v1/models?limit=100")).andExpect(header("x-api-key", "private-key"))
            .andExpect(header("anthropic-version", "2023-06-01"))
            .andRespond(withSuccess("""{"data":[{"id":"claude-one","display_name":"Claude One"}],"has_more":true,"last_id":"claude-one"}""", MediaType.APPLICATION_JSON))
        server.expect(requestTo("https://provider.test/v1/models?limit=100&after_id=claude-one"))
            .andRespond(withSuccess("""{"data":[{"id":"claude-two","display_name":"Claude Two"}],"has_more":false,"last_id":"claude-two"}""", MediaType.APPLICATION_JSON))
        val result = AiProviderClient(http).models(AiEndpoint(AiProvider.ANTHROPIC, "https://provider.test/v1", "", "private-key"))
        assertThat(result.models.map { it.name }).containsExactly("Claude One", "Claude Two")
        assertThat(result.truncated).isFalse()
        server.verify()
    }
    @Test fun `local Ollama can discover installed models without an API key`() {
        val http = RestTemplate(); val server = MockRestServiceServer.bindTo(http).build()
        server.expect(requestTo("http://localhost:11434/v1/models")).andExpect(headerDoesNotExist("Authorization"))
            .andRespond(withSuccess("""{"data":[{"id":"embedding:latest"}]}""", MediaType.APPLICATION_JSON))
        assertThat(AiProviderClient(http).models(AiEndpoint(AiProvider.OLLAMA, "http://localhost:11434/v1", "")).models.single().id).isEqualTo("embedding:latest")
        server.verify()
    }
    @Test fun `model pagination stops after five requests and reports a partial list`() {
        val http = RestTemplate(); val server = MockRestServiceServer.bindTo(http).build()
        for (page in 1..5) {
            val route = "https://provider.test/v1/models?limit=100" + if (page == 1) "" else "&after_id=model-${page - 1}"
            server.expect(requestTo(route)).andRespond(withSuccess("""{"data":[{"id":"model-$page"}],"has_more":true,"last_id":"model-$page"}""", MediaType.APPLICATION_JSON))
        }
        val result = AiProviderClient(http).models(AiEndpoint(AiProvider.ANTHROPIC, "https://provider.test/v1", "", "private-key"))
        assertThat(result.models).hasSize(5)
        assertThat(result.truncated).isTrue()
        server.verify()
    }
    @Test fun `unsupported discovery and provider response bodies never expose keys`() {
        val http = RestTemplate(); val server = MockRestServiceServer.bindTo(http).build()
        server.expect(anything()).andRespond(withStatus(HttpStatus.NOT_FOUND).body("private-key and provider internals"))
        assertThatThrownBy { AiProviderClient(http).models(AiEndpoint(AiProvider.OPENAI_COMPATIBLE, "https://provider.test/v1", "", "private-key")) }
            .isInstanceOf(AiUnavailableException::class.java).hasMessageContaining("manually").hasMessageNotContaining("private-key").hasMessageNotContaining("provider internals")
        server.verify()
    }
    @Test fun `unsafe model endpoints fail before an outbound request`() {
        val http = RestTemplate(); val server = MockRestServiceServer.bindTo(http).build()
        listOf("https://user:secret@provider.test/v1", "https://provider.test/v1?key=secret", "http://provider.test/v1").forEach { url ->
            assertThatThrownBy { AiProviderClient(http).models(AiEndpoint(AiProvider.OPENAI_COMPATIBLE, url, "", "private-key")) }
                .hasMessageNotContaining("private-key")
        }
        server.verify()
    }
}
