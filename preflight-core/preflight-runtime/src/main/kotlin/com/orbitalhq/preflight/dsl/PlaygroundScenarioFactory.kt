package com.orbitalhq.preflight.dsl

import arrow.core.Either
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.orbitalhq.SourcePackage
import com.orbitalhq.playground.OperationStub
import com.orbitalhq.schemas.taxi.TaxiSchema
import io.kotest.assertions.AssertionFailedError
import io.kotest.core.test.TestCase
import java.io.ByteArrayOutputStream
import java.util.zip.GZIPOutputStream
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi

object PlaygroundScenarioFactory {
    private val objectMapper = jacksonObjectMapper()
    fun buildPlaygroundScenario(
        capturedScenario: CapturedQuery,
        sourcePackage: SourcePackage,
        schema: TaxiSchema,
        failure: AssertionFailedError,
        testCase: TestCase,
        playgroundHost: String = "https://playground.taxilang.org"
    ): Pair<PlaygroundQueryMessage, String> {

        val sources = sourcePackage.sources.joinToString("\n") { it.content }
        val stubs = buildOperationStubs(capturedScenario, schema)
        val readme = """
            ## ❌ Failed test scenario
            
            Your test `${testCase.name.testName}` failed. Bummer. 👎 
            
            Don't worry, it happens to the best of us.

            The test failed with the following error:
            
            ```
            ${failure.message.orEmpty()}
            ```
            
            Here's the query you were trying to run:
            
            ```taxiql
            ${capturedScenario.query.trim()}
            ```
            
            Stubs have been wired up, so you can re-run your query here. Either click the run button above,
            or the in the Query panel.
        """.trimIndent()
        val playgroundMessage = PlaygroundQueryMessage(
            sources,
            capturedScenario.query.trim(),
            emptyMap(),
            stubs,
            null, // TODO : expected json doesn't actually do anything yet.
            readme = readme
        )

        return playgroundMessage to getPlaygroundUrl(playgroundMessage,playgroundHost )

    }

    private fun buildOperationStubs(
        capturedScenario: CapturedQuery,
        schema: TaxiSchema
    ): List<OperationStub> {
        return capturedScenario.stub.responses.mapNotNull { (operationKey, response) ->
            when (response) {
                is Either.Right -> {
                    val resultTypedInstances = response.value
                    val listOfResults = resultTypedInstances.map { it.toRawObject() }

                    // find the operation this was intended for
                    capturedScenario.stub
                    val operation = schema.operations.firstOrNull { it.name == operationKey } ?: return@mapNotNull null
                    val responseAsJson = if (operation.returnType.isCollection) {
                        objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(listOfResults)
                    } else {
                        objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(listOfResults.firstOrNull())
                    }
                    OperationStub(
                        operationKey,
                        responseAsJson
                    )
                }

                else -> null
            }
        }
    }

    @OptIn(ExperimentalEncodingApi::class)
    fun getPlaygroundUrl(
        queryMessage: PlaygroundQueryMessage,
        playgroundHost: String = "https://playground.taxilang.org",
        enableDevTools: Boolean = false
    ): String {
        val json = jacksonObjectMapper().writeValueAsString(queryMessage)

        // Compress the JSON with GZIP
        val gzipOutput = ByteArrayOutputStream()
        GZIPOutputStream(gzipOutput).use {
            it.write(json.toByteArray())
        }
        val compressed = gzipOutput.toByteArray()

        // Base64 encode:
        val base64Encoded = Base64.encode(compressed)
        val devToolsPart = if (enableDevTools) {
            "?enableDevTools=true"
        } else ""

        return "$playgroundHost/$devToolsPart#pako:$base64Encoded"
    }

}

// TODO : This is duplicated here because I need to add the readme property upstream on the
// orbital instance of this message
data class PlaygroundQueryMessage(
    val schema: String,
    val query: String,
    val parameters: Map<String, Any> = emptyMap(),
    val stubs: List<OperationStub> = emptyList(),
    val expectedJson: String? = null,
    /**
     * The nebula stack Id that's currently running for this session.
     * Null if one doesn't exist yet.
     */
    val stackId: String? = null,
    val readme: String?
)