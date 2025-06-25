plugins {
    `kotlin-dsl`
    `java-gradle-plugin`
    `maven-publish`
    signing
    id("com.gradleup.shadow") version "9.0.0-beta17"
    id("com.gradle.plugin-publish") version "1.3.1"
}

buildscript {
    repositories {
        gradlePluginPortal()
        mavenCentral()
    }
}

repositories {
    mavenCentral()
    mavenLocal()
    gradlePluginPortal()
    maven { url = uri("https://repo.orbitalhq.com/release") }
}

dependencies {
    implementation("org.jetbrains.kotlin:kotlin-gradle-plugin:1.9.23")
    implementation(project(":preflight-runtime"))
    testImplementation(platform("org.junit:junit-bom:5.10.0"))
    testImplementation("org.junit.jupiter:junit-jupiter")

}

tasks.test {
    useJUnitPlatform()
}


gradlePlugin {
    website.set("https://github.com/orbitalapi/preflight")
    vcsUrl.set("https://github.com/orbitalapi/preflight")
    plugins {
        create("preflight") {
            id = "com.orbitalhq.preflight"
            implementationClass = "com.orbitalhq.preflight.gradle.PreflightPlugin"
            displayName = "Orbital Preflight Gradle Plugin"
            description = "Gradle plugin for running tests against Orbital and Taxi projects"
            tags.set(listOf("preflight", "taxi", "orbital"))
        }
    }
}

tasks.shadowJar {
    archiveClassifier.set("")  // Make this the main JAR
    dependencies {
        include(project(":preflight-runtime"))
        include(dependency("org.taxilang:.*"))
        include(dependency("com.orbitalhq:.*"))
        include(dependency("com.orbitalhq.preflight:.*"))
    }
}

signing {
    val gpgPrivateKey = System.getenv("GPG_PRIVATE_KEY")
    val gpgPassphrase = System.getenv("GPG_PASSPHRASE")

    if (gpgPrivateKey != null && gpgPassphrase != null) {
        // GitHub Actions path - use environment variables
        useInMemoryPgpKeys(gpgPrivateKey, gpgPassphrase)
    } else {
        // Local development path - use gradle.properties
        // Gradle will automatically pick up signing.keyId and signing.password
    }
    sign(publishing.publications)
}

publishing {
    repositories {
        mavenLocal()
        maven {
            name = "orbital"
            url = if (version.toString().endsWith("SNAPSHOT")) {
                uri("s3://repo.orbitalhq.com/snapshot")
            } else {
                uri("s3://repo.orbitalhq.com/release")
            }
            credentials(AwsCredentials::class) {
                accessKey = providers.environmentVariable("AWS_ACCESS_KEY_ID").orNull
                secretKey = providers.environmentVariable("AWS_SECRET_ACCESS_KEY").orNull
            }
        }
    }
}

// Capture the version at script level (configuration time)
//val projectVersion = project.version.toString()
/**
 * Generates a Versions.kt file containing the current version of this project
 * which we can use inside the plugin code
 */
val generateVersionConstants by tasks.registering {
    description = "Generate version constants for plugin"

    val outputDir = layout.buildDirectory.dir("generated/kotlin")
    val outputFile = outputDir.get().file("com/orbitalhq/preflight/Versions.kt")

    // Use Provider API - this is configuration cache safe
    val versionProvider = providers.provider { version.toString() }

    inputs.property("version", versionProvider)
    outputs.file(outputFile)

    doLast {
        val versionString = versionProvider.get()
        outputFile.asFile.parentFile.mkdirs()
        outputFile.asFile.writeText("""
            package com.orbitalhq.preflight
            
            object Versions {
                const val PREFLIGHT_VERSION = "$versionString"
            }
        """.trimIndent())
    }
}

tasks.compileKotlin {
    dependsOn(generateVersionConstants)
}
// Fix the sourcesJar dependency issue - only if the task exists
tasks.matching { it.name == "sourcesJar" }.configureEach {
    dependsOn(generateVersionConstants)
}
// Add the generated source to the source set
kotlin {
    sourceSets {
        main {
            kotlin.srcDir(layout.buildDirectory.dir("generated/kotlin"))
        }
    }
}