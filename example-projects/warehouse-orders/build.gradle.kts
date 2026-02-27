import java.net.URI

plugins {
   kotlin("jvm") version "2.2.21"
   id("com.orbitalhq.preflight") version "0.1.0-M2"
}

// OR specify a custom Orbital version
preflight {
   orbitalVersion = "0.38.0-M4"
}

repositories {
   mavenLocal()
   mavenCentral()
//   maven {
//      name = "orbital"
//      url = URI("https://repo.orbitalhq.com/release")
//      mavenContent {
//         releasesOnly()
//      }
//   }
//   maven {
//      name = "orbital-snapshot"
//      url = URI("https://repo.orbitalhq.com/snapshot")
//      mavenContent {
//         snapshotsOnly()
//      }
//   }
}
