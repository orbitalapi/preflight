package com.orbitalhq.preflight.dsl

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import com.orbitalhq.models.json.Jackson
import com.orbitalhq.preflight.spec.Stub
import com.orbitalhq.preflight.spec.StubMode
import com.orbitalhq.preflight.spec.TestSpec
import com.orbitalhq.preflight.spec.TestSpecReader
import com.orbitalhq.query.RemoteCallExchangeMetadata
import com.orbitalhq.stubbing.StubService
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.flow.flowOf
import java.nio.file.Files
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
    objectMapper: ObjectMapper
) {
    val stubCustomizer: (StubService) -> Unit = { stubService ->

        val stubsByOperationName = spec.dataSources.groupBy { it.operationName }
        stubsByOperationName
            .forEach { (operationName, stubbedCalls) ->
                if (stubbedCalls.isNotEmpty()) {
                    val modes = stubbedCalls.map { it.mode }.distinct()
                    if (modes.size > 1) {
                        // There's no reason we can't support this in theory, but I don't think it's a real use-case,
                        // and complicates the execution, as we need to further split these
                        error("Operation $operationName has a mix of stub modes (${modes.joinToString()}) which is not currently supported")
                    }
                    val singleStubMode = modes.single()
                    when (singleStubMode) {
                        StubMode.STREAM -> {
                            stubbedCalls.forEach { stub ->
                                stubService.addResponseFlow(stub.operationName) { _,_ ->
                                    val messages = stubResponseAsTypedInstanceResponses(stub, stubService.schema!!)
                                    flowOf(*messages.toTypedArray())
                                }
                            }
                        }

                        StubMode.REQUEST_RESPONSE -> {
                            val stubbedCallsByParameters: List<Pair<Map<String, Any?>, Stub>> = stubbedCalls.map { stubbedCall ->
                                val parametersAsMap = if (stubbedCall.parameters != null) {
                                    Jackson.defaultObjectMapper.readValue<Map<String,Any?>>(stubbedCall.parameters!!)
                                } else emptyMap()
                                parametersAsMap to stubbedCall
                            }
                            stubService.addResponse(operationName) { _,parameters ->
                                // We need to match the request with the incoming parameters
                                if (parameters.isEmpty()) {
                                    // If there's no parameters provided, we just match the first call
                                    if (stubbedCalls.size != 1) {
                                        error("The test spec is ambiguous. There are ${stubbedCalls.size} calls configured for ${operationName}, but no parameters are declared on the operation, so cannot determine which to pick")
                                    } else {
                                        val stubbedCall = stubbedCalls.single()
                                        stubResponseAsTypedInstanceResponses(stubbedCall, stubService.schema!!)
                                    }
                                } else {
                                    // Otherwise, we need to map on parameters
                                    val receivedParametersAsMap = RemoteCallExchangeMetadata.convertParametersToMap(parameters)
                                    val matchingCalls = stubbedCallsByParameters.filter { it.first == receivedParametersAsMap }
                                    when (matchingCalls.size) {
                                        0 -> error("No stubbed operation calls for operation ${operationName} matched on the provided parameters, although ${stubbedCalls.size} stubs are configured for this operation. Provided parameters: ${receivedParametersAsMap}")
                                        1 -> stubResponseAsTypedInstanceResponses(matchingCalls.single().second, stubService.schema!!)
                                        else -> error("The test spec is ambiguous. There are ${matchingCalls.size} calls configured for ${operationName}, which match the parameters provided: ${receivedParametersAsMap}")
                                    }

                                }
                            }
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
                val actualJson = objectMapper.valueToTree<JsonNode>(actual)
                actualJson shouldBe expectedJson
            } else {
                val actual = spec.query.queryForMap(stubCustomizer)
                val actualJson = objectMapper.valueToTree<JsonNode>(actual)
                actualJson shouldBe expectedJson
            }
        }
    }
}
