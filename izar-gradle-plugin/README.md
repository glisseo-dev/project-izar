# Izar Gradle plugin

The Izar Gradle plugin generates Java code from your GraphQL schema and
`.graphql` operation files before `compileJava` runs. Each operation becomes an
immutable Java record with builders for its variables, ready to run with
[`izar-client`](../izar-client/) on a Spring for GraphQL `GraphQlClient`. The
plugin also writes an Apollo-compatible operation manifest and can publish it
to an Izar controller or to a Maven repository.

It has the same options and produces the same output as
[`izar-maven-plugin`](../izar-maven-plugin/). The same operation gets the same
operation ID and generated Java under either build tool.

## Install the plugin

Izar is not on a public repository yet. Install the Izar modules, then publish
the plugin to your local Maven repository. This project is a standalone Gradle
build outside the root Maven reactor, and it resolves `izar-compiler` and
`izar-manifest` from the installed artifacts.

```sh
./mvnw install
./izar-gradle-plugin/gradlew -p izar-gradle-plugin publishToMavenLocal
```

Then resolve the plugin from `mavenLocal()` in your `settings.gradle.kts`:

```kotlin
pluginManagement {
    repositories {
        mavenLocal()
        gradlePluginPortal()
    }
}
```

## Add the plugin

```kotlin
plugins {
    java
    id("dev.glisseo.izar") version "0.1.0-SNAPSHOT"
}

izar {
    basePackage = "com.example.graphql"
}
```

In Groovy DSL, write `id 'dev.glisseo.izar' version '0.1.0-SNAPSHOT'` and
`izar { basePackage = 'com.example.graphql' }`.

The plugin adds the generated-sources directory to the `main` source set, so
`compileJava` runs `izarGenerate` first. Your project also needs
`dev.glisseo.izar:izar-operation` on the compile classpath, which
[`izar-client`](../izar-client/) brings in.

## Tasks

| Task | Purpose | Maven goal |
| --- | --- | --- |
| `izarGenerate` | Generates Java sources and `build/izar/manifest.json`. `compileJava` depends on it. | `generate` |
| `izarPublish` | Registers the manifest with a controller. No other task depends on it. | `publish` |
| `izarAttach` | Validates the manifest and ships it with the project's `maven-publish` publications. Registered when `attach { enabled = true }`. | `attach` |
| `izarDeploy` | Uploads the manifest as a Maven artifact under its own coordinates. No other task depends on it. | `deploy` |
| `izarAssemble` | Resolves exact client releases and assembles one union manifest. | `assemble` |

You can set any option in the `izar { }` block or with a Gradle property named
`izar.<option>`, such as `-Pizar.schema=...`, `-Pizar.skip=true`, or
`-Pizar.publish.url=...`. A value in the `izar { }` block wins over the
property. Nested options keep the Maven names, for example
`izar.deploy.repositoryUrl`.

## Generate Java from operation files

Put your schema at `src/main/graphql/schema.graphqls` and your `.graphql`
operation files anywhere under `src/main/graphql`. Each file holds one named
operation: a query, a mutation, or a subscription. The schema must be a file in
your project or an unpacked schema artifact. The plugin does not introspect a
running server. The schema and operation files are task inputs, so
`izarGenerate` is up to date until one changes.

`izarGenerate` writes Java sources to `build/generated-sources/izar` and the
manifest to `build/izar/manifest.json`.

| Option | Property | Default |
| --- | --- | --- |
| `basePackage` (required) | `izar.basePackage` | none |
| `schema` | `izar.schema` | `src/main/graphql/schema.graphqls` |
| `operationDirectory` | `izar.operations` | `src/main/graphql` |
| `outputDirectory` | `izar.outputDirectory` | `build/generated-sources/izar` |
| `manifestFile` | `izar.manifestFile` | `build/izar/manifest.json` |
| `manifestInput` | `izar.manifestInput` | none |
| `generationMode` | `izar.generationMode` | `"IZAR"` |
| `scalarMappings` | none | empty |
| `skip` | `izar.skip` | `false` |

`izar.skip=true` skips the task and leaves existing generated files and
manifests as they are.

### Generate from an existing manifest

To generate from an Apollo-compatible manifest instead of operation files, set
`manifestInput`. The manifest replaces `operationDirectory` as the source of
operations. The schema stays required, because the compiler validates each
operation against it and builds the Java types from it. Izar keeps each
operation's ID and exact document text, and in this mode it does not write
`manifestFile`.

```kotlin
izar {
    basePackage = "com.example.graphql"
    manifestInput = layout.projectDirectory.file("src/main/resources/manifest.json")
}
```

