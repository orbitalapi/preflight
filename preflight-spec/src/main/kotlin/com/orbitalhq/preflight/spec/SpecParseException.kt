package com.orbitalhq.preflight.spec

class SpecParseException(
    message: String,
    val filename: String? = null,
    val lineNumber: Int? = null,
    val section: String? = null
) : RuntimeException(buildMessage(message, filename, lineNumber, section)) {

    companion object {
        private fun buildMessage(
            message: String,
            filename: String?,
            lineNumber: Int?,
            section: String?
        ): String = buildString {
            append("Preflight spec parse error")
            if (filename != null) append(" in $filename")
            if (section != null) append(" (section: $section)")
            if (lineNumber != null) append(" at line $lineNumber")
            append(": $message")
        }
    }
}
