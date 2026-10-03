plugins {
    `java-gradle-plugin`
    `maven-publish`
}

group = "dev.glisseo.izar"
// The release workflow passes -PizarVersion=<tag version>, the same value it gives Maven as
// -Drevision, so a release commits no version change.
val izarVersion = providers.gradleProperty("izarVersion").getOrElse("0.1.0-SNAPSHOT")
version = izarVersion
description = "The Gradle adapter over izar-compiler and izar-manifest."

repositories {
    // izar-compiler and izar-manifest come from `./mvnw install` at the repository root, the
    // same way every example resolves Izar.
    mavenLocal()
    mavenCentral()
}

java {
    sourceCompatibility = JavaVersion.VERSION_25
    targetCompatibility = JavaVersion.VERSION_25
}

dependencies {
    implementation("dev.glisseo.izar:izar-compiler:$izarVersion")
    implementation("dev.glisseo.izar:izar-manifest:$izarVersion")
    compileOnly("org.jspecify:jspecify:1.0.0")

    testImplementation(platform("org.junit:junit-bom:6.1.3"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
    testImplementation("org.assertj:assertj-core:3.27.7")
    testImplementation(gradleTestKit())
}

gradlePlugin {
    plugins {
        create("izar") {
            id = "dev.glisseo.izar"
            implementationClass = "dev.glisseo.izar.gradle.IzarPlugin"
            displayName = "Izar"
            description = "Generates Java from GraphQL operations and publishes, deploys, and assembles Izar manifests."
        }
    }
}

tasks.test {
    useJUnitPlatform()
}
