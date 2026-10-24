package com.starter.email

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.starter.dto.SendEmailRequest
import com.sun.net.httpserver.HttpServer
import jakarta.mail.Session
import jakarta.mail.internet.MimeMessage
import org.assertj.core.api.Assertions.*
import org.junit.jupiter.api.Test
import java.io.ByteArrayInputStream
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.util.Properties
import java.util.concurrent.CompletableFuture
import java.util.concurrent.TimeUnit

class EmailProviderClientTest {
    private val mapper = jacksonObjectMapper()
    private val client = EmailProviderClient(mapper)
    private val message = SendEmailRequest("reader@example.test", "Sample subject", "Sample plain text")
    private fun config(provider: EmailProvider) = EmailConfiguration(true, provider, "sender@example.test", "Sample app", "reply@example.test", apiKey = "private-api-key", mailgunDomain = "mg.example.test")

    @Test fun `API providers send their documented authentication routes and plain text payloads`() {
        data class Captured(val path: String, val auth: String, val contentType: String, val body: String)
        val captured = mutableListOf<Captured>()
        val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        server.createContext("/") { exchange ->
            captured += Captured(exchange.requestURI.path, exchange.requestHeaders.getFirst("Authorization"), exchange.requestHeaders.getFirst("Content-Type"), exchange.requestBody.bufferedReader().readText())
            exchange.sendResponseHeaders(202, -1); exchange.close()
        }
        server.start()
        try {
            listOf(EmailProvider.RESEND, EmailProvider.SENDGRID, EmailProvider.MAILGUN).forEach {
                client.send(config(it).copy(apiBaseUrl = "http://127.0.0.1:${server.address.port}" + if (it == EmailProvider.MAILGUN) "/v3" else ""), message, listOf(EmailAttachment("contract.pdf", "%PDF-protected-sample".toByteArray())))
            }
        } finally { server.stop(0) }
        assertThat(captured.map { it.path }).containsExactly("/emails", "/v3/mail/send", "/v3/mg.example.test/messages")
        assertThat(captured[0].auth).isEqualTo("Bearer private-api-key")
        assertThat(captured[1].auth).isEqualTo("Bearer private-api-key")
        assertThat(captured[2].auth).startsWith("Basic ")
        assertThat(String(java.util.Base64.getDecoder().decode(captured[2].auth.removePrefix("Basic ")))).isEqualTo("api:private-api-key")
        assertThat(mapper.readTree(captured[0].body).path("reply_to").asText()).isEqualTo("reply@example.test")
        val sendgrid = mapper.readTree(captured[1].body)
        assertThat(sendgrid.path("personalizations")[0].path("to")[0].path("email").asText()).isEqualTo(message.recipient)
        assertThat(sendgrid.path("content")[0].path("type").asText()).isEqualTo("text/plain")
        assertThat(captured[2].contentType).startsWith("multipart/form-data; boundary=")
        assertThat(captured[2].body).contains("name=\"h:Reply-To\"", message.text, "Sample app")
        val resendAttachment = mapper.readTree(captured[0].body).path("attachments")[0]
        assertThat(resendAttachment.path("filename").asText()).isEqualTo("contract.pdf")
        assertThat(String(java.util.Base64.getDecoder().decode(resendAttachment.path("content").asText()))).isEqualTo("%PDF-protected-sample")
        val sendgridAttachment = sendgrid.path("attachments")[0]
        assertThat(sendgridAttachment.path("disposition").asText()).isEqualTo("attachment")
        assertThat(sendgridAttachment.path("type").asText()).isEqualTo("application/pdf")
        assertThat(String(java.util.Base64.getDecoder().decode(sendgridAttachment.path("content").asText()))).isEqualTo("%PDF-protected-sample")
        assertThat(captured[2].body).contains("name=\"attachment\"; filename=\"contract.pdf\"", "Content-Type: application/pdf", "%PDF-protected-sample")
    }

    @Test fun `provider rejection bodies are discarded and redirects never receive credentials`() {
        var redirects = 0
        var status = 302
        val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        server.createContext("/emails") { exchange ->
            exchange.responseHeaders.add("Location", "http://127.0.0.1:${server.address.port}/stolen")
            val body = "private-api-key echoed-private-message".toByteArray()
            exchange.sendResponseHeaders(status, body.size.toLong())
            exchange.responseBody.use { it.write(body) }
        }
        server.createContext("/stolen") { exchange -> redirects++; exchange.sendResponseHeaders(200, -1); exchange.close() }
        server.start()
        try {
            assertThatThrownBy { client.send(config(EmailProvider.RESEND).copy(apiBaseUrl = "http://127.0.0.1:${server.address.port}"), message) }
                .isInstanceOf(EmailDeliveryException::class.java).hasMessageContaining("redirected").hasMessageNotContaining("private-api-key").hasMessageNotContaining("echoed-private-message")
            status = 401
            assertThatThrownBy { client.send(config(EmailProvider.RESEND).copy(apiBaseUrl = "http://127.0.0.1:${server.address.port}"), message) }
                .isInstanceOf(EmailDeliveryException::class.java).hasMessageContaining("authentication").hasMessageNotContaining("private-api-key").hasMessageNotContaining("echoed-private-message")
        } finally { server.stop(0) }
        assertThat(redirects).isZero()
    }

