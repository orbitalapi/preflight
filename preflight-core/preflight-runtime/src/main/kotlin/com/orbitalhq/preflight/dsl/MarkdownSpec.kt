package com.orbitalhq.preflight.dsl

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.orbitalhq.preflight.spec.StubMode
import com.orbitalhq.preflight.spec.TestSpec
import com.orbitalhq.preflight.spec.TestSpecReader
import com.orbitalhq.stubbing.StubService
import io.kotest.matchers.shouldBe
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import kotlin.io.path.extension
import kotlin.io.path.isRegularFile

abstract class MarkdownSpec(
    specsPath: String = "test-resources/specs",
    sourceConfig: PreflightSourceConfig = FilePathSourceConfig()
) : OrbitalSpec({

    val objectMapper = jacksonObjectMapper()
    val specsDir = Paths.get(specsPath)

    if (Files.exists(specsDir) && Files.isDirectory(specsDir)) {
        val specFiles = Files.walk(specsDir)
            .filter { it.isRegularFile() && it.extension == "md" && it.fileName.toString().endsWith(".spec.md") }
            .sorted()
            .toList()

        for (file in specFiles) {
            val spec = TestSpecReader.readFile(file)
            registerSpec(spec, objectMapper)
        }
    }

}, sourceConfig)

private fun OrbitalSpec.registerSpec(
    spec: TestSpec,
    objectMapper: com.fasterxml.jackson.databind.ObjectMapper
) {
    val stubCustomizer: (StubService) -> Unit = { stubService ->
        for (stub in spec.dataSources) {
            when (stub.mode) {
                StubMode.REQUEST_RESPONSE -> {
                    val response = stub.response
                    if (response != null) {
                        stubService.addResponse(stub.operationName, response)
                    }
                }
                StubMode.STREAM -> {
                    val emitter = stubService.addResponseEmitter(stub.operationName)
                    stub.messages?.forEach { message ->
                        emitter.next(message)
                    }
                }
            }
        }
    }

    describe(spec.name) {
        it("matches expected result") {
            val expectedJson = objectMapper.readTree(spec.expectedResult)
            val isArray = expectedJson.isArray

            if (isArray) {
                val actual = spec.query.queryForCollectionOfMaps(stubCustomizer)
                val actualJson = objectMapper.valueToTree<com.fasterxml.jackson.databind.JsonNode>(actual)
                actualJson shouldBe expectedJson
            } else {
                val actual = spec.query.queryForMap(stubCustomizer)
                val actualJson = objectMapper.valueToTree<com.fasterxml.jackson.databind.JsonNode>(actual)
                actualJson shouldBe expectedJson
            }
        }
    }
}
