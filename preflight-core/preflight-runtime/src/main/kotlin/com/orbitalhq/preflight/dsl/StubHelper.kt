package com.orbitalhq.preflight.dsl

fun stub(name: String): StubResponseBuilder {
    return StubResponseBuilder(name)
}

data class StubResponseBuilder(val name: String) {
    fun returns(response: String): StubScenario {
        return StubScenario(name, response)
    }
}

data class StubScenario(val operationName: String, val response: String)