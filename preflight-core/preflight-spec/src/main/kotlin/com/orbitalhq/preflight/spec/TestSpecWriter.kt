package com.orbitalhq.preflight.spec

import java.nio.file.Path
import kotlin.io.path.writeText

object TestSpecWriter {

    fun write(spec: TestSpec): String = buildString {
        // Front matter
        appendLine("---")
        appendLine("spec-version: ${spec.specVersion}")
        appendLine("---")
        appendLine()

        // H1 - test name
        appendLine("# ${spec.name}")
        appendLine()

        // Description (optional)
        if (spec.description != null) {
            appendLine(spec.description)
            appendLine()
        }

        // Query section
        appendLine("## Query")
        appendLine()
        appendLine("```taxiql")
        appendLine(spec.query)
        appendLine("```")
        appendLine()

        // Data Sources section
        appendLine("## Data Sources")
        appendLine()

        spec.dataSources.forEachIndexed { index, stub ->
            appendLine("### ${stub.label}")
            // Directive immediately after H3 (no blank line)
            if (stub.mode == StubMode.STREAM) {
                appendLine("<!-- operation: ${stub.operationName}, mode: stream -->")
            } else {
                appendLine("<!-- operation: ${stub.operationName} -->")
            }
            appendLine()

            when (stub.mode) {
                StubMode.REQUEST_RESPONSE -> {
                    if (stub.response != null) {
                        appendLine("Response:")
                        appendLine("```json")
                        appendLine(stub.response)
                        appendLine("```")
                    }
                }
                StubMode.STREAM -> {
                    stub.messages?.forEachIndexed { msgIndex, message ->
                        if (msgIndex > 0) appendLine()
                        appendLine("Message:")
                        appendLine("```json")
                        appendLine(message)
                        appendLine("```")
                    }
                }
            }
            appendLine()
        }

        // Expected Result section
        appendLine("## Expected Result")
        appendLine()
        val infoString = when (spec.resultFormat) {
            ResultFormat.JSON -> "json"
            ResultFormat.TYPED_INSTANCE -> "json typedInstance"
        }
        appendLine("```$infoString")
        appendLine(spec.expectedResult)
        appendLine("```")

        // Flow section (optional)
        if (spec.flow != null) {
            appendLine()
            appendLine("## Flow")
            appendLine()
            appendLine("```mermaid")
            appendLine(spec.flow)
            appendLine("```")
        }
    }

    fun writeFile(spec: TestSpec, path: Path) {
        path.writeText(write(spec))
    }
}
