import org.springframework.boot.gradle.plugin.SpringBootPlugin

plugins {
    java
    `maven-publish`
    id("org.springframework.boot") version "4.1.1"
    id("dev.glisseo.izar") version "0.1.0-SNAPSHOT"
}

group = "dev.glisseo.izar.examples"
version = "0.1.0-SNAPSHOT"

val izarVersion = "0.1.0-SNAPSHOT"

java {
    sourceCompatibility = JavaVersion.VERSION_25
    targetCompatibility = JavaVersion.VERSION_25
}

repositories {
    mavenLocal()
    mavenCentral()
}

dependencies {
    implementation(platform(SpringBootPlugin.BOM_COORDINATES))
    implementation("dev.glisseo.izar:izar-client:$izarVersion")
    implementation("dev.glisseo.izar:izar-operation:$izarVersion")
    // Supplies the InstantScalarCodec the DateTime mapping below instantiates.
    implementation("dev.glisseo.izar:izar-scalars:$izarVersion")
    implementation("org.springframework.boot:spring-boot-starter-web")
}

izar {
    // schema and operationDirectory keep the plugin's defaults: src/main/graphql.
    basePackage = "dev.glisseo.izar.examples.nightsky.gradle.generated"
    scalarMappings {
        register("DateTime") {
            javaTypeName = "java.time.Instant"
            codecClassName = "dev.glisseo.izar.scalars.InstantScalarCodec"
        }
    }
    // Ships the manifest next to the jar, the way the Maven examples' attach goal does.
    attach {
        enabled = true
        classifier = "nightsky-gradle-manifest"
    }
}

publishing {
    publications {
        register<MavenPublication>("maven") {
            from(components["java"])
        }
    }
}
