// A standalone consumer: install Izar at the repository root (`./mvnw install`) and publish the
// Gradle plugin (`./gradlew -p izar-gradle-plugin publishToMavenLocal`) before building this project.
pluginManagement {
    repositories {
        mavenLocal()
        mavenCentral()
        gradlePluginPortal()
    }
}

rootProject.name = "nightsky-gradle-client"
