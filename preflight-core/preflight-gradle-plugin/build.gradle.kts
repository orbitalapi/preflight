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
val taxiVersion = "1.64.0"
val orbitalVersion = "0.35.0"

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

publishing {
    repositories {
        maven {
            name = "localTest"
            url = uri("${project.rootDir}/../maven-repo")
        }
    }
}

tasks.shadowJar {
    archiveClassifier.set("")  // Make this the main JAR
    dependencies {
        include(project(":preflight-runtime"))
        include(dependency("org.taxilang:.*"))
        include(dependency("com.orbitalhq:.*"))
    }
}

signing {
    useInMemoryPgpKeys(
        System.getenv("GPG_PRIVATE_KEY"),
        System.getenv("GPG_PASSPHRASE")
    )
    sign(publishing.publications)
}
