package com.orbitalhq.preflight.spec.internal

internal object DirectiveParser {

    private val COMMENT_PATTERN = Regex("^\\s*<!--(.*)-->\\s*$", RegexOption.DOT_MATCHES_ALL)

    fun parse(htmlComment: String): Map<String, String> {
        val match = COMMENT_PATTERN.find(htmlComment.trim()) ?: return emptyMap()
        val inner = match.groupValues[1].trim()
        if (inner.isEmpty()) return emptyMap()

        val result = mutableMapOf<String, String>()
        inner.split(",").forEach { part ->
            val trimmed = part.trim()
            if (trimmed.isEmpty()) return@forEach
            val colonIndex = trimmed.indexOf(':')
            if (colonIndex < 0) return@forEach
            val key = trimmed.substring(0, colonIndex).trim()
            val value = trimmed.substring(colonIndex + 1).trim()
            if (key.isNotEmpty()) {
                result[key] = value
            }
        }
        return result
    }

    fun parseAll(htmlComments: List<String>): Map<String, String> {
        val merged = mutableMapOf<String, String>()
        for (comment in htmlComments) {
            merged.putAll(parse(comment))
        }
        return merged
    }
}
