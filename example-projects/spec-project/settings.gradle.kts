pluginManagement {
    repositories {
        mavenCentral()
        mavenLocal()
        gradlePluginPortal()
        maven { url = uri("https://repo.orbitalhq.com/release") }
    }
    includeBuild("../../preflight-core")
}

rootProject.name = "spec-project"
includeBuild("../../preflight-core")
