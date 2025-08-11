package com.orbitalhq.preflight.dsl.containers.kafka

import com.orbitalhq.avro.AvroFormatSpec
import com.orbitalhq.connectors.config.kafka.KafkaConnectionConfiguration
import com.orbitalhq.connectors.kafka.KafkaConsumerStatsFlowBuilder
import com.orbitalhq.connectors.kafka.KafkaInvoker
import com.orbitalhq.connectors.kafka.KafkaStreamManager
import com.orbitalhq.connectors.kafka.KafkaStreamPublisher
import com.orbitalhq.connectors.kafka.registry.InMemoryKafkaConnectorRegistry
import com.orbitalhq.metrics.GaugeRegistry
import com.orbitalhq.models.format.DefaultFormatRegistry
import com.orbitalhq.preflight.dsl.containers.ContainerSupport
import com.orbitalhq.preflight.dsl.containers.DefaultContainerSupport
import com.orbitalhq.protobuf.ProtobufFormatSpec
import com.orbitalhq.query.connectors.OperationInvoker
import com.orbitalhq.schema.consumer.SimpleSchemaStore
import com.orbitalhq.schemas.taxi.TaxiSchema
import io.kotest.extensions.testcontainers.ContainerExtension
import io.micrometer.core.instrument.simple.SimpleMeterRegistry
import mu.KotlinLogging
import org.apache.kafka.clients.producer.KafkaProducer
import org.apache.kafka.clients.producer.ProducerConfig
import org.apache.kafka.clients.producer.ProducerRecord
import org.apache.kafka.clients.producer.RecordMetadata
import org.apache.kafka.common.header.Header
import org.apache.kafka.common.serialization.ByteArraySerializer
import org.apache.kafka.common.serialization.StringSerializer
import org.testcontainers.containers.wait.strategy.Wait
import org.testcontainers.containers.KafkaContainer
import org.testcontainers.utility.DockerImageName
import java.nio.charset.Charset
import java.time.Duration
import java.time.Instant
import java.util.*
import kotlin.random.Random


class KafkaContainerSupport(
    container: KafkaContainer,
    containerExtension: ContainerExtension<*>,
    connectionName: String,
    invokerFactory: (schema: TaxiSchema) -> OperationInvoker,
) : DefaultContainerSupport(containerExtension, connectionName, container, invokerFactory) {
    companion object {
        private val logger = KotlinLogging.logger {}
    }

    private val kafkaContainer: KafkaContainer = container as KafkaContainer

    fun producer(): KafkaProducer<String, ByteArray> {
        val props = Properties()
        props.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, kafkaContainer.bootstrapServers)
        props.put(ProducerConfig.CLIENT_ID_CONFIG, "KafkaProducer-${Instant.now().toEpochMilli()}")
        props.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer::class.java.name)
        props.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, ByteArraySerializer::class.java.name)
        props.put(ProducerConfig.REQUEST_TIMEOUT_MS_CONFIG, 1000)
        return KafkaProducer<String, ByteArray>(props)
    }

    fun sendMessage(
        message: String,
        topic: String,
        key: String = UUID.randomUUID().toString(),
        headers: List<Header> = emptyList()
    ): RecordMetadata {
        val byteArray = message.toByteArray(Charset.defaultCharset())
        return sendMessage(byteArray, topic, key, headers)
    }

    fun sendMessage(
        message: ByteArray,
        topic: String,
        key: String = UUID.randomUUID().toString(),
        headers: List<Header> = emptyList()
    ): RecordMetadata {
        logger.info { "Sending message to topic $topic" }
        val metadata = producer().send(
            ProducerRecord(
                topic,
                null, // partition
                key,
                message,
                headers
            )
        )
            .get()
        logger.info { "message sent to topic $topic with offset ${metadata.offset()}" }
        return metadata
    }
}

fun kafkaContainer(
    connectionName: String = "kafkaConnection",
    groupId: String = "orbitalTest-" + Random.nextInt()
): ContainerSupport {
    val container = KafkaContainer(DockerImageName.parse("confluentinc/cp-kafka:7.6.1")
        .asCompatibleSubstituteFor("apache/kafka")
    )
        .withNetworkAliases("kafka")
        .withStartupTimeout(Duration.ofMinutes(2))
        .waitingFor(Wait.forListeningPort())

    val containerExtension: ContainerExtension<KafkaContainer> = ContainerExtension(container)
    val formatRegistry = DefaultFormatRegistry(listOf(ProtobufFormatSpec, AvroFormatSpec))
    return KafkaContainerSupport(
        container,
        containerExtension,
        connectionName,
    ) { schema ->
        val schemaStore = SimpleSchemaStore.forSchema(schema)
        val connectionConfig = KafkaConnectionConfiguration(
            connectionName,
            container.bootstrapServers,
            groupId,
            emptyMap()
        )
        val registry = InMemoryKafkaConnectorRegistry(
            listOf(connectionConfig)
        )
        val kafkaStreamPublisher = KafkaStreamPublisher(
            registry,
            formatRegistry = formatRegistry,
            meterRegistry = SimpleMeterRegistry()
        )
        val kafkaStreamManager = KafkaStreamManager(
            registry,
            schemaStore,
            formatRegistry = formatRegistry,
            meterRegistry = SimpleMeterRegistry(),
            emitConsumerInfoMessages = false,
            kafkaConsumerStatsFlowBuilder = KafkaConsumerStatsFlowBuilder(GaugeRegistry.simple())
        )
        KafkaInvoker(kafkaStreamManager, kafkaStreamPublisher)
    }
}