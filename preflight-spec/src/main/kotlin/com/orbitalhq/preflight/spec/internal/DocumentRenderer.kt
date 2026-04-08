package com.orbitalhq.preflight.spec.internal

internal object DocumentRenderer {

    fun render(doc: ParsedDocument): String = buildString {
        // Front matter
        if (doc.frontMatter.isNotEmpty()) {
            appendLine("---")
            for ((key, value) in doc.frontMatter) {
                appendLine("$key: $value")
            }
            appendLine("---")
            appendLine()
        }

        // Title
        if (doc.title != null) {
            appendLine("# ${doc.title}")
            appendLine()
        }

        // Description
        if (doc.description != null) {
            appendLine(doc.description)
            appendLine()
        }

        // Sections
        for (section in doc.sections) {
            renderSection(this, section)
        }
    }

    private fun renderSection(sb: StringBuilder, section: ParsedSection) {
        val prefix = "#".repeat(section.level)
        sb.appendLine("$prefix ${section.title}")

        // Directives immediately after heading (no blank line)
        if (section.directives.isNotEmpty()) {
            val directiveStr = section.directives.entries.joinToString(", ") { "${it.key}: ${it.value}" }
            sb.appendLine("<!-- $directiveStr -->")
        }
        sb.appendLine()

        // Content
        for (content in section.content) {
            when (content) {
                is ParsedContent.CodeBlock -> {
                    if (content.label != null) {
                        sb.appendLine("${content.label}:")
                    }
                    val infoString = buildString {
                        append(content.language)
                        if (content.qualifier != null) {
                            append(" ${content.qualifier}")
                        }
                    }
                    sb.appendLine("```$infoString")
                    sb.appendLine(content.code)
                    sb.appendLine("```")
                    sb.appendLine()
                }
                is ParsedContent.Paragraph -> {
                    sb.appendLine(content.text)
                    sb.appendLine()
                }
            }
        }

        // Children
        for (child in section.children) {
            renderSection(sb, child)
        }
    }
}
