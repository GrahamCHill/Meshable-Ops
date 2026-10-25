package com.starter.service.storage

import com.starter.exception.ForbiddenException

/** Reserved for generated exports; ordinary storage APIs must not bypass document ownership/policy. */
object GeneratedPdfNamespace {
    const val FOLDER = "__generated_pdfs__"
    const val SQL_PATTERN = "(^|/)__generated_pdfs__(/|$)"
    fun excluded(vararg paths: String?): Boolean = paths.any { path ->
        path.orEmpty().replace('\\', '/').split('/').any { it.equals(FOLDER, ignoreCase = true) }
    }
    fun requireOrdinary(vararg paths: String?) {
        if (excluded(*paths)) throw ForbiddenException("Generated PDFs are available through their document or report, not shared storage")
    }
}