    @Test fun `SMTP settings require STARTTLS hostname verification bounded waits and explicit OAuth2 mechanism`() {
        val starttls = client.smtpSender(config(EmailProvider.SMTP)).javaMailProperties
        assertThat(starttls.getProperty("mail.smtp.starttls.required")).isEqualTo("true")
        assertThat(starttls.getProperty("mail.smtp.ssl.checkserveridentity")).isEqualTo("true")
        assertThat(starttls.getProperty("mail.smtp.connectiontimeout")).isEqualTo("10000")
        assertThat(starttls.getProperty("mail.debug")).isEqualTo("false")
        val oauth = client.smtpSender(config(EmailProvider.SMTP).copy(smtpAuth = SmtpAuth.OAUTH2)).javaMailProperties
        assertThat(oauth.getProperty("mail.smtp.auth.mechanisms")).isEqualTo("XOAUTH2")
        val tls = client.smtpSender(config(EmailProvider.SMTP).copy(smtpSecurity = SmtpSecurity.TLS)).javaMailProperties
        assertThat(tls.getProperty("mail.smtp.ssl.enable")).isEqualTo("true")
        assertThat(tls.getProperty("mail.smtp.starttls.enable")).isEqualTo("false")
    }

    @Test fun `local SMTP relay receives a correctly addressed UTF8 message`() {
        ServerSocket(0, 1, java.net.InetAddress.getByName("127.0.0.1")).use { server ->
            val received = CompletableFuture.supplyAsync {
                server.accept().use { socket ->
                    socket.soTimeout = 10000
                    val input = socket.getInputStream().bufferedReader(); val output = socket.getOutputStream().bufferedWriter()
                    fun reply(line: String) { output.write(line + "\r\n"); output.flush() }
                    reply("220 local test SMTP")
                    val mime = StringBuilder()
                    while (true) {
                        val line = input.readLine() ?: break
                        when {
                            line.startsWith("EHLO") -> { reply("250-localhost"); reply("250 8BITMIME") }
                            line.startsWith("MAIL FROM") || line.startsWith("RCPT TO") -> reply("250 OK")
                            line == "DATA" -> { reply("354 Continue"); while (true) { val content = input.readLine() ?: break; if (content == ".") break; mime.append(content).append("\r\n") }; reply("250 Accepted") }
                            line == "QUIT" -> { reply("221 Bye"); break }
                            else -> reply("250 OK")
                        }
                    }
                    mime.toString()
                }
            }
            client.send(config(EmailProvider.SMTP).copy(smtpHost = "127.0.0.1", smtpPort = server.localPort, smtpSecurity = SmtpSecurity.NONE, smtpAuth = SmtpAuth.NONE), message.copy(subject = "Agreement résumé", text = "A café example"), listOf(EmailAttachment("contract.pdf", "%PDF-protected-sample".toByteArray())))
            val mime = MimeMessage(Session.getInstance(Properties()), ByteArrayInputStream(received.get(10, TimeUnit.SECONDS).toByteArray()))
            assertThat(mime.subject).isEqualTo("Agreement résumé")
            fun leaves(part: jakarta.mail.Part): List<jakarta.mail.Part> = if (part.isMimeType("multipart/*")) {
                val content = part.content as jakarta.mail.Multipart; (0 until content.count).flatMap { leaves(content.getBodyPart(it)) }
            } else listOf(part)
            val parts = leaves(mime)
            assertThat(parts.first { it.isMimeType("text/plain") }.content.toString()).contains("A café example")
            val attachment = parts.single { it.disposition == jakarta.mail.Part.ATTACHMENT }
            assertThat(attachment.fileName).isEqualTo("contract.pdf")
            assertThat(attachment.inputStream.readBytes()).isEqualTo("%PDF-protected-sample".toByteArray())
            assertThat(mime.allRecipients.single().toString()).isEqualTo(message.recipient)
            assertThat(mime.replyTo.single().toString()).isEqualTo("reply@example.test")
        }
    }

    @Test fun `a server without STARTTLS never receives authentication credentials`() {
        ServerSocket(0, 1, java.net.InetAddress.getByName("127.0.0.1")).use { server ->
            val received = CompletableFuture.supplyAsync {
                server.accept().use { socket ->
                    socket.soTimeout = 10000
                    val input = socket.getInputStream().bufferedReader(); val output = socket.getOutputStream().bufferedWriter()
                    fun reply(line: String) { output.write(line + "\r\n"); output.flush() }
                    val commands = mutableListOf<String>(); reply("220 local test SMTP")
                    while (true) {
                        val line = input.readLine() ?: break; commands += line
                        if (line == "QUIT") { reply("221 Bye"); break }
                        if (line.startsWith("EHLO")) { reply("250-localhost"); reply("250 AUTH LOGIN PLAIN") } else reply("500 Rejected")
                    }
                    commands
                }
            }
            val config = config(EmailProvider.SMTP).copy(smtpHost = "127.0.0.1", smtpPort = server.localPort, smtpUsername = "private-user", smtpPassword = "private-password")
            assertThatThrownBy { client.send(config, message) }.isInstanceOf(EmailDeliveryException::class.java).hasMessageNotContaining("private-user").hasMessageNotContaining("private-password")
            assertThat(received.get(10, TimeUnit.SECONDS).joinToString(" ")).doesNotContain("AUTH", "private-user", "private-password")
        }
    }
}