The compiler applies the same rules to manifest entries as under Maven. See
[the Maven plugin](../izar-maven-plugin/README.md#generate-from-an-existing-manifest).

### Choose a generation mode

`generationMode` takes `IZAR` (the default) or `JACKSON`, in any letter case.
`JACKSON` maps response paths with Spring GraphQL's `toEntity` methods:

```kotlin
izar {
    basePackage = "com.example.graphql"
    generationMode = "JACKSON"
}
```

Each mode generates the same code as under Maven. See
[the Maven plugin](../izar-maven-plugin/README.md#choose-a-generation-mode) for
the differences.

### Map custom scalars

GraphQL built-in scalars need no configuration. Map each custom scalar to a
Java type and a codec:

```kotlin
izar {
    basePackage = "com.example.graphql"
    scalarMappings {
        register("DateTime") {
            javaTypeName = "java.time.Instant"
            codecClassName = "dev.glisseo.izar.scalars.InstantScalarCodec"
        }
    }
}
```

`javaTypeName` must be fully qualified, because generated code adds no import
for it. `codecClassName` is any class that implements
`dev.glisseo.izar.operation.ScalarCodec<T>` and has a public no-argument
constructor. Use one from [`izar-scalars`](../izar-scalars/) or write your own.
If an operation reaches a custom scalar that has no mapping, generation fails
and names the scalar.

### Generate for multiple GraphQL servers

The `izar { }` extension supplies defaults, and each task holds its own
options. Register one `IzarGenerateTask` per server. Give each a distinct Java
package, output directory, and manifest file, and add its output directory to
the source set:

```kotlin
import dev.glisseo.izar.gradle.IzarGenerateTask

val generateServerA = tasks.register<IzarGenerateTask>("izarGenerateServerA") {
    schema = layout.projectDirectory.file("src/main/graphql/server-a/schema.graphqls")
    operationDirectory = layout.projectDirectory.dir("src/main/graphql/server-a")
    basePackage = "com.example.graphql.servera"
    outputDirectory = layout.buildDirectory.dir("generated-sources/server-a")
    manifestFile = layout.buildDirectory.file("izar/server-a/manifest.json")
}

sourceSets.main {
    java.srcDir(generateServerA.flatMap { it.outputDirectory })
}
```

A task falls back to the `izar { }` values for any option it omits, so a shared
`scalarMappings` block applies to every task. To add a mapping to one task
only, call
`scalarMapping("DateTime", "java.time.Instant", "dev.glisseo.izar.scalars.InstantScalarCodec")`
on it. A property such as `-Pizar.schema=...` has one value for the whole
invocation, so use task-level configuration when the schemas differ.

`compileJava` always depends on the default `izarGenerate`. When every server
has its own task, disable the default one:

```kotlin
tasks.named("izarGenerate") { enabled = false }
```

## Publish a manifest to a controller

`izarPublish` sends the manifest to the release endpoint of an
Izar controller:

```sh
./gradlew izarPublish \
    -Pizar.publish.url=http://localhost:8080 \
    -Pizar.publish.clientName=catalog \
    -Pizar.publish.manifestVersion=2026.09
```

You can also set the options in the build script:

```kotlin
izar {
    publish {
        url = "http://localhost:8080"
        clientName = "catalog"
        manifestVersion = "2026.09"
    }
}
```

`url`, `clientName`, and `manifestVersion` are required. No other task depends
on `izarPublish`, so `build` and `publishToMavenLocal` never publish. By
default it publishes the manifest `izarGenerate` writes, and Gradle runs
`izarGenerate` first, so the file is never stale. To publish a manifest from
another Apollo-compatible pipeline, set `publish { manifestFile }` or
`-Pizar.publish.manifestFile`. The task then reads and validates that file
only. `publish { skip = true }` skips the task.

The task has no password option, so credentials never appear in
`build.gradle`, on the command line, or in build output. Supply them in one of
two ways:

- Set the `IZAR_PUBLISH_USERNAME` and `IZAR_PUBLISH_PASSWORD` environment
  variables. Set both or neither.
- Set `publish { credentialsId = "controller" }`, the counterpart of Maven's
  `serverId`. Gradle then reads the properties `controllerUsername` and
  `controllerPassword` from `~/.gradle/gradle.properties`, from
  `ORG_GRADLE_PROJECT_*` environment variables, or from the command line.

When both are present, the environment variables win.

A successful run logs the registered revision and operation count.
Registration does not mean a server has activated the revision.

## Share manifests through Maven repositories

`izarPublish` talks to a controller over authenticated HTTP. `izarAttach`,
`izarDeploy`, and `izarAssemble` instead move manifests as versioned Maven
artifacts through the repositories your organization already runs. You can use
either route, both, or neither.

### Attach a manifest to the project

Attaching is opt-in and works with the `maven-publish` plugin:

```kotlin
plugins {
    java
    `maven-publish`
    id("dev.glisseo.izar") version "0.1.0-SNAPSHOT"
}

izar {
    basePackage = "com.example.graphql"
    attach {
        enabled = true
    }
}

publishing {
    publications {
        register<MavenPublication>("maven") {
            from(components["java"])
        }
    }
}
```

`izarAttach` validates the manifest, and every `MavenPublication` then carries
it as an extra artifact. `attach { classifier }` defaults to `izar-manifest`
and `attach { extension }` to `json`. `attach { manifestFile }` selects another
manifest file, and `attach { skip = true }` skips the task.

The attachment uses the project's own group, name, and version. A project
`com.example:catalog-client:1.2.3` produces
`com.example:catalog-client:json:izar-manifest:1.2.3`. The standard publishing
tasks upload it:

```sh
./gradlew publishToMavenLocal
./gradlew publish
```

To attach several manifests, register more `IzarAttachTask` instances, each
with a distinct `classifier` and `manifestFile`.

### Deploy a manifest under its own coordinates

Use `izarDeploy` when a manifest needs Maven coordinates independent of the
project:

```sh
./gradlew izarDeploy \
    -Pizar.deploy.groupId=com.example.manifests \
    -Pizar.deploy.artifactId=catalog \
    -Pizar.deploy.version=2026.09 \
    -Pizar.deploy.repositoryId=releases \
    -Pizar.deploy.repositoryUrl=https://repo.example.internal/releases
```

Or set the options in the build script:

```kotlin
izar {
    deploy {
        groupId = "com.example.manifests"
        artifactId = "catalog"
        version = "2026.09"
        repositoryId = "releases"
        repositoryUrl = "https://repo.example.internal/releases"
    }
}
```

`groupId`, `artifactId`, and `version` identify this manifest. They usually
encode the client name and manifest version, so a deployment build can later
resolve the exact release. `repositoryId` and `repositoryUrl` name the target.
`classifier` (default `izar-manifest`) and `extension` (default `json`) rarely
need changing. By default the task deploys the manifest `izarGenerate` writes,
and Gradle runs `izarGenerate` first.

The plugin applies `maven-publish` and creates a publication named
`izarManifest`. It uploads only when `izarDeploy` is in the task graph.
`publish` and `publishToMavenLocal` skip it, and the deployment repository
never receives the project's own publications. The version must be exact. A
range, a dynamic version, or `LATEST` fails the build before anything uploads.

The task has no credential option. For an `http` or `https` repository, Gradle
reads `<repositoryId>Username` and `<repositoryId>Password` (`releasesUsername`
and `releasesPassword` above) from `~/.gradle/gradle.properties`, from
`ORG_GRADLE_PROJECT_*` environment variables, or from the command line, as it
does for every `maven-publish` repository. A `file:` repository needs none.

### Assemble one manifest from several client releases

A deployment build can resolve exact client releases and merge them into one
union manifest with `izarAssemble`:

```kotlin
repositories {
    maven("https://repo.example.internal/releases")
}

izar {
    assemble {
        graph = "storefront"
        environment = "production"
        releases {
            register("catalog") {
                manifestVersion = "2026.09"
                groupId = "com.example.manifests"
                artifactId = "catalog"
                version = "2026.09"
            }
            register("checkout") {
                manifestVersion = "2026.10"
                groupId = "com.example.manifests"
                artifactId = "checkout"
                version = "2026.10"
            }
        }
    }
}
```

Run it with `./gradlew izarAssemble`. Each release's name is its `clientName`.
The release pairs that client identity with the Maven coordinates that supply
its manifest. `classifier` and `extension` default as they do for
`izarDeploy`. The task resolves releases through the build's own
`repositories { }`, so your repository and credential configuration already
applies. Every version must be exact: a range, a dynamic version such as `1.+`,
or `LATEST` fails the build.

The task tries every release even after one fails, so one run reports every
missing coordinate. When all releases resolve, it writes `manifest.json`,
`provenance.json`, and `lock.json` to `assemble { outputDirectory }` (default
`build/izar/assembled`). Any resolution failure, missing release, or
conflicting release fails the build with no partial output.

## Differences from the Maven plugin

| Maven | Gradle |
| --- | --- |
| `<configuration>` and `-Dizar.<option>` | `izar { }` and `-Pizar.<option>` |
| One execution per GraphQL server | One `IzarGenerateTask` per GraphQL server |
| `generate` bound to `generate-sources` | `compileJava` depends on `izarGenerate` |
| `serverId` names a `settings.xml` server | `credentialsId` prefixes the `<id>Username` and `<id>Password` Gradle properties |
| `publish`, `attach`, and `deploy` read the file `generate` wrote | The same, and Gradle runs `izarGenerate` first when they use that default file |
| `attach` is active once its execution is declared | `attach { enabled = true }` |
| `deploy` goes through `RepositorySystem.deploy` | `izarDeploy` goes through a `maven-publish` publication |
| `generationMode` is an enum | `generationMode` is the string `IZAR` or `JACKSON` |
| `assemble` resolves through `RepositorySystem` | `izarAssemble` and `izarDeploy` read the live project and task graph, so neither works with the configuration cache |

## Distribute a manifest without a controller

If a server loads its manifest from a file or a static host, `izarGenerate`
alone produces everything you need. `izarPublish`, `izarDeploy`,
`izarAssemble`, and the controller are all optional.

## Develop the plugin

```sh
./mvnw install -DskipTests
./izar-gradle-plugin/gradlew -p izar-gradle-plugin test
```

The tests run each task through Gradle TestKit against throwaway builds. They
include a loopback controller for `izarPublish` and a local `file:` Maven
repository for `izarDeploy` and `izarAssemble`.

See the [root README](../README.md) for the other Izar modules, and
[`examples/nightsky-gradle-client`](../examples/nightsky-gradle-client/) for a
complete project.
