package com.orbitalhq.preflight.spec.internal

import com.orbitalhq.preflight.spec.SpecParseException

internal data class FrontMatterResult(
    val metadata: Map<String, String>,
    val remainingContent: String
)

internal object FrontMatterParser {

    fun parse(markdown: String, filename: String? = null): FrontMatterResult {
        val lines = markdown.lines()

        val firstDelimiter = lines.indexOfFirst { it.trim() == "---" }
        if (firstDelimiter < 0) {
            throw SpecParseException(
                "Missing front matter. File must start with --- and include spec-version.",
                filename = filename
            )
        }

        val secondDelimiter = lines.drop(firstDelimiter + 1).indexOfFirst { it.trim() == "---" }
        if (secondDelimiter < 0) {
            throw SpecParseException(
                "Missing front matter. File must start with --- and include spec-version.",
                filename = filename
            )
        }

        val metadataLines = lines.subList(firstDelimiter + 1, firstDelimiter + 1 + secondDelimiter)
        val metadata = mutableMapOf<String, String>()
        for (line in metadataLines) {
            val trimmed = line.trim()
            if (trimmed.isEmpty()) continue
            val colonIndex = trimmed.indexOf(':')
            if (colonIndex < 0) continue
            val key = trimmed.substring(0, colonIndex).trim()
            val value = trimmed.substring(colonIndex + 1).trim()
            if (key.isNotEmpty()) {
                metadata[key] = value
            }
        }

        val remainingStartIndex = firstDelimiter + 1 + secondDelimiter + 1
        val remainingContent = if (remainingStartIndex < lines.size) {
            lines.subList(remainingStartIndex, lines.size).joinToString("\n")
        } else {
            ""
        }

        return FrontMatterResult(metadata, remainingContent)
    }
}
