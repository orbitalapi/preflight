package com.orbitalhq.preflight.gradle

import org.gradle.api.Project

enum class ConnectorSupport(val configureBuild: (OrbitalVersion, Project) -> Unit) {
    Kafka({ orbitalVersion, project ->
        // Add Kafka connector
        val kafkaConnector =
            project.dependencies.create("com.orbitalhq:kafka-connector:$orbitalVersion") as org.gradle.api.artifacts.ModuleDependency
        kafkaConnector.exclude(mapOf("group" to "io.confluent"))
        project.dependencies.add("implementation", kafkaConnector)

        // Add TestContainers Kafka support
        project.dependencies.add("implementation", "org.testcontainers:kafka")

        // Force resolution strategy for kafka-clients to use Apache build instead of Confluent
        project.configurations.configureEach {
            resolutionStrategy.eachDependency {
                if (requested.group == "org.apache.kafka" && requested.name == "kafka-clients") {
                    useVersion("3.9.1")
                    because("Avoid Confluent's -ccs build, use Apache Kafka client instead")
                }
            }
        }
    })
}