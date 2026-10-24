package com.starter.notification

import com.starter.exception.BadRequestException
import org.assertj.core.api.Assertions.*
import org.junit.jupiter.api.Test

class NotificationRendererTest {
    private val fixture=NotificationFixture()
    private val definition=fixture.catalogue.get("general.message")
    private val method=fixture.transports.get("email")
    @Test fun `HTML uses a parser safelist and retains safe email formatting without scripts images forms or network CSS`() {
        val html="""<html><head><script>alert(1)</script><style>body{background:url(https://tracker.test)}</style></head><body><table><tr><td onclick="bad()" style="color:#112233;padding:12px;background-image:url(https://tracker.test);position:fixed">{{message}}</td></tr></table><img src="https://tracker.test/pixel"><form><input name="password"></form><iframe src="https://tracker.test"></iframe><a href="javascript:alert(1)">bad</a></body></html>"""
        val rendered=fixture.renderer.render(definition,method,NotificationTemplate(html=html),mapOf("subject" to "Sample","message" to "<img src=x onerror=bad()>"))
        assertThat(rendered.html).contains("<table>","color:#112233","padding:12px","&lt;img")
            .doesNotContain("<script","<style","<img","<form","<input","<iframe","onclick","background-image","position:fixed","javascript:","tracker.test")
    }
    @Test fun `variables remain literal values and cannot inject HTML or recursively expand templates`() {
        val rendered=fixture.renderer.render(definition,method,NotificationTemplate(html="<p>{{message}}</p>"),mapOf("subject" to "Notice","message" to "<b>text</b> {{code}}"))
        assertThat(rendered.html).isEqualTo("<p>&lt;b&gt;text&lt;/b&gt; {{code}}</p>")
        assertThat(rendered.text).isEqualTo("<b>text</b> {{code}}")
        assertThatThrownBy {fixture.renderer.normalize(definition,method,NotificationTemplate(text="{{unknown}}"))}.isInstanceOf(BadRequestException::class.java)
        assertThatThrownBy {fixture.renderer.normalize(definition,method,NotificationTemplate(html="<a href='https://site.test/{{message}}'>Click</a>"))}.isInstanceOf(BadRequestException::class.java)
    }
    @Test fun `both plain and HTML verification messages must contain the code and SMS cannot take HTML`() {
        val otp=fixture.catalogue.get("security.otp")
        assertThatThrownBy {fixture.renderer.normalize(otp,method,NotificationTemplate(text="No code"))}.isInstanceOf(BadRequestException::class.java)
        assertThatThrownBy {fixture.renderer.normalize(otp,method,NotificationTemplate(text="{{code}}",html="<p>Missing code</p>"))}.isInstanceOf(BadRequestException::class.java)
        fixture.renderer.normalize(otp,method,NotificationTemplate(text="Code {{code}}",html="<strong>{{code}}</strong>"))
        assertThatThrownBy {fixture.renderer.normalize(definition,fixture.transports.get(fixture.sms.channel),NotificationTemplate(html="<p>Hi</p>"))}.isInstanceOf(BadRequestException::class.java)
    }
    @Test fun `previews use synthetic codes and parameters preserve their order`() {
        val preview=fixture.renderer.preview(fixture.catalogue.get("security.otp"),method,NotificationTemplate(text="Code {{code}}",html="<p>{{code}}</p>"))
        assertThat(preview.text).isEqualTo("Code 123456")
        val parameterMethod=method.copy(id="whatsapp",supportsHtml=false,parameterized=true)
        val parameters=fixture.renderer.render(definition,parameterMethod,NotificationTemplate(parameters=listOf("{{recipientName}}","{{message}}"),providerTemplate="approved_message"),mapOf("subject" to "Sample","message" to "Body","recipientName" to "Member"))
        assertThat(parameters.parameters).containsExactly("Member","Body")
        assertThat(fixture.emailMessages).isEmpty();assertThat(fixture.sms.sent).isEmpty()
    }
}
