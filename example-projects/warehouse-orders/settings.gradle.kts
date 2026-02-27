rootProject.name = "warehouse-orders"

pluginManagement {
   repositories {
      mavenCentral()
      mavenLocal()
      gradlePluginPortal()
      maven { url = uri("https://repo.orbitalhq.com/release") }
   }
   includeBuild("../../preflight-core")
}
includeBuild("../../preflight-core")