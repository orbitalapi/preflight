package com.orbitalhq.preflight.dsl.containers.mongo

import com.mongodb.ConnectionString
import com.mongodb.MongoClientSettings
import com.mongodb.reactivestreams.client.MongoClient
import com.mongodb.reactivestreams.client.MongoClients
import com.orbitalhq.connectors.config.mongodb.MongoConnection
import com.orbitalhq.connectors.config.mongodb.MongoConnectionConfiguration
import com.orbitalhq.connectors.nosql.mongodb.MongoConnectionFactory
import com.orbitalhq.connectors.nosql.mongodb.MongoDbInvoker
import com.orbitalhq.connectors.nosql.mongodb.registry.InMemoryMongoConnectionRegistry
import com.orbitalhq.preflight.dsl.containers.ContainerSupport
import com.orbitalhq.preflight.dsl.containers.DefaultContainerSupport
import com.orbitalhq.query.connectors.OperationInvoker
import com.orbitalhq.schema.api.SimpleSchemaProvider
import com.orbitalhq.schemas.taxi.TaxiSchema
import io.kotest.extensions.testcontainers.ContainerExtension
import io.micrometer.core.instrument.simple.SimpleMeterRegistry
import org.testcontainers.containers.GenericContainer
import org.testcontainers.utility.DockerImageName
import org.testcontainers.utility.MountableFile

class MongoContainerSupport(
    container: MongoDbContainer,
    containerExtension: ContainerExtension<*>,
    connectionName: String,
    invokerFactory: (schema: TaxiSchema) -> OperationInvoker,
) : DefaultContainerSupport(containerExtension, connectionName, container, invokerFactory) {

    val connectionString = "mongodb://${container.host}:${container.getMappedPort(27017)}"

    fun mongoClient(): MongoClient {
        val clientSettings = MongoClientSettings.builder()
            .applyConnectionString(ConnectionString(connectionString))
            .build()
        return MongoClients.create(clientSettings)
    }
}

fun mongoConnector(
    connectionName: String = "mongoConnection",

    ): ContainerSupport {
    val container = MongoDbContainer()
        .withExposedPorts(27017)
        .withCopyToContainer(
            MountableFile.forClasspathResource("./init-schema.js"),
            "/docker-entrypoint-initdb.d/init-script.js"
        )
    return DefaultContainerSupport(
        ContainerExtension(container),
        connectionName,
        container,
    ) { schema ->
        val connectionString =
            "mongodb://test_container:test_container@${container.host}:${container.firstMappedPort}/user_management"
        val connectionParams = mapOf(MongoConnection.Parameters.CONNECTION_STRING.templateParamName to connectionString)
        val mongoConnectionConfig = MongoConnectionConfiguration(connectionName, connectionParams)
        val connectionRegistry = InMemoryMongoConnectionRegistry(listOf(mongoConnectionConfig))
        val connectionFactory = MongoConnectionFactory(connectionRegistry, SimpleMeterRegistry())
        MongoDbInvoker(
            connectionFactory,
            SimpleSchemaProvider(schema),
            SimpleMeterRegistry()
        )
    }
}

class MongoDbContainer : GenericContainer<MongoDbContainer>(DockerImageName.parse("mongo:6.0.7"))
