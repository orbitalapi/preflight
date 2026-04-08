package com.orbitalhq.preflight.spec

import com.orbitalhq.preflight.spec.internal.FrontMatterParser
import java.nio.file.Path
import kotlin.io.path.name
import kotlin.io.path.readText

object TestSpecReader {

    private val SUPPORTED_VERSIONS = setOf("0.1")
    private val format = markdownFormat<TestSpec>()

    fun read(markdown: String, filename: String? = null): TestSpec {
        // Validate front matter and spec version before delegating
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

        val spec = try {
            format.read(markdown, filename)
        } catch (e: SpecParseException) {
            throw translateError(e, filename)
        }

        validate(spec, filename)
        return spec
    }

    fun readFile(path: Path): TestSpec = read(path.readText(), filename = path.name)

    private fun translateError(e: SpecParseException, filename: String?): SpecParseException {
        val msg = e.message ?: return e
        return when {
            msg.contains("'name'") ->
                SpecParseException("Missing H1 heading. Every spec must have a # heading as the test name.", filename = filename)
            msg.contains("'query'") ->
                SpecParseException("Missing required section: ## Query", filename = filename)
            msg.contains("'expectedResult'") ->
                SpecParseException("Missing required section: ## Expected Result", filename = filename)
            msg.contains("'dataSources'") ->
                SpecParseException("Missing required section: ## Data Sources", filename = filename)
            msg.contains("'operationName'") -> {
                val stubName = e.section
                SpecParseException(
                    "Stub \"${stubName}\" is missing a source directive (<!-- operation: ... -->). Add an HTML comment after the ### heading.",
                    filename = filename,
                    section = "Data Sources"
                )
            }
            else -> e
        }
    }

    private fun validate(spec: TestSpec, filename: String?) {
        if (spec.dataSources.isEmpty()) {
            throw SpecParseException(
                "No stubs found under ## Data Sources. Add at least one ### heading with a source directive.",
                filename = filename,
                section = "Data Sources"
            )
        }

        for (stub in spec.dataSources) {
            if (stub.mode == StubMode.STREAM && stub.messages.isNullOrEmpty()) {
                throw SpecParseException(
                    "Stream-mode stub must have at least one Message: block.",
                    filename = filename,
                    section = stub.label
                )
            }
        }
    }
}
