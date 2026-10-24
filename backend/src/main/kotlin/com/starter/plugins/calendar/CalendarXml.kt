package com.starter.plugins.calendar

import com.starter.plugins.integrations.IntegrationUnavailable
import org.w3c.dom.Document
import org.xml.sax.InputSource
import java.io.StringReader
import javax.xml.XMLConstants
import javax.xml.parsers.DocumentBuilderFactory

internal object CalendarXml {
    fun parse(xml: String): Document {
        try {
            if (xml.length>1_048_576) throw IllegalArgumentException("Oversized XML")
            val factory=DocumentBuilderFactory.newInstance().apply {
                isNamespaceAware=true;isXIncludeAware=false;isExpandEntityReferences=false
                setFeature("http://apache.org/xml/features/disallow-doctype-decl",true)
                setFeature("http://xml.org/sax/features/external-general-entities",false)
                setFeature("http://xml.org/sax/features/external-parameter-entities",false)
                setAttribute(XMLConstants.ACCESS_EXTERNAL_DTD,"");setAttribute(XMLConstants.ACCESS_EXTERNAL_SCHEMA,"")
            }
            val builder=factory.newDocumentBuilder().apply {setErrorHandler(object:org.xml.sax.helpers.DefaultHandler() {
                override fun error(error: org.xml.sax.SAXParseException) {throw error}
                override fun fatalError(error: org.xml.sax.SAXParseException) {throw error}
            })}
            return builder.parse(InputSource(StringReader(xml)))
        } catch (_:Exception) {throw IntegrationUnavailable("CalDAV returned unsupported XML")}
    }
}
