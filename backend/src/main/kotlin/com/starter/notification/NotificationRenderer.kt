package com.starter.notification

import com.starter.exception.BadRequestException
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.jsoup.safety.Safelist
import org.springframework.stereotype.Component
import org.springframework.web.util.HtmlUtils

@Component
class NotificationRenderer {
    private val marker=Regex("\\{\\{\\s*([a-z][A-Za-z0-9_.-]{0,79})\\s*}}")
    fun normalize(definition: NotificationDefinition,method: NotificationMethod,input: NotificationTemplate): NotificationTemplate {
        if(input.subject.length>200 || input.text.length>20000 || input.html.length>100000 || input.parameters.size>20 || input.parameters.any {it.length>1000} || input.providerTemplate.length>200) bad("Message template exceeds its limits")
        if(input.subject.any {it.isISOControl()} || input.text.any {it.isISOControl() && it !in "\r\n\t"}) bad("Use printable message content")
        if(input.html.isNotBlank() && !method.supportsHtml) bad("This delivery method does not support HTML")
        if(input.providerTemplate.isNotBlank() && (!method.parameterized || !input.providerTemplate.matches(Regex("[a-z0-9_]{1,200}")))) bad("Use an approved provider template name")
        val safe=input.copy(html=if(input.html.isBlank()) "" else cleanHtml(input.html))
        val parts=listOf(safe.subject,safe.text,safe.html)+safe.parameters
        if(parts.any {marker.findAll(it).any {match -> match.groupValues[1] !in definition.variables}}) bad("Use only the variables declared for this notification")
        if(method.parameterized && safe.parameters.isEmpty() || !method.parameterized && safe.text.isBlank()) bad("Enter a message for this delivery method")
        if(definition.category==NotificationCategory.SECURITY) {
            val bodies=if(method.parameterized) listOf(safe.parameters.joinToString("\n")) else listOf(safe.text)+listOfNotNull(safe.html.takeIf {it.isNotBlank()})
            if(bodies.any {marker.findAll(it).none {match -> match.groupValues[1]=="code"}}) bad("Each verification-code message body must include {{code}}")
        }
        return safe
    }
    fun render(definition: NotificationDefinition,method: NotificationMethod,input: NotificationTemplate,values: Map<String,String>): NotificationPreview {
        val template=normalize(definition,method,input)
        fun fill(source: String,html: Boolean=false)=marker.replace(source) {match ->
            val value=values[match.groupValues[1]] ?: throw BadRequestException("A required notification value was not supplied")
            if(html) HtmlUtils.htmlEscape(value) else value
        }
        return NotificationPreview(fill(template.subject).replace(Regex("[\\r\\n]")," "),fill(template.text),fill(template.html,true),template.parameters.map {fill(it)})
    }
    fun preview(definition: NotificationDefinition,method: NotificationMethod,input: NotificationTemplate): NotificationPreview {
        val samples=definition.variables.associateWith {key -> when(key) {"code" -> "123456";"expiresMinutes" -> "5";"applicationName" -> "Your workspace";"recipientName" -> "Sample member";"subject" -> "Sample notification";"message" -> "This is a preview. No message is sent.";"link" -> "/workflows";"workflowName" -> "Sample workflow";"stepLabel" -> "Review document";"status" -> "IN_PROGRESS";else -> "Example"}}
        return render(definition,method,input,samples)
    }
    fun cleanHtml(source: String): String {
        val parsed=Jsoup.parse(source).outputSettings(Document.OutputSettings().prettyPrint(false))
        if(parsed.allElements.any {element -> element.attributes().any {it.value.contains("{{")}}) bad("HTML variables must be in text content, not attributes")
        val rules=Safelist.relaxed().removeTags("img").addAttributes(":all","style").removeProtocols("a","href","ftp","http").addProtocols("a","href","https","mailto")
        val cleaned=Jsoup.parseBodyFragment(Jsoup.clean(parsed.body().html(),"",rules,Document.OutputSettings().prettyPrint(false)))
        cleaned.select("[style]").forEach {element ->
            val style=element.attr("style").split(';').mapNotNull { declaration ->
                val parts=declaration.split(':',limit=2);if(parts.size!=2) return@mapNotNull null
                val key=parts[0].trim().lowercase();val value=parts[1].trim()
                val valid=when(key) {
                    "color","background-color","border-color" -> value.matches(Regex("#[0-9a-fA-F]{3,8}|[a-zA-Z]{1,20}|rgba?\\([0-9.,% ]{1,60}\\)"))
                    "font-size","width","height","max-width","padding","padding-top","padding-right","padding-bottom","padding-left","margin","line-height","border-width","border-radius" -> value.matches(Regex("(0|[0-9]{1,4}(\\.[0-9]{1,2})?(px|pt|em|rem|%))([ ]+(0|[0-9]{1,4}(px|pt|em|rem|%))){0,3}"))
                    "font-family" -> value.matches(Regex("[a-zA-Z0-9 ,'\"-]{1,100}"))
                    "font-weight" -> value.matches(Regex("normal|bold|[1-9]00"))
                    "font-style" -> value in setOf("normal","italic")
                    "text-align" -> value in setOf("left","center","right","justify")
                    "text-decoration" -> value in setOf("none","underline","line-through")
                    "border-style" -> value in setOf("none","solid","dashed","dotted")
                    "border-collapse" -> value in setOf("collapse","separate")
                    else -> false
                }
                if(valid) "$key:$value" else null
            }.joinToString(";")
            if(style.isBlank()) element.removeAttr("style") else element.attr("style",style)
        }
        return cleaned.outputSettings(Document.OutputSettings().prettyPrint(false)).body().html()
    }
    private fun bad(message: String): Nothing=throw BadRequestException(message)
}
