package com.orbitalhq.preflight.dsl

import arrow.core.Either
import com.orbitalhq.models.Provided
import com.orbitalhq.models.TypedInstance
import com.orbitalhq.models.TypedNull
import com.orbitalhq.models.json.right
import com.orbitalhq.preflight.spec.Stub
import com.orbitalhq.query.StreamErrorMessage
import com.orbitalhq.schemas.Schema
import com.orbitalhq.schemas.fqn
import lang.taxi.types.PrimitiveType
import kotlin.collections.map
import kotlin.collections.orEmpty

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

/**
 * Converts the stub response (either a request response, or a stream) into
 * the format expected by the stub service
 */
fun stubResponseAsTypedInstanceResponses(stub: Stub, schema: Schema): List<Either<StreamErrorMessage, TypedInstance>> {
    val (_, operation) = schema.remoteOperation(stub.operationName.fqn())
    // Unwrap Array or Stream types
    val stubResponseAsTypedInstance = when {
        stub.messages != null -> {
            // Should be a stream
            val responseType = operation.returnType.typeParameters[0]
            stub.messages.orEmpty().map { json ->
                TypedInstance.from(responseType, json, source = Provided, schema = schema)
                    .right()
            }
        }
        stub.response != null -> {
            val responseType = operation.returnType
            listOf(TypedInstance.from(responseType, stub.response, source = Provided, schema = schema).right())
        }
        else -> {
            // No response provided -- could be a void method - so return null
            // Not actually sure what to return here, since void methods don't typically exist,
            // but doesn't feel like a reasonable reason to bail
            listOf(TypedNull.create(schema.type(PrimitiveType.ANY)).right())
        }
    }
    return stubResponseAsTypedInstance

    return stubResponseAsTypedInstance
}