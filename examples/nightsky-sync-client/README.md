# Nightsky sync client

A runnable console client that browses the Nightsky catalog, checks what's
visible right now, logs an observation, reads it back, then receives three
sky subscription events by persisted ID. It uses Izar's synchronous
`SynchronousGraphQlOperations` adapter for catalog and observation calls.

This project is deliberately outside the root Maven reactor (see
[`../../README.md`](../../README.md)): it depends on Izar the way a real
consumer would, as installed artifacts with its own ordinary
`spring-boot-starter-parent`.

## Running it

From the repository root, install the Izar modules this project depends on,
then start [`nightsky-default-sb-server`](../nightsky-default-sb-server) in one terminal:

```bash
./mvnw install
./mvnw -f examples/nightsky-default-sb-server/pom.xml spring-boot:run
```

```powershell
.\mvnw.cmd install
.\mvnw.cmd -f examples\nightsky-default-sb-server\pom.xml spring-boot:run
```

Then, in a second terminal, run this client:

```bash
./mvnw -f examples/nightsky-sync-client/pom.xml spring-boot:run
```

```powershell
.\mvnw.cmd -f examples\nightsky-sync-client\pom.xml spring-boot:run
```

It prints the full constellation catalog, what's currently visible from a
fixed viewing location, logs a sample observation against whatever is first
in that list, then reads the observation log back. It receives three
`WatchVisibleSky` subscription events by persisted ID. Unlike earlier
versions of this example, the process then keeps running instead of exiting,
serving the REST endpoints below on `http://localhost:8081`. Stop it with
Ctrl-C.

## REST endpoints

[`SkyExplorerController`](src/main/java/dev/glisseo/izar/examples/nightsky/SkyExplorerController.java)
wraps the same [`NightskyGraphQlFacade`](src/main/java/dev/glisseo/izar/examples/nightsky/NightskyGraphQlFacade.java)
calls the startup story already makes, as plain JSON:

| Endpoint | Description |
| --- | --- |
| `GET /catalog` | The full constellation catalog. |
| `GET /visible-now?locationId=` | What's currently visible from a viewing location. |
| `POST /observations` | Log an observation: `{"locationId", "objectId", "conditions", "notes"}`. |
| `GET /observations?locationId=` | The observation log for a viewing location. |

## Build and publish the manifest artifact

The build generates sources during `generate-sources` and attaches the generated
manifest during `package`. The attached artifact uses this project's own
coordinates and the configured `nightsky-sync-manifest` classifier:

```text
dev.glisseo.izar.examples:nightsky-sync-client:json:nightsky-sync-manifest:0.1.0-SNAPSHOT
```

Build the package and inspect the attachment in `target`:

```bash
./mvnw -f examples/nightsky-sync-client/pom.xml package
```

Install it in the local Maven repository:

```bash
./mvnw -f examples/nightsky-sync-client/pom.xml install
```

The installed file is
`~/.m2/repository/dev/glisseo/izar/examples/nightsky-sync-client/0.1.0-SNAPSHOT/nightsky-sync-client-0.1.0-SNAPSHOT-nightsky-sync-manifest.json`.
Use the explicit `izar:deploy` goal instead when the manifest must use
coordinates independent of this project.

## Application-configured client

[`GraphQlClientConfiguration`](src/main/java/dev/glisseo/izar/examples/nightsky/GraphQlClientConfiguration.java)
hand-wires a Spring `GraphQlClient` and wraps it in Izar's
`SynchronousGraphQlOperations`: Izar never constructs or owns the transport,
so authentication, timeouts, or interceptors would be configured on the
`GraphQlClient` bean the same way any Spring application would, with no
Izar-specific mechanism involved. A second `WebClient` feeds
`ReactiveGraphQlOperations.persistedQuery` for the SSE subscription, which
sends the operation ID without its document.

## Request/response logging

[`LoggingClientHttpRequestInterceptor`](src/main/java/dev/glisseo/izar/examples/nightsky/LoggingClientHttpRequestInterceptor.java)
logs every GraphQL request and response at `INFO`, wired onto the
`HttpSyncGraphQlClient`'s underlying `RestClient` in
`GraphQlClientConfiguration` via `restClient(builder ->
builder.requestInterceptor(...))`. See that class's own Javadoc for why it's
hand-written rather than a shipped Spring component.

## Polymorphic decoding

[`GetVisibleNow.graphql`](src/main/graphql/GetVisibleNow.graphql) selects
`CelestialObject`'s shared fields plus a type-conditioned fragment per
concrete type (`Star`, `Nebula`, `Galaxy`). The generated `VisibleNow` sealed
interface (`VisibleNowStar`, `VisibleNowNebula`, `VisibleNowGalaxy`, and
`VisibleNowUnrecognized` for a future type this client doesn't know about
yet) is exhaustively pattern-matched in [`SkyExplorerRunner`](src/main/java/dev/glisseo/izar/examples/nightsky/SkyExplorerRunner.java).

## A custom scalar with no shipped codec

`izar-scalars` ships a codec for `DateTime` (`InstantScalarCodec`, used for
`Observation.observedAt`), but the schema's `Coordinates` scalar is specific
to this example, so [`GeoCoordinatesScalarCodec`](src/main/java/dev/glisseo/izar/examples/nightsky/GeoCoordinatesScalarCodec.java)
is an ordinary application-supplied `dev.glisseo.izar.operation.ScalarCodec`,
configured on `izar-maven-plugin` exactly like a shipped one:

```xml
<scalarMapping>
  <graphqlScalarName>Coordinates</graphqlScalarName>
  <javaTypeName>dev.glisseo.izar.examples.nightsky.GeoCoordinates</javaTypeName>
  <codecClassName>dev.glisseo.izar.examples.nightsky.GeoCoordinatesScalarCodec</codecClassName>
</scalarMapping>
```
