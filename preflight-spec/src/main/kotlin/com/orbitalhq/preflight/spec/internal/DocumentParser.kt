package com.orbitalhq.preflight.spec.internal

import org.commonmark.node.*
import org.commonmark.parser.Parser

internal object DocumentParser {

    fun parse(markdown: String, filename: String? = null): ParsedDocument {
        val frontMatterResult = FrontMatterParser.parseOptional(markdown)
        val parser = Parser.builder().build()
        val document = parser.parse(frontMatterResult.remainingContent)

        var title: String? = null
        var description: String? = null
        val descriptionParts = mutableListOf<String>()
        var collectingDescription = false
        val sections = mutableListOf<ParsedSection>()

        // We'll collect top-level nodes and group them by heading hierarchy
        val nodes = mutableListOf<Node>()
        var node: Node? = document.firstChild
        while (node != null) {
            nodes.add(node)
            node = node.next
        }

        var i = 0
        while (i < nodes.size) {
            val current = nodes[i]
            when {
                current is Heading && current.level == 1 -> {
                    title = current.textContent()
                    collectingDescription = true
                    i++
                }
                collectingDescription && current is Heading -> {
                    collectingDescription = false
                    description = descriptionParts.joinToString("\n\n").takeIf { it.isNotBlank() }
                    // Don't increment - process this heading in the next iteration
                }
                collectingDescription && current is Paragraph -> {
                    descriptionParts.add(current.textContent())
                    i++
                }
                collectingDescription -> {
                    // Non-paragraph, non-heading while collecting description - stop collecting
                    collectingDescription = false
                    description = descriptionParts.joinToString("\n\n").takeIf { it.isNotBlank() }
                    // Don't increment
                }
                current is Heading && current.level >= 2 -> {
                    val (section, nextIdx) = parseSectionAt(nodes, i, current.level)
                    sections.add(section)
                    i = nextIdx
                }
                else -> {
                    i++
                }
            }
        }

        // Flush description if we were still collecting at end
        if (collectingDescription) {
            description = descriptionParts.joinToString("\n\n").takeIf { it.isNotBlank() }
        }

        return ParsedDocument(
            frontMatter = frontMatterResult.metadata,
            title = title,
            description = description,
            sections = sections
        )
    }

    private fun parseSectionAt(nodes: List<Node>, startIdx: Int, level: Int): Pair<ParsedSection, Int> {
        val heading = nodes[startIdx] as Heading
        val sectionTitle = heading.textContent()
        val directives = mutableMapOf<String, String>()
        val content = mutableListOf<ParsedContent>()
        val children = mutableListOf<ParsedSection>()
        var lastParagraphLabel: String? = null

        var i = startIdx + 1
        while (i < nodes.size) {
            val current = nodes[i]
            when {
                current is Heading && current.level <= level -> {
                    // End of this section - a heading at same or higher level
                    break
                }
                current is Heading && current.level == level + 1 -> {
                    // Child section
                    val (child, nextIdx) = parseSectionAt(nodes, i, current.level)
                    children.add(child)
                    i = nextIdx
                }
                current is Heading -> {
                    // Deeper heading - treat as child
                    val (child, nextIdx) = parseSectionAt(nodes, i, current.level)
                    children.add(child)
                    i = nextIdx
                }
                current is HtmlBlock -> {
                    val parsed = DirectiveParser.parse(current.literal.trim())
                    directives.putAll(parsed)
                    i++
                }
                current is Paragraph -> {
                    val text = current.textContent().trim()
                    if (text.endsWith(":")) {
                        lastParagraphLabel = text.dropLast(1).trim()
                    } else {
                        content.add(ParsedContent.Paragraph(text))
                        lastParagraphLabel = null
                    }
                    i++
                }
                current is FencedCodeBlock -> {
                    val info = current.info?.trim() ?: ""
                    val parts = info.split("\\s+".toRegex())
                    val language = parts.getOrNull(0) ?: ""
                    val qualifier = parts.getOrNull(1)
                    val code = current.literal.trimEnd()
                    content.add(
                        ParsedContent.CodeBlock(
                            language = language,
                            qualifier = qualifier,
                            code = code,
                            label = lastParagraphLabel
                        )
                    )
                    lastParagraphLabel = null
                    i++
                }
                else -> {
                    i++
                }
            }
        }

        return ParsedSection(
            title = sectionTitle,
            level = level,
            directives = directives,
            content = content,
            children = children
        ) to i
    }

    private fun Node.textContent(): String {
        val sb = StringBuilder()
        var child: Node? = this.firstChild
        while (child != null) {
            when (child) {
                is Text -> sb.append(child.literal)
                is Code -> sb.append(child.literal)
                is SoftLineBreak -> sb.append(" ")
                is HardLineBreak -> sb.append(" ")
                else -> sb.append(child.textContent())
            }
            child = child.next
        }
        return sb.toString()
    }
}
