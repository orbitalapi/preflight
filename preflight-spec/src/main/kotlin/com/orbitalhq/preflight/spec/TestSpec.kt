package com.orbitalhq.preflight.spec

@MarkdownSpec
data class TestSpec(
    @FrontMatter("spec-version") val specVersion: String = "0.1",
    @Title val name: String,
    @Description val description: String?,
    @Section("Query") @CodeBlock("taxiql") val query: String,
    @Section("Data Sources") val dataSources: List<Stub>,
    @Section("Expected Result") @CodeBlock("json", qualifierProperty = "resultFormat") val expectedResult: String,
    val resultFormat: ResultFormat = ResultFormat.JSON,
)

enum class ResultFormat(override val qualifier: String?) : CodeBlockQualifier {
    JSON(null),
    TYPED_INSTANCE("typedInstance")
}

data class Stub(
    @Heading val label: String,
    @Directive("operation") val operationName: String,
    @Directive("mode", defaultValue = "request-response") val mode: StubMode,
    @LabeledCodeBlock("Request", "json") val parameters: String?,
    @LabeledCodeBlock("Response", "json") val response: String?,
    @LabeledCodeBlock("Message", "json") val messages: List<String>?
)

enum class StubMode {
    REQUEST_RESPONSE,
    STREAM
}
