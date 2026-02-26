import org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17
import org.jetbrains.kotlin.gradle.dsl.KotlinJvmProjectExtension
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile
import java.net.URI

plugins {
    `kotlin-dsl`
    `java-gradle-plugin`
    `maven-publish`
}



tasks.register("publishAll") {
    dependsOn(subprojects.map { "${it.path}:publish" })
    group = "publishing"
    description = "Publishes all subprojects"
}

tasks.register("publishAllMavenLocal") {
    dependsOn(subprojects.map { "${it.path}:publishToMavenLocal" })
    group = "publishing"
    description = "Publishes all subprojects"
}


allprojects {
    group = "com.orbitalhq.preflight"
    version = "0.1.0-M1"

    repositories {
        mavenCentral()
        mavenLocal()
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
            jvmToolchain(21)
        }
    }

    plugins.withId("maven-publish") {
        extensions.configure<PublishingExtension> {
            publications.withType<MavenPublication> {
                pom {
                    licenses {
                        license {
                            name.set("The Apache License, Version 2.0")
                            url.set("https://www.apache.org/licenses/LICENSE-2.0.txt")
                        }
                    }
                }
            }
        }
    }

}
