# Nightsky reactive client

A runnable console client that subscribes to `WatchVisibleSky` and prints
server-pushed sky snapshots through Izar's reactive
`ReactiveGraphQlOperations` adapter.

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
./mvnw -f examples/nightsky-reactive-client/pom.xml spring-boot:run
```

```powershell
.\mvnw.cmd -f examples\nightsky-reactive-client\pom.xml spring-boot:run
```

It watches for about 72 seconds, a bit more than one full compressed night
cycle (see [`nightsky-default-sb-server`](../nightsky-default-sb-server)'s README), printing each
subscription event and calling out what rose or set since the last event. It
sends the full GraphQL document. Unlike earlier versions of this example, the
process then keeps running instead of exiting, serving the streaming endpoint
below on `http://localhost:8082`. Stop it with Ctrl-C.

## REST endpoint

[`LiveSkyController`](src/main/java/dev/glisseo/izar/examples/nightsky/LiveSkyController.java)
proxies the same `WatchVisibleSky` subscription as server-sent events:

| Endpoint | Description |
| --- | --- |
| `GET /visible-sky/stream?locationId=` | Streams visible-sky snapshots for a viewing location as SSE. |

## Build and publish the manifest artifact

The build generates sources during `generate-sources` and attaches the generated
manifest during `package`. The attached artifact uses this project's own
coordinates and the configured `nightsky-reactive-manifest` classifier:

```text
dev.glisseo.izar.examples:nightsky-reactive-client:json:nightsky-reactive-manifest:0.1.0-SNAPSHOT
```

Build the package and inspect the attachment in `target`:

```bash
./mvnw -f examples/nightsky-reactive-client/pom.xml package
```

Install it in the local Maven repository:

```bash
./mvnw -f examples/nightsky-reactive-client/pom.xml install
```

The installed file is
`~/.m2/repository/dev/glisseo/izar/examples/nightsky-reactive-client/0.1.0-SNAPSHOT/nightsky-reactive-client-0.1.0-SNAPSHOT-nightsky-reactive-manifest.json`.
[`nightsky-release-assembly`](../nightsky-release-assembly) resolves this
artifact, alongside `nightsky-sync-client`'s, into one assembled release.

## Streaming updates with Flux

[`LiveSkyFeed`](src/main/java/dev/glisseo/izar/examples/nightsky/LiveSkyFeed.java)
executes the subscription as one reactive chain:

```java
operations.executeSubscription(subscription)
        .map(result -> result.assertNoErrors().visibleSky())
        .doOnNext(this::printTick)
        .take(WATCH_DURATION)
        .blockLast();
```

`executeSubscription` returns a `Flux` of decoded server events. The client
stops after 72 seconds, then `blockLast()` lets this `CommandLineRunner` wait
for the bounded demo before the process exits.

## Application-configured client

[`ReactiveGraphQlClientConfiguration`](src/main/java/dev/glisseo/izar/examples/nightsky/ReactiveGraphQlClientConfiguration.java)
hand-wires a reactive Spring `GraphQlClient` (`HttpGraphQlClient` over
`WebClient`) and wraps it in Izar's `ReactiveGraphQlOperations`, mirroring
[`nightsky-sync-client`](../nightsky-sync-client)'s synchronous configuration.
The client uses full-document execution and a `WebClient` filter that logs each
request method, URL, and body, plus each response status and body chunk. Request
body logging is capped at 64 KiB. Response chunks are logged as they arrive, so
the subscription remains streamed. Without `reactor-netty`, `WebClient` falls
back to `spring-web`'s `JdkClientHttpConnector`.
