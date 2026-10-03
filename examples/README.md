# Izar examples

Six projects built around one small schema, Nightsky: constellations, and
the stars and deep-sky objects in them. A night cycle runs faster than real
time, so the visible objects change while a client watches. Izar's acceptance
suite is a separate project,
[`integration-tests/bookstore-consumer`](../integration-tests/bookstore-consumer),
with its own books schema.

- [`nightsky-default-sb-server`](nightsky-default-sb-server): a plain Spring
  Boot GraphQL server that resolves persisted IDs through a graphql-java
  persisted-query document provider. Run this first.
- [`nightsky-sync-client`](nightsky-sync-client): browses the catalog, checks
  what is visible now, logs an observation, and streams the sky by persisted
  ID, through Izar's synchronous and reactive adapters.
- [`nightsky-reactive-client`](nightsky-reactive-client): subscribes to the
  visible sky with the full GraphQL document and prints server-pushed updates
  through Izar's reactive `ReactiveGraphQlOperations` adapter.
- [`nightsky-jackson-client`](nightsky-jackson-client): maps the polymorphic
  visible-sky response with Spring GraphQL's `toEntityList` and Jackson-mode
  generated records.
- [`nightsky-gradle-client`](nightsky-gradle-client): the only example built
  with Gradle. It generates through `izar-gradle-plugin` and serves the sky
  catalog, visible objects, and observations over REST.
- [`nightsky-release-assembly`](nightsky-release-assembly): a Maven-only
  build with no application of its own. It resolves the two clients'
  published manifest artifacts and assembles them into one union manifest.

The examples sit outside the root Maven reactor and resolve Izar from
installed artifacts, the way an application would. Run `./mvnw install` at the
repository root first, then follow each example's README. The Gradle client
also needs the Gradle plugin in your local Maven repository, and its README
shows how.

Each server and client has its own `spring-boot-starter-parent`. The Gradle
client applies the Spring Boot Gradle plugin instead.
`nightsky-release-assembly` has no application code and no Spring dependency,
because it only runs `izar-maven-plugin`'s `assemble` and `attach` goals.

The four clients also serve HTTP. Each has `spring-boot-starter-web` and a
REST controller that wraps the operations its startup story runs, so a client
keeps running after the story finishes. Stop it with Ctrl-C.
