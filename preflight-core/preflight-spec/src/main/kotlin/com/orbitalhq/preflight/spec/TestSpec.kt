package com.orbitalhq.preflight.spec

data class TestSpec(
    val name: String,
    val description: String?,
    val query: String,
    val dataSources: List<Stub>,
    val expectedResult: String,
    val resultFormat: ResultFormat = ResultFormat.JSON,
    val flow: String?,
    val specVersion: String = "0.1",
)

enum class ResultFormat {
    JSON,
    TYPED_INSTANCE
}

data class Stub(
    val label: String,
    val operationName: String,
    val mode: StubMode,
    val response: String?,
    val messages: List<String>?
)

enum class StubMode {
    REQUEST_RESPONSE,
    STREAM
}
