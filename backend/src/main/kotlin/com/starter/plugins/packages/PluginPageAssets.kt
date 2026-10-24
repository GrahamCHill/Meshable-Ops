package com.starter.plugins.packages

import com.starter.exception.BadRequestException
import org.jsoup.Jsoup
import org.jsoup.nodes.DataNode
import java.net.URI

/** Bundle-local assets are inlined into the opaque frontend frame; no authenticated static URL is exposed. */
object PluginPageAssets {
    fun render(entry: String, files: Map<String, String>): String {
        val document = Jsoup.parse(files[entry] ?: invalid())
        document.select("meta[http-equiv], base, iframe, object, embed").remove()
        document.select("script[src]").forEach { element ->
            val path = resolve(entry, element.attr("src"))
            if (!path.endsWith(".js")) invalid()
            val source = files[path] ?: invalid()
            element.removeAttr("src").removeAttr("integrity").removeAttr("crossorigin").empty().appendChild(DataNode(source.replace(Regex("</script", RegexOption.IGNORE_CASE)) { "<\\/script" }))
        }
        document.select("link[rel=stylesheet]").forEach { element ->
            val path = resolve(entry, element.attr("href"))
            if (!path.endsWith(".css")) invalid()
            element.tagName("style").removeAttr("href").removeAttr("rel").empty().appendChild(DataNode(css(path, files[path] ?: invalid(), files)))
        }
        document.select("style").forEach { element -> val source = element.data(); element.empty().appendChild(DataNode(css(entry, source, files))) }
        document.select("[style]").forEach { element -> element.attr("style", css(entry, element.attr("style"), files)) }
        document.select("img[src]").forEach { element -> element.attr("src", asset(entry, element.attr("src"), files)) }
        document.select("[srcset]").removeAttr("srcset")
        val result = document.outerHtml()
        if (result.toByteArray().size > 16 * 1024 * 1024) invalid()
        return result
    }
    private fun css(base: String, value: String, files: Map<String,String>): String {
        if (Regex("@import", RegexOption.IGNORE_CASE).containsMatchIn(value)) throw BadRequestException("Bundle CSS imports before packaging the plugin")
        return Regex("url\\(\\s*(['\"]?)([^)'\"]+)\\1\\s*\\)").replace(value) { "url(\"${asset(base, it.groupValues[2].trim(), files)}\")" }
    }
    private fun asset(base: String, reference: String, files: Map<String,String>): String {
        if (reference.startsWith("data:")) return reference
        val path = resolve(base, reference)
        val mime = when (path.substringAfterLast('.').lowercase()) {"png" -> "image/png"; "jpg", "jpeg" -> "image/jpeg"; "webp" -> "image/webp"; "woff" -> "font/woff"; "woff2" -> "font/woff2"; else -> invalid()}
        return "data:$mime;base64,${files[path] ?: invalid()}"
    }
    private fun resolve(base: String, reference: String): String {
        val uri = runCatching { URI(reference) }.getOrNull() ?: invalid()
        if (uri.isAbsolute || uri.rawAuthority != null || uri.query != null || uri.fragment != null || reference.startsWith('/') || reference.contains('\\')) invalid()
        val path = URI(base).resolve(uri).normalize().path
        if (path.startsWith("../") || !path.matches(Regex("[A-Za-z0-9][A-Za-z0-9_./-]{0,119}"))) invalid()
        return path
    }
    private fun invalid(): Nothing = throw BadRequestException("Frontend assets must reference packaged relative JS, CSS, raster images or fonts; bundle JavaScript imports into one entry file")
}
