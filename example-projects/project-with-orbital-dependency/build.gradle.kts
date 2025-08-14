import com.orbitalhq.preflight.gradle.ConnectorSupport
import java.net.URI


plugins {
    id("com.orbitalhq.preflight")
}

preflight {
    connectors = listOf(ConnectorSupport.Kafka,ConnectorSupport.MongoDb)
}

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