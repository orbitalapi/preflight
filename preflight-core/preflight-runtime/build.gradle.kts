plugins {
    kotlin("jvm")
    `maven-publish`
}

val taxiVersion = "1.66.0-SNAPSHOT"
val orbitalVersion = "0.36.0-M9"

dependencies {
    testImplementation(platform("org.junit:junit-bom:5.10.0"))
    testImplementation("org.junit.jupiter:junit-jupiter")

    api("org.taxilang:compiler:$taxiVersion")
    api("org.taxilang:compiler:$taxiVersion") {
        artifact { classifier = "tests" }
    }
    api("com.orbitalhq:taxiql-query-engine:$orbitalVersion") {
        // Not published to maven central, and not needed for testing
        // as it relates to saml auth
        exclude(group = "org.pac4j")

        // This might need adding (and shading), but later...
        // Can we avoid by just using the OSS version in Preflight?
        exclude(group = "org.jooq.pro")
        exclude(group = "org.pac4j")
    }
    api("com.orbitalhq:taxiql-query-engine:$orbitalVersion") {
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
    api("com.orbitalhq:taxi-playground-core:$orbitalVersion") {
        exclude(group = "io.confluent")
        exclude(group = "org.jooq.pro")
        exclude(group = "org.pac4j")
    }

    api("app.cash.turbine:turbine-jvm:0.12.1")
    implementation("com.orbitalhq:schema-server-core:$orbitalVersion") {
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
