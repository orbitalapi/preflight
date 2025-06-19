package com.orbitalhq.preflight.gradle

import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.tasks.SourceSetContainer
import org.gradle.api.tasks.testing.Test
import java.net.URI

class PreflightPlugin : Plugin<Project> {
    override fun apply(project: Project) {
        project.pluginManager.apply("org.jetbrains.kotlin.jvm")
        project.extensions.configure<org.jetbrains.kotlin.gradle.dsl.KotlinJvmProjectExtension>("kotlin") {
            jvmToolchain(21)
        }

        project.repositories.mavenCentral()
        project.repositories.maven {
            name = "orbital"
            url = URI("https://repo.orbitalhq.com/release")
            mavenContent {
                releasesOnly()
            }
        }


        project.dependencies.add("implementation", "org.jetbrains.kotlin:kotlin-stdlib")
        project.dependencies.add("implementation", "com.orbitalhq.preflight:preflight-runtime:0.1.0-SNAPSHOT")
        project.dependencies.add("testImplementation", "io.kotest:kotest-runner-junit5:5.8.0")
        project.dependencies.add("testImplementation", "io.kotest:kotest-assertions-core:5.8.0")

        val sourceSets = project.extensions.getByName("sourceSets") as SourceSetContainer
        val mainRuntimeClasspath = sourceSets.getByName("main").runtimeClasspath
        val mainSourceSet = sourceSets.getByName("main")

        val testSourceSet = sourceSets.maybeCreate("test")
        testSourceSet.java.srcDir("test")  // top-level test directory
        testSourceSet.compileClasspath += mainSourceSet.output + mainSourceSet.compileClasspath
        testSourceSet.runtimeClasspath += mainSourceSet.output + mainSourceSet.runtimeClasspath


        project.tasks.withType(Test::class.java).configureEach {
            useJUnitPlatform()
            // Custom test directory support
            testClassesDirs = testSourceSet.output.classesDirs
            classpath = testSourceSet.runtimeClasspath
        }
    }
}
