# Izar

[![Build](https://github.com/glisseo-dev/project-izar/actions/workflows/build.yml/badge.svg)](https://github.com/glisseo-dev/project-izar/actions/workflows/build.yml)
[![CodeQL](https://github.com/glisseo-dev/project-izar/actions/workflows/codeql.yml/badge.svg)](https://github.com/glisseo-dev/project-izar/actions/workflows/codeql.yml)
[![Dependabot](https://img.shields.io/badge/dependabot-enabled-025e8c?logo=dependabot)](.github/dependabot.yml)
[![License](https://img.shields.io/github/license/glisseo-dev/project-izar)](LICENSE)
[![OpenSSF Scorecard](https://api.scorecard.dev/projects/github.com/glisseo-dev/project-izar/badge)](https://scorecard.dev/viewer/?uri=github.com/glisseo-dev/project-izar)
[![Java 25](https://img.shields.io/badge/Java-25-orange)](#compatibility)

Izar is a type-safe Java GraphQL client and code generator for Spring Boot and
Spring for GraphQL. It generates immutable Java records from the queries,
mutations, and subscriptions in your .graphql files, with typed variables,
input builders, and exactly the response fields each operation selects. Maven
and Gradle plugins generate the code at build time.
Generated operations run through the Spring GraphQlClient your application
already configures, synchronously or reactively, so authentication,
interceptors, timeouts, and connection settings remain in your existing Spring
configuration. Generated classes depend only on the JDK and Izar's small
operation contract, keeping the generated code lightweight.

## Features

- **Operation-first code generation.** Each `.graphql` operation file becomes
  an immutable Java record, with builders for variables and input objects.
  The generated metadata records whether the operation is a query, a
  mutation, or a subscription.
- **JDK-only generated code.** A generated operation class depends on the JDK
  and Izar's small operation contract, nothing else.
- **Your Spring GraphQL client.** Izar wraps the `GraphQlClient` bean your
  application already defines. Authentication, timeouts, interceptors, and
  connection pooling stay in that client's configuration.
- **Persisted-ID execution.** A generated operation can send its SHA-256 ID
  instead of its document, over `RestClient`, `WebClient`, and HTTP SSE. Any
  Spring GraphQL server can resolve those IDs through GraphQL Java's
  `PreparsedDocumentProvider`; [`examples/nightsky-default-sb-server`](examples/nightsky-default-sb-server/)
  shows one.

## Example

This operation file:

```graphql
query GetVisibleNow($locationId: ID!) {
  visibleNow(locationId: $locationId) {
    id
    name
    magnitude
  }
}
```

generates the class `GetVisibleNowQuery`. Run it with a
`SynchronousGraphQlOperations` or `ReactiveGraphQlOperations` bean built from
your `GraphQlClient`:

```java
GetVisibleNowQuery query = GetVisibleNowQuery.builder().locationId("oslo").build();
GetVisibleNowQuery.Data data = operations.execute(query).assertNoErrors();
```

[`examples/`](examples/) has complete projects: a server, and synchronous,
reactive, and Jackson clients.

## Get started

1. To generate Java from your schema and `.graphql` files at build time, add
   [`izar-maven-plugin`](izar-maven-plugin/) or
   [`izar-gradle-plugin`](izar-gradle-plugin/).
2. To execute the generated operations, add [`izar-client`](izar-client/) and
   wrap your `GraphQlClient` bean in `SynchronousGraphQlOperations` or
   `ReactiveGraphQlOperations`.
3. If your schema uses `DateTime`, `Date`, or `BigDecimal` custom scalars, add
   [`izar-scalars`](izar-scalars/). For other scalars, implement
   `ScalarCodec` from [`izar-operation`](izar-operation/).

Step 3 is optional. Each module's README covers its configuration and
dependencies.

## Modules

Izar is a Maven multi-module build, plus a standalone Gradle build for the
Gradle plugin. Add only the modules your application needs.

| Module | Responsibility |
| --- | --- |
| [`izar-operation`](izar-operation/) | The contract generated operations implement. It depends only on the JDK and usually arrives as a transitive dependency. |
| [`izar-compiler`](izar-compiler/) | Build-time generation of models, executable documents, IDs, and manifests, independent of the build tool. |
| [`izar-maven-plugin`](izar-maven-plugin/) | Maven adapter over the compiler and manifest publication. |
| [`izar-gradle-plugin`](izar-gradle-plugin/) | Gradle adapter with the same options and workflow as the Maven plugin. A standalone Gradle build outside the Maven reactor. |
| [`izar-client`](izar-client/) | Executes generated operations through a Spring GraphQL client, synchronously and reactively. |
| [`izar-scalars`](izar-scalars/) | Optional custom-scalar mappings for common GraphQL Java Extended Scalars types. |
| [`izar-manifest`](izar-manifest/) | Apollo-compatible manifest format, SHA-256 validation, imported manifests with opaque IDs, and the `ManifestSource` interface. Shared by the compiler and client. |

[`integration-tests/`](integration-tests/) holds the consumer projects behind
the test suite. They sit outside the Maven reactor, so they resolve Izar the
way a real application does.

## Build from source

You need a Java 25 JDK. The build uses the Maven wrapper, so you can skip
installing Maven.

```bash
./mvnw verify
```

### Run the examples

[`examples/`](examples/) uses a small stargazing schema. A standalone server
enforces an allowlist built from what its clients send. The clients are
synchronous, reactive, and Jackson-based. The project
`nightsky-release-assembly` merges two clients' published manifests into one
deployable manifest. Install the modules first, then follow each example's
README:

```bash
./mvnw install
```

## Compatibility

Izar targets Java 25, Spring Boot 4.1.1, Spring for GraphQL 2.0.5, and
GraphQL Java 25.0. The root POM imports these versions from the Spring Boot
BOM. In your application, Izar uses the Spring, Reactor, Jackson, and GraphQL
Java versions that your own Boot BOM resolves.

## License

Izar is licensed under the [Apache License 2.0](LICENSE).
