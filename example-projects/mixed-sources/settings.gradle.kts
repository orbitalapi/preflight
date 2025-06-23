pluginManagement {
    repositories {
        mavenCentral()
        mavenLocal()
        gradlePluginPortal()
        maven { url = uri("https://repo.orbitalhq.com/release") }
    }
    includeBuild("../../preflight-core")
}

rootProject.name = "mixed-sources"
includeBuild("../../preflight-core")