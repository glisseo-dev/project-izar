# Nightsky Gradle client

The Gradle counterpart of the Maven-built clients: a small Spring Boot web service that
asks the Nightsky server for the sky catalog, what is visible now, and logged observations
through Izar's `SynchronousGraphQlOperations`. It is the only example built with Gradle, so it
shows [`izar-gradle-plugin`](../../izar-gradle-plugin) end to end:

- `id("dev.glisseo.izar")` generates the Java for the three operations in
  [`src/main/graphql`](src/main/graphql) before `compileJava`.
- `scalarMappings` maps the `DateTime` scalar to `java.time.Instant` with
  `izar-scalars`' `InstantScalarCodec`, so `GetObservations` decodes `observedAt`.
- `attach { enabled = true }` ships the generated manifest next to the jar as the
  `nightsky-gradle-manifest` artifact when the project is published.
- `./gradlew izarPublish` registers the manifest with a controller, the way the Maven
  clients' `publish` goal does.

The operations are the same documents the Maven sync client sends, so they get the same
operation IDs under either build tool.

This project is deliberately outside the root Maven reactor (see
[`../../README.md`](../../README.md)): it depends on Izar the way a real consumer would, from
installed artifacts, and applies the Spring Boot Gradle plugin instead of a
`spring-boot-starter-parent`.

## Running it

From the repository root, install the Izar modules and publish the Gradle plugin to your
local Maven repository, then start [`nightsky-default-sb-server`](../nightsky-default-sb-server) in one terminal:

```bash
./mvnw install
./izar-gradle-plugin/gradlew -p izar-gradle-plugin publishToMavenLocal
./mvnw -f examples/nightsky-default-sb-server/pom.xml spring-boot:run
```

```powershell
.\mvnw.cmd install
.\izar-gradle-plugin\gradlew.bat -p izar-gradle-plugin publishToMavenLocal
.\mvnw.cmd -f examples\nightsky-default-sb-server\pom.xml spring-boot:run
```

Then, in a second terminal, run this client:

```bash
./examples/nightsky-gradle-client/gradlew -p examples/nightsky-gradle-client bootRun
```

```powershell
.\examples\nightsky-gradle-client\gradlew.bat -p examples\nightsky-gradle-client bootRun
```

It serves the REST endpoints below on `http://localhost:8085`. Stop it with Ctrl-C.

## REST endpoints

[`SkyController`](src/main/java/dev/glisseo/izar/examples/nightsky/gradle/SkyController.java)
exposes the generated operations as JSON:

| Endpoint | Description |
| --- | --- |
| `GET /catalog` | Every constellation, with how many objects it contains. |
| `GET /visible-now?locationId=` | What's currently visible from a viewing location. |
| `GET /observations?locationId=` | Observations logged at a viewing location. |
| `GET /health` | A liveness check for Docker Compose. |
