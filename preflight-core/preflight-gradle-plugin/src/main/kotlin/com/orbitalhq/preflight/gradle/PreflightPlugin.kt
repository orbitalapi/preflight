package com.orbitalhq.preflight.gradle

import com.orbitalhq.preflight.Versions
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.artifacts.ModuleDependency
import org.gradle.api.tasks.SourceSetContainer
import org.gradle.api.tasks.testing.Test
import java.net.URI

typealias OrbitalVersion = String

open class PreflightExtension {
    var orbitalVersion: OrbitalVersion = "0.36.0-M9"  // current default
    var connectors: List<ConnectorSupport> = emptyList()
}

class PreflightPlugin : Plugin<Project> {
    override fun apply(project: Project) {
        project.pluginManager.apply("org.jetbrains.kotlin.jvm")
        project.extensions.configure<org.jetbrains.kotlin.gradle.dsl.KotlinJvmProjectExtension>("kotlin") {
            jvmToolchain(21)
        }

        // Register the extension
        val extension = project.extensions.create("preflight", PreflightExtension::class.java)

        project.repositories.mavenCentral()
        project.repositories.maven {
            name = "orbital"
            url = URI("https://repo.orbitalhq.com/release")
            mavenContent {
                releasesOnly()
            }
        }
        project.repositories.maven {
            name = "orbital-snapshot"
            url = URI("https://repo.orbitalhq.com/snapshot")
            mavenContent {
                snapshotsOnly()
            }
        }


        val sourceSets = project.extensions.getByName("sourceSets") as SourceSetContainer
        val mainRuntimeClasspath = sourceSets.getByName("main").runtimeClasspath
        val mainSourceSet = sourceSets.getByName("main")

        val testSourceSet = sourceSets.maybeCreate("test")
        testSourceSet.resources.srcDir("test-resources")
        testSourceSet.java.srcDir("test")  // top-level test directory
        testSourceSet.compileClasspath += mainSourceSet.output + mainSourceSet.compileClasspath
        testSourceSet.runtimeClasspath += mainSourceSet.output + mainSourceSet.runtimeClasspath

        project.tasks.withType(Test::class.java).configureEach {
            useJUnitPlatform()
            // Custom test directory support
            testClassesDirs = testSourceSet.output.classesDirs
            classpath = testSourceSet.runtimeClasspath
        }

        // Move dependency declarations to afterEvaluate block to access extension values
        project.afterEvaluate {
            val orbitalVersion = extension.orbitalVersion


            // Core dependencies
            project.dependencies.add("implementation", "org.jetbrains.kotlin:kotlin-stdlib")
            project.dependencies.add("implementation", "com.orbitalhq.preflight:preflight-runtime:${Versions.PREFLIGHT_VERSION}")
            project.dependencies.add("testImplementation", "io.kotest:kotest-runner-junit5:5.8.0")
            project.dependencies.add("testImplementation", "io.kotest:kotest-assertions-core:5.8.0")
            
            // Orbital dependencies (moved from preflight-runtime)
            val taxiqlEngine = project.dependencies.create("com.orbitalhq:taxiql-query-engine:$orbitalVersion") as ModuleDependency
            taxiqlEngine.exclude(mapOf("group" to "org.pac4j"))
            taxiqlEngine.exclude(mapOf("group" to "org.jooq.pro"))
            project.dependencies.add("implementation", taxiqlEngine)
            
            val taxiqlEngineTests = project.dependencies.create("com.orbitalhq:taxiql-query-engine:$orbitalVersion:tests") as ModuleDependency
            taxiqlEngineTests.exclude(mapOf("group" to "org.pac4j"))
            taxiqlEngineTests.exclude(mapOf("group" to "org.jooq.pro"))
            project.dependencies.add("implementation", taxiqlEngineTests)
            
            val playgroundCore = project.dependencies.create("com.orbitalhq:taxi-playground-core:$orbitalVersion") as ModuleDependency
            playgroundCore.exclude(mapOf("group" to "io.confluent"))
            playgroundCore.exclude(mapOf("group" to "org.jooq.pro"))
            playgroundCore.exclude(mapOf("group" to "org.pac4j"))
            project.dependencies.add("implementation", playgroundCore)
            
            val schemaServerCore = project.dependencies.create("com.orbitalhq:schema-server-core:$orbitalVersion") as ModuleDependency
            schemaServerCore.exclude(mapOf("group" to "io.confluent"))
            schemaServerCore.exclude(mapOf("group" to "org.pac4j"))
            schemaServerCore.exclude(mapOf("group" to "org.jooq.pro"))
            project.dependencies.add("implementation", schemaServerCore)
            
            project.dependencies.add("implementation", "org.opentest4j:opentest4j:1.3.0")
            project.dependencies.add("implementation", "app.cash.turbine:turbine-jvm:0.12.1")


            // add in any container support
            project.dependencies.add("implementation", project.dependencies.platform("org.testcontainers:testcontainers-bom:1.19.3"))

            extension.connectors.forEach { connector ->
                connector.configureBuild(orbitalVersion,project)
            }
        }
    }
}
