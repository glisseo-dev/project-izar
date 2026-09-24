# Izar examples

Four runnable projects built around one small stargazing schema, Nightsky:
constellations, the stars and deep-sky objects that make them up, and a
compressed night cycle that makes the sky visibly rotate while you watch.
They show what building against Izar looks like. Izar's acceptance suite is
a separate project, [`integration-tests/one-query-consumer`](../integration-tests/one-query-consumer),
with its own books-themed fixture data.

- [`nightsky-server`](nightsky-server): a standalone Spring GraphQL server,
  enforcing an allowlist of what the two clients below actually send. Run
  this first.
- [`nightsky-default-sb-server`](nightsky-default-sb-server): a plain Spring Boot GraphQL server with a graphql-java persisted-query
  document provider, with no `izar-server` or `izar-controller` dependency.
- [`nightsky-sync-client`](nightsky-sync-client): browses the catalog, checks
  what's visible right now, logs an observation, and streams the sky by
  persisted ID, through Izar's synchronous and reactive adapters.
- [`nightsky-reactive-client`](nightsky-reactive-client): subscribes to the
  visible sky with the full GraphQL document and prints server-pushed updates
  through Izar's reactive `ReactiveGraphQlOperations` adapter.
- [`nightsky-release-assembly`](nightsky-release-assembly): a Maven-only
  deployment build with no application of its own; it resolves the two
  clients' published manifest artifacts and assembles them into one
  deployable union manifest.

Each is deliberately outside the root Maven reactor: an example resolves
Izar the way a real consumer does, from installed artifacts, not from being
built alongside Izar's own source. The server and the two clients each have
their own ordinary `spring-boot-starter-parent`; `nightsky-release-assembly`
has no application code and no Spring dependency at all, since it only
exercises `izar-maven-plugin`'s `assemble` and `attach` goals. Run
`./mvnw install` at the repository root first so those artifacts exist
locally, then follow each module's own README to run it.

## Why a stargazing theme

Nightsky's schema is built to exercise several distinct decoding cases at
once: a `CelestialObject` interface (`Star`, `Nebula`, `Galaxy`) for
polymorphic decoding, an enum output field, and a custom `Coordinates`
scalar with no shipped codec. The compressed night cycle, where different
objects are visible from one moment to the next, gives the reactive client
something real to poll instead of a synthetic heartbeat.
