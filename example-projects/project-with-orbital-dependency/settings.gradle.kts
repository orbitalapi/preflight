pluginManagement {
    repositories {
        mavenCentral()
        mavenLocal()
        gradlePluginPortal()
        maven { url = uri("https://repo.orbitalhq.com/release") }
    }
    includeBuild("../../preflight-core")
}

rootProject.name = "project-with-orbital-dependency"
includeBuild("../../preflight-core")