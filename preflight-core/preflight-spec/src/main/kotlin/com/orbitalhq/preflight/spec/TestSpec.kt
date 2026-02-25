package com.orbitalhq.preflight.spec

data class TestSpec(
    val specVersion: String,
    val name: String,
    val description: String?,
    val query: String,
    val dataSources: List<Stub>,
    val expectedResult: String,
    val resultFormat: ResultFormat = ResultFormat.JSON,
    val flow: String?
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
