package com.orbitalhq.preflight.spec.internal

internal data class ParsedDocument(
    val frontMatter: Map<String, String>,
    val title: String?,
    val description: String?,
    val sections: List<ParsedSection>
)

internal data class ParsedSection(
    val title: String,
    val level: Int,
    val directives: Map<String, String>,
    val content: List<ParsedContent>,
    val children: List<ParsedSection>
)

internal sealed class ParsedContent {
    data class CodeBlock(
        val language: String,
        val qualifier: String?,
        val code: String,
        val label: String?
    ) : ParsedContent()

    data class Paragraph(val text: String) : ParsedContent()
}
