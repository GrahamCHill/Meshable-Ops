package com.starter.plugins

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.starter.exception.*
import com.starter.plugins.googledrive.*
import com.starter.plugins.integrations.*
import org.assertj.core.api.Assertions.*
import org.junit.jupiter.api.Test
import org.mockito.Mockito.*

class GoogleDriveTest {
    private val mapper=jacksonObjectMapper()
    private val registry=mock(PluginRegistry::class.java)
    private val config=mock(PluginConfigurationService::class.java)
    private val urls=mutableListOf<String>()
    private var listing="""{"files":[{"id":"file-1","name":"Notes","webViewLink":"https://docs.google.com/document/d/file-1","size":"42"}],"nextPageToken":"next"}"""
    private var parent="root-id"
    private var afterList:()->Unit={}
    private val http=mock(IntegrationHttpClient::class.java) {call->
        if(call.method.name=="request") {
            val url=call.arguments[1] as String;urls+=url
            val body=when {
                url.endsWith("/token")->"""{"access_token":"server-only"}"""
                url.contains("/files/root?")->"""{"id":"root-id","mimeType":"application/vnd.google-apps.folder"}"""
                url.contains("/files/child?")->"""{"id":"child","mimeType":"application/vnd.google-apps.folder","parents":["$parent"]}"""
                url.contains("/files?")->listing.also {afterList()}
                else->"""{"id":"other","mimeType":"application/vnd.google-apps.folder","parents":[]}"""
            }
            IntegrationResponse(200,mapper.readTree(body))
        } else org.mockito.Answers.RETURNS_DEFAULTS.answer(call)
    }
    private val service=GoogleDriveService(registry,config,http)
    private val values=mapOf("clientId" to "client","clientSecret" to "secret","refreshToken" to "refresh","rootFolderId" to "root")
    init {`when`(config.current("google-drive")).thenReturn(values);`when`(registry.missingConfiguration("google-drive",values)).thenReturn(emptyList())}
    @Test fun `browse is bounded to descendants and passes encoded pagination`() {
        val page=service.files("USER","child","page+token")
        assertThat(page.folderId).isEqualTo("child");assertThat(page.rootId).isEqualTo("root-id")
        assertThat(page.items.single().url).startsWith("https://docs.google.com/")
        assertThat(urls.last()).contains("pageToken=page%2Btoken","pageSize=100")
        assertThat(mapper.writeValueAsString(page)).doesNotContain("secret","refresh","server-only")
    }
    @Test fun `outside folders cycles and malicious IDs are rejected before listing`() {
        parent="child"
        assertThatThrownBy {service.files("USER","child",null)}.isInstanceOf(ForbiddenException::class.java)
        assertThat(urls.none {it.contains("/files?")}).isTrue()
        urls.clear()
        assertThatThrownBy {service.files("USER","../escape",null)}.isInstanceOf(BadRequestException::class.java)
        assertThat(urls).isEmpty()
    }
    @Test fun `unsafe links are removed and configuration changes invalidate an in-flight response`() {
        listing="""{"files":[{"id":"x","webViewLink":"https://attacker.test/file"}]}"""
        assertThat(service.files("USER",null,null).items.single().url).isNull()
        afterList={`when`(config.current("google-drive")).thenReturn(values+("rootFolderId" to "changed"))}
        assertThatThrownBy {service.files("USER",null,null)}.isInstanceOf(ConflictException::class.java)
    }
    @Test fun `disabled providers make no outbound requests`() {
        doThrow(NotFoundException("Disabled")).`when`(registry).requireAvailable("google-drive","USER")
        assertThatThrownBy {service.files("USER",null,null)}.isInstanceOf(NotFoundException::class.java)
        assertThat(urls).isEmpty()
    }
}
