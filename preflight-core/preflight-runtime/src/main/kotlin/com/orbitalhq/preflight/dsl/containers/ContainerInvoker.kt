package com.orbitalhq.preflight.dsl.containers

import com.orbitalhq.query.connectors.OperationInvoker
import com.orbitalhq.schemas.taxi.TaxiSchema
import io.kotest.extensions.testcontainers.ContainerExtension
import org.testcontainers.containers.GenericContainer

/**
 * An configured instance of a container that
 * will be converted into an Orbital connection, once started
 */
interface ContainerSupport {
    val containerExtension: ContainerExtension<*>
    val invokerFactory: (schema: TaxiSchema) -> OperationInvoker
    val container: GenericContainer<*>
    val connectionName: String
}


open class DefaultContainerSupport(
    override val containerExtension: ContainerExtension<*>,
    override val connectionName: String,
    override val container: GenericContainer<*>,
    override val invokerFactory: (schema: TaxiSchema) -> OperationInvoker
) : ContainerSupport

