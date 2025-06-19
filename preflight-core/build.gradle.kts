import org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17
import org.jetbrains.kotlin.gradle.dsl.KotlinJvmProjectExtension
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile
import java.net.URI

plugins {
    `kotlin-dsl`
    `java-gradle-plugin`
    `maven-publish`
}




allprojects {
    group = "com.orbitalhq.preflight"
    version = "0.1.0-SNAPSHOT"

    repositories {
        mavenCentral()
        maven {
            name = "orbital"
            url = URI("https://repo.orbitalhq.com/release")
            mavenContent {
                releasesOnly()
            }
        }
        maven {
            name = "orbital-snapshot"
            url = URI("https://repo.orbitalhq.com/snapshot")
            mavenContent {
                snapshotsOnly()
            }
        }
    }
}

subprojects {

    apply(plugin = "org.jetbrains.kotlin.jvm")

    dependencies {
        implementation(kotlin("stdlib"))
    }
    plugins.withId("org.jetbrains.kotlin.jvm") {
        extensions.configure<KotlinJvmProjectExtension>() {
            jvmToolchain(17)
        }

    }

}

val taxiVersion = "1.64.0"
val orbitalVersion = "0.35.0"


// dependencies {
//     implementation("org.taxilang:compiler:$taxiVersion")
//     implementation("org.taxilang:compiler:$taxiVersion") {
//         artifact { classifier = "tests" }
//     }
//     implementation("com.orbitalhq:taxiql-query-engine:$orbitalVersion") {
//         // Not published to maven central, and not needed for testing
//         // as it relates to saml auth
//         exclude(group = "org.pac4j")
//     }
//     implementation("com.orbitalhq:taxiql-query-engine:$orbitalVersion") {
//         artifact { classifier = "tests" }   // Not published to maven central, and not needed for testing
//         // as it relates to saml auth
//         exclude(group = "org.pac4j")
//     }
//     implementation("io.github.microutils:kotlin-logging-jvm:3.0.5")
//     implementation(platform("io.kotest:kotest-bom:5.8.0"))
//     implementation("org.jetbrains.kotlin:kotlin-gradle-plugin:1.9.23")
//     implementation("io.kotest:kotest-framework-api")
//     implementation("io.kotest:kotest-framework-engine")
//     implementation("io.kotest:kotest-framework-datatest")
//     implementation("io.kotest:kotest-framework-discovery")
//     implementation("io.kotest:kotest-assertions-core")
//     testImplementation(platform("org.junit:junit-bom:5.10.0"))
//     testImplementation("org.junit.jupiter:junit-jupiter")

// }

// tasks.test {
//     useJUnitPlatform()
// }

// gradlePlugin {
//     plugins {
//         create("preflight") {
//             id = "com.orbitalhq.preflight"
//             implementationClass = "com.orbitalhq.preflight.build.PreflightPlugin"
//         }
//     }
// }

// publishing {
//     repositories {
//         maven {
//             name = "localTest"
//             url = uri("${project.rootDir}/../maven-repo")
//         }
//     }
// }
