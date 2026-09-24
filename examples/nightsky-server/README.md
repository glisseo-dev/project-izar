# Nightsky server

A standalone Spring GraphQL server serving the Nightsky stargazing schema
([`src/main/resources/graphql/schema.graphqls`](src/main/resources/graphql/schema.graphqls)):
five real constellations, the stars, nebulae, and one galaxy that make them
up, three viewing locations, a mutation for logging what you saw, and a
subscription that streams the visible sky every three seconds. It is
the target [`nightsky-sync-client`](../nightsky-sync-client) and
[`nightsky-reactive-client`](../nightsky-reactive-client) run against, and it
enforces an operation allowlist through `izar-server`, built from what those
two clients actually send. See
[Enforcing an allowlist](#enforcing-an-allowlist) below.

This project is deliberately outside the root Maven reactor (see
[`../../README.md`](../../README.md) at the repository root and
[`integration-tests/README.md`](../../integration-tests/README.md) for the
same reasoning applied to Izar's own test suite): it is a real, independently
runnable application with its own ordinary `spring-boot-starter-parent`,
resolving `izar-server` as an installed artifact the same way
`nightsky-sync-client`/`nightsky-reactive-client` resolve `izar-client`. Run
`./mvnw install` at the repository root first so that artifact exists
locally.

## Running it

```bash
./mvnw -f examples/nightsky-server/pom.xml spring-boot:run
```

```powershell
.\mvnw.cmd -f examples\nightsky-server\pom.xml spring-boot:run
```

The server listens on `http://localhost:8083/graphql`, with GraphiQL
available at `http://localhost:8083/graphiql` for exploring the schema by
hand. Leave it running, then start either client in a second terminal.

## Enforcing an allowlist

`izar.graphql.server.mode: enforce` in [`application.yml`](src/main/resources/application.yml)
turns on `izar-server`'s allowlisting against
[`izar/manifest.json`](izar/manifest.json), a committed manifest listing
every operation `nightsky-sync-client` and `nightsky-reactive-client`
actually generate. Both clients work normally against it: try either one
against the running server and watch their operations succeed.

A GraphQL operation neither client sends gets rejected instead. With the server running,
try one over GraphiQL or `curl`:

```bash
curl -s http://localhost:8083/graphql -H 'Content-Type: application/json' \
  -d '{"query":"query AdHoc{constellations{name}}"}'
```

```json
{"errors":[{"message":"This operation is not registered for execution against this endpoint.", ...}],
 "extensions":{"izar":{"registered":false,"mode":"ENFORCE","revision":"..."}}}
```

Every response, allowed or not, carries this `izar` extension, `registered`
and the currently active manifest `revision`, regardless of whether the
request succeeded.

### Regenerating the manifest

Whenever either client's operations change, regenerate `izar/manifest.json`
from their own generated manifests rather than hand-editing it:

```bash
./mvnw -f examples/nightsky-sync-client/pom.xml generate-sources
./mvnw -f examples/nightsky-reactive-client/pom.xml generate-sources
node examples/nightsky-server/izar/merge-manifests.mjs
```

(run the third command from `examples/nightsky-server/`, or adjust its
relative paths). [`merge-manifests.mjs`](izar/merge-manifests.mjs)
deduplicates by operation id (`GetViewingLocations` and `GetVisibleNow` are
sent by both clients) and fails loudly if the same id ever disagreed on its
document body. `izar-server` only ever reads the committed output, never the
clients' own `target/izar/manifest.json`, so this module stays runnable on
its own with no build-order dependency on either client.

## The compressed night cycle

Real Earth rotation takes 24 hours to bring every object through a full
rise-and-set cycle, far too slow to watch live. [`NightSky`](src/main/java/dev/glisseo/izar/examples/nightsky/server/NightSky.java)
instead compresses one full night into 60 seconds, measured from server
startup: `visibleNow` returns a different set of objects every few seconds,
and `visibleSky` pushes those snapshots through an HTTP SSE subscription.
[`nightsky-reactive-client`](../nightsky-reactive-client) watches the full
document operation, while [`nightsky-sync-client`](../nightsky-sync-client)
watches by persisted ID. Longitude still behaves the way it does for a real
night: two `ViewingLocation`s far apart in longitude see different objects
visible at the same instant, offset by one hour per 15 degrees.

## Custom scalars

`DateTime` (on `Observation.observedAt`) is GraphQL Java Extended Scalars'
published implementation, registered as a real (not test-only) dependency
since this is a real server process. `Coordinates` (on
`ViewingLocation.coordinates`) has no published implementation: [`GeoCoordinatesCoercing`](src/main/java/dev/glisseo/izar/examples/nightsky/server/GeoCoordinatesCoercing.java)
is a hand-written `graphql.schema.Coercing`, coercing a `"latitude,longitude"`
string to and from a `GeoCoordinates` record, the same wire format each
client's own `GeoCoordinatesScalarCodec` decodes.

## Interface resolution

`Star`, `Nebula`, and `Galaxy` all implement the schema's `CelestialObject`
interface. Spring GraphQL's default type resolver matches each returned
object to a GraphQL type by its Java simple class name, so no explicit type
resolver is registered; [`NightskyGraphQlController`](src/main/java/dev/glisseo/izar/examples/nightsky/server/NightskyGraphQlController.java)'s
single `@SchemaMapping(typeName = "CelestialObject", field = "constellation")`
method resolves that field for all three concrete types at once, since none
of them stores a direct object reference back to its `Constellation` (see
that class's Javadoc for why: a record's generated `equals`/`hashCode`
recurses into every component, and a genuine bidirectional reference between
the two would recurse forever).
