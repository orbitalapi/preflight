
plugins {
    kotlin("jvm")
    `maven-publish`
}

val taxiVersion = "1.64.0"
val orbitalVersion = "0.35.0"

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
    }
    api("com.orbitalhq:taxiql-query-engine:$orbitalVersion") {
        artifact { classifier = "tests" }
        // Not published to maven central, and not needed for testing
        // as it relates to saml auth
        exclude(group = "org.pac4j")
    }
    implementation("io.github.microutils:kotlin-logging-jvm:3.0.5")
    implementation(platform("io.kotest:kotest-bom:5.8.0"))
    implementation("io.kotest:kotest-framework-api")
    implementation("io.kotest:kotest-framework-engine")
    implementation("io.kotest:kotest-framework-datatest")
    implementation("io.kotest:kotest-framework-discovery")
    implementation("io.kotest:kotest-assertions-core")


    testImplementation(platform("org.junit:junit-bom:5.10.0"))
    testImplementation("org.junit.jupiter:junit-jupiter")
}

tasks.test {
    useJUnitPlatform()
}