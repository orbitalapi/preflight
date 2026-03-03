plugins {
    kotlin("jvm")
    `maven-publish`
}

val taxiVersion = "1.71.0-SNAPSHOT"
val orbitalVersion = "0.38.0-SNAPSHOT" // Default version, can be overridden in consumer projects

dependencies {
    implementation("com.orbitalhq.preflight:preflight-spec")
    testImplementation(platform("org.junit:junit-bom:5.10.0"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    implementation(platform("org.testcontainers:testcontainers-bom:1.19.3"))

    compileOnly("org.taxilang:compiler:$taxiVersion")
    compileOnly("org.taxilang:compiler:$taxiVersion") {
        artifact { classifier = "tests" }
    }

    // Containers / Invokers support
    compileOnly("com.orbitalhq:kafka-connector:$orbitalVersion") {
        // Drop all Confluent-specific artifacts
        exclude(group = "io.confluent")
    }
    compileOnly("org.testcontainers:kafka")
    compileOnly("com.orbitalhq:mongodb-connector:$orbitalVersion")

    compileOnly("com.orbitalhq:taxiql-query-engine:$orbitalVersion") {
        // Not published to maven central, and not needed for testing
        // as it relates to saml auth
        exclude(group = "org.pac4j")

        // This might need adding (and shading), but later...
        // Can we avoid by just using the OSS version in Preflight?
        exclude(group = "org.jooq.pro")
        exclude(group = "org.pac4j")
    }
    compileOnly("com.orbitalhq:taxiql-query-engine:$orbitalVersion") {
        artifact { classifier = "tests" }
        // Not published to maven central, and not needed for testing
        // as it relates to saml auth
        exclude(group = "org.pac4j")

        // This might need adding (and shading), but later...
        // Can we avoid by just using the OSS version in Preflight?
        exclude(group = "org.jooq.pro")
        exclude(group = "org.pac4j")
    }
    api("org.opentest4j:opentest4j:1.3.0")
    compileOnly("com.orbitalhq:taxi-playground-core:$orbitalVersion") {
        exclude(group = "io.confluent")
        exclude(group = "org.jooq.pro")
        exclude(group = "org.pac4j")
    }

    api("app.cash.turbine:turbine-jvm:0.12.1")
    compileOnly("com.orbitalhq:schema-server-core:$orbitalVersion") {
        // This could become an issue - but this isn't published to maven central
        // If we end up needing this, we'll need to configure
        //  <repositories>
        //      <repository>
        //         <id>confluent</id>
        //         <url>https://packages.confluent.io/maven/</url>
        //      </repository>
        //   </repositories>
        exclude(group = "io.confluent")
        // As above
        exclude(group = "org.pac4j")

        // This might need adding (and shading), but later...
        // Can we avoid by just using the OSS version in Preflight?
        exclude(group = "org.jooq.pro")
    }
    implementation("io.github.microutils:kotlin-logging-jvm:3.0.5")
    implementation(platform("io.kotest:kotest-bom:5.8.0"))
    implementation("io.kotest:kotest-framework-api")
    implementation("io.kotest:kotest-framework-engine")
    implementation("io.kotest:kotest-framework-datatest")
    implementation("io.kotest:kotest-framework-discovery")
    implementation("io.kotest:kotest-assertions-core")
    implementation("io.kotest.extensions:kotest-extensions-testcontainers:2.0.2")

    testImplementation("io.kotest:kotest-runner-junit5")


    testImplementation(platform("org.junit:junit-bom:5.10.0"))
    testImplementation("org.junit.jupiter:junit-jupiter")
}

tasks.test {
    useJUnitPlatform()
    systemProperty("kotest.framework.classpath.scanning.config.disable", "false")
}

publishing {
    publications {
        create<MavenPublication>("maven") {
            from(components["java"])

            // Optional: customize the publication
//            artifactId = "preflight-runtime"
            // groupId and version are inherited from the project
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


// Force any request for kafka-clients to resolve to the Apache build
configurations.all {
    resolutionStrategy.eachDependency {
        if (requested.group == "org.apache.kafka" && requested.name == "kafka-clients") {
            useVersion("3.9.1")
            because("Avoid Confluent's -ccs build, use Apache Kafka client instead")
        }
        // Replace commercial JOOQ with OSS version
        if (requested.group == "org.jooq.pro" && requested.name == "jooq") {
            useTarget("org.jooq:jooq:${requested.version}")
            because("Use OSS JOOQ instead of commercial version")
        }
    }
}
