package com.orbitalhq.preflight.spec

import com.orbitalhq.preflight.spec.internal.DirectiveParser
import com.orbitalhq.preflight.spec.internal.FrontMatterParser
import org.commonmark.node.*
import org.commonmark.parser.Parser
import java.nio.file.Path
import kotlin.io.path.name
import kotlin.io.path.readText

object TestSpecReader {

    private val SUPPORTED_VERSIONS = setOf("0.1")

    fun read(markdown: String, filename: String? = null): TestSpec {
        val frontMatter = FrontMatterParser.parse(markdown, filename)
        val specVersion = frontMatter.metadata["spec-version"]
            ?: throw SpecParseException(
                "Missing required field 'spec-version' in front matter.",
                filename = filename
            )
        if (specVersion !in SUPPORTED_VERSIONS) {
            throw SpecParseException(
                "Unsupported spec version \"$specVersion\". This reader supports versions: ${SUPPORTED_VERSIONS.joinToString(", ")}",
                filename = filename
            )
        }

        val parser = Parser.builder().build()
        val document = parser.parse(frontMatter.remainingContent)

        var name: String? = null
        var description: String? = null
        var query: String? = null
        val dataSources = mutableListOf<Stub>()
        var expectedResult: String? = null
        var flow: String? = null

        var currentH2: String? = null
        var collectingDescription = false
        val descriptionParts = mutableListOf<String>()

        // Stub parsing state
        var currentStubLabel: String? = null
        var currentStubDirectives = mutableListOf<String>()
        var currentStubResponse: String? = null
        var currentStubMessages = mutableListOf<String>()
        var lastParagraphLabel: String? = null

        fun flushStub() {
            val label = currentStubLabel ?: return
            val directives = DirectiveParser.parseAll(currentStubDirectives)

            if (directives.isEmpty()) {
                throw SpecParseException(
                    "Stub \"$label\" is missing a source directive (<!-- operation: ... -->). Add an HTML comment after the ### heading.",
                    filename = filename,
                    section = "Data Sources"
                )
            }

            val operationName = directives["operation"]
                ?: throw SpecParseException(
                    "Directive is missing required field \"operation\". Expected: <!-- operation: operationName -->",
                    filename = filename,
                    section = label
                )

            val modeStr = directives["mode"]
            val mode = when (modeStr) {
                null, "request-response" -> StubMode.REQUEST_RESPONSE
                "stream" -> StubMode.STREAM
                else -> StubMode.REQUEST_RESPONSE
            }

            if (mode == StubMode.STREAM && currentStubMessages.isEmpty()) {
                throw SpecParseException(
                    "Stream-mode stub must have at least one Message: block.",
                    filename = filename,
                    section = label
                )
            }

            dataSources.add(
                Stub(
                    label = label,
                    operationName = operationName,
                    mode = mode,
                    response = if (mode == StubMode.REQUEST_RESPONSE) currentStubResponse else null,
                    messages = if (mode == StubMode.STREAM) currentStubMessages.toList() else null
                )
            )

            currentStubLabel = null
            currentStubDirectives = mutableListOf()
            currentStubResponse = null
            currentStubMessages = mutableListOf()
            lastParagraphLabel = null
        }

        var node: Node? = document.firstChild
        while (node != null) {
            when (node) {
                is Heading -> {
                    when (node.level) {
                        1 -> {
                            name = node.textContent()
                            collectingDescription = true
                            currentH2 = null
                        }
                        2 -> {
                            if (collectingDescription) {
                                collectingDescription = false
                                description = descriptionParts.joinToString("\n\n").takeIf { it.isNotBlank() }
                            }
                            if (currentH2 == "Data Sources") {
                                flushStub()
                            }
                            currentH2 = node.textContent()
                            lastParagraphLabel = null
                        }
                        3 -> {
                            if (currentH2 == "Data Sources") {
                                flushStub()
                                currentStubLabel = node.textContent()
                                lastParagraphLabel = null
                            }
                        }
                    }
                }
                is Paragraph -> {
                    if (collectingDescription) {
                        descriptionParts.add(node.textContent())
                    } else if (currentH2 == "Data Sources" && currentStubLabel != null) {
                        val text = node.textContent().trim()
                        if (text.endsWith(":")) {
                            lastParagraphLabel = text.dropLast(1).trim()
                        } else {
                            lastParagraphLabel = null
                        }
                    } else {
                        lastParagraphLabel = null
                    }
                }
                is HtmlBlock -> {
                    if (currentH2 == "Data Sources" && currentStubLabel != null) {
                        currentStubDirectives.add(node.literal.trim())
                    }
                }
                is FencedCodeBlock -> {
                    when (currentH2) {
                        "Query" -> {
                            query = node.literal.trimEnd()
                        }
                        "Data Sources" -> {
                            if (currentStubLabel != null) {
                                val content = node.literal.trimEnd()
                                when (lastParagraphLabel) {
                                    "Response" -> currentStubResponse = content
                                    "Message" -> currentStubMessages.add(content)
                                }
                                lastParagraphLabel = null
                            }
                        }
                        "Expected Result" -> {
                            expectedResult = node.literal.trimEnd()
                        }
                        "Flow" -> {
                            flow = node.literal.trimEnd()
                        }
                    }
                }
                else -> {
                    // Skip unknown node types
                }
            }
            node = node.next
        }

        // Flush final stub if we were collecting one
        if (currentH2 == "Data Sources") {
            flushStub()
        }

        // Flush description if document ended while still collecting
        if (collectingDescription) {
            collectingDescription = false
            description = descriptionParts.joinToString("\n\n").takeIf { it.isNotBlank() }
        }

        // Validation
        if (name == null) {
            throw SpecParseException("Missing H1 heading. Every spec must have a # heading as the test name.", filename = filename)
        }
        if (query == null) {
            throw SpecParseException("Missing required section: ## Query", filename = filename)
        }
        if (dataSources.isEmpty() && currentH2 != "Data Sources") {
            // If we never saw a Data Sources section at all
            throw SpecParseException("Missing required section: ## Data Sources", filename = filename)
        }
        if (dataSources.isEmpty()) {
            throw SpecParseException(
                "No stubs found under ## Data Sources. Add at least one ### heading with a source directive.",
                filename = filename,
                section = "Data Sources"
            )
        }
        if (expectedResult == null) {
            throw SpecParseException("Missing required section: ## Expected Result", filename = filename)
        }

        return TestSpec(
            specVersion = specVersion,
            name = name,
            description = description,
            query = query,
            dataSources = dataSources,
            expectedResult = expectedResult,
            flow = flow
        )
    }

    fun readFile(path: Path): TestSpec = read(path.readText(), filename = path.name)

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
