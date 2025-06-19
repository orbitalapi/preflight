package com.orbitalhq.preflight.dsl

/**
 * Generates a Stub response builder for an operation with
 * the provided name.
 *
 * The test engine will search all services for an operation
 * with the matching name
 */
fun stub(operationName: String): StubResponseBuilder {
    return StubResponseBuilder(operationName)
}

data class StubResponseBuilder(val operationName: String) {
    /**
     * Accepts a JSON response which is returned from the remote call.
     */
    fun returns(response: String): StubScenario {
        return StubScenario(operationName, response)
    }
}

data class StubScenario(val operationName: String, val response: String)