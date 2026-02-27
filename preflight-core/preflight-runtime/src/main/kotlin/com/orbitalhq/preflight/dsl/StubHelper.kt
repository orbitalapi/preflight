package com.orbitalhq.preflight.dsl

import arrow.core.Either
import com.orbitalhq.models.Provided
import com.orbitalhq.models.TypedInstance
import com.orbitalhq.models.json.right
import com.orbitalhq.preflight.spec.Stub
import com.orbitalhq.query.StreamErrorMessage
import com.orbitalhq.schemas.Schema
import com.orbitalhq.schemas.fqn
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

fun messagesAsTypedInstanceResponses(stub: Stub, schema: Schema): List<Either<StreamErrorMessage, TypedInstance>> {
    val (_,operation) = schema.remoteOperation(stub.operationName.fqn())
    val streamType = operation.returnType.typeParameters[0]
    val messagesAsTypedInstances = stub.messages.orEmpty().map { json ->
        TypedInstance.from(streamType, json, source = Provided, schema = schema)
            .right()
    }
    return messagesAsTypedInstances
}