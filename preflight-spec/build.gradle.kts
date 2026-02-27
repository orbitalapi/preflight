plugins {
    kotlin("jvm") version "2.1.10"
    `maven-publish`
}

group = "com.orbitalhq.preflight"
version = "0.1.0-SNAPSHOT"

repositories {
    mavenCentral()
    mavenLocal()
}

kotlin {
    jvmToolchain(21)
}

dependencies {
    implementation(kotlin("stdlib"))
    implementation("org.commonmark:commonmark:0.24.0")
    implementation("com.fasterxml.jackson.module:jackson-module-kotlin:2.17.2")

    testImplementation(platform("io.kotest:kotest-bom:5.8.0"))
    testImplementation("io.kotest:kotest-runner-junit5")
    testImplementation("io.kotest:kotest-assertions-core")
    testImplementation("io.kotest:kotest-assertions-json")
}

tasks.test {
    useJUnitPlatform()
}

publishing {
    publications {
        create<MavenPublication>("maven") {
            from(components["java"])
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
