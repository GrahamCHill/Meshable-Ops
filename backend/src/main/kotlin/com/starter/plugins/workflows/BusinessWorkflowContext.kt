package com.starter.plugins.workflows

import com.fasterxml.jackson.databind.ObjectMapper
import org.springframework.stereotype.Component
import org.springframework.web.context.request.RequestContextHolder
import org.springframework.web.context.request.ServletRequestAttributes
import org.springframework.web.servlet.HandlerMapping

/** A bounded preview of the requested operation. Never copies secrets or free-form documents. */
@Component
class BusinessWorkflowContext(private val mapper: ObjectMapper) : WorkflowActionContextProvider {
    override fun supports(path: String) = path.startsWith("/api/")
    override fun context(email: String, path: String, body: Any?): WorkflowTriggerContext {
        val values=mutableMapOf<String,String>()
        val request=(RequestContextHolder.getRequestAttributes() as? ServletRequestAttributes)?.request
        val variables=request?.getAttribute(HandlerMapping.URI_TEMPLATE_VARIABLES_ATTRIBUTE) as? Map<*,*>
        variables?.entries?.take(10)?.forEach { (key,value) ->
            if (key.toString().matches(Regex("[A-Za-z][A-Za-z0-9_]{0,39}"))) values["trigger.resource.$key"]=value.toString()
        }
        if (body!=null) {
            val input=mapper.valueToTree<com.fasterxml.jackson.databind.JsonNode>(body)
            for (field in FIELDS) {
                input.get(field)?.takeIf { it.isValueNode && !it.isNull }?.let { values["trigger.input.$field"]=it.asText() }
            }
        }
        FIELDS.forEach { field -> request?.getParameter(field)?.let { values.putIfAbsent("trigger.input.$field",it) } }
        return WorkflowTriggerContext(values=values)
    }
    companion object {
        val FIELDS = listOf("name","title","recipient","subject","amount","currency","status","state","action","projectId","clientId","templateId","folderId","boardId","sprintId","path","sendEmail","from","to","until","zone","kind","format","billable","includeDescriptions","includeAiSummary")
    }
}
