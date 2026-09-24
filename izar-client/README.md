# Izar client

Izar client executes generated GraphQL operations against a Spring `GraphQlClient` an application already configured. It is the module most adopters add directly, because it is the one that turns a generated operation class into an actual network call: everything upstream (code generation, manifests) produces artifacts this module consumes, and nothing downstream is required for a working client.

`SynchronousGraphQlOperations` and `ReactiveGraphQlOperations` share the same request encoding and response mapping, so a synchronous Spring service and a reactive one decode identical operations the same way. Izar builds neither client itself: an application hands it a `GraphQlClient`, and Spring's own HTTP stack, authentication, and instrumentation carry every request.

## Add the dependency

```xml
<dependency>
    <groupId>dev.glisseo.izar</groupId>
    <artifactId>izar-client</artifactId>
    <version>0.1.0-SNAPSHOT</version>
</dependency>
```

This pulls in [izar-operation](../izar-operation/) and [izar-manifest](../izar-manifest/) transitively, plus `spring-graphql` and `reactor-core`. `spring-web` is required and `spring-webflux` is optional, so a synchronous-only consumer is not forced onto the reactive stack.

Transport support remains application-owned. A WebSocket consumer must provide
Spring's WebSocket support and a `WebSocketClient` implementation, while an
RSocket consumer must provide Spring RSocket support. For example, the
integration consumer adds `spring-boot-starter-websocket`,
`spring-boot-starter-rsocket`, and `reactor-netty-http`; an application should
choose versions and implementations that match its Spring Boot setup.

## Wiring a client by hand

An application configures an ordinary Spring `GraphQlClient` bean, with whatever URL, authentication, timeouts, or interceptors it needs, and wraps it in `SynchronousGraphQlOperations.fullDocument`:

```java
@Configuration
class GraphQlClientConfiguration {

    @Bean
    GraphQlClient bookServiceGraphQlClient() {
        return HttpSyncGraphQlClient.builder()
                .url("https://books.example.com/graphql")
                .build();
    }

    @Bean
    SynchronousGraphQlOperations bookServiceOperations(GraphQlClient bookServiceGraphQlClient) {
        return SynchronousGraphQlOperations.fullDocument(bookServiceGraphQlClient);
    }
}
```

`ReactiveGraphQlOperations` follows the same shape over an `HttpGraphQlClient` built from a `WebClient`, composing with `Mono` rather than blocking. Its `execute` returns a `Mono` that does not run the operation until subscribed, matching `GraphQlClient.RequestSpec.execute()`'s own deferred semantics; cancelling that `Mono`, or composing it with `Mono#timeout`, propagates to the underlying transport exactly as it would for any other reactive Spring GraphQL call.

`fullDocument` wraps any `GraphQlClient` the application already built; the [persisted-ID factory below](#persisted-id-execution) builds the client itself instead, so the two named factories cover the two execution modes without a caller ever having to pick a constructor and a mode separately. Both accept an optional `DecodingPolicy` (default `LENIENT`): `STRICT` fails decoding on an enum value or polymorphic type the schema did not know about at generation time, instead of falling back to the generated `Unrecognized` representation. The policy is fixed once per `SynchronousGraphQlOperations`/`ReactiveGraphQlOperations` instance, so two clients executing the same generated operation can each choose differently.

## Executing a subscription over application-configured transports

Build the transport-specific `GraphQlClient` in the consuming application, then call the same `executeSubscription` method with a generated subscription. The public Izar API does not change with the transport:

```java
Flux<GraphQlResult<WatchBookSubscription.Data>> events =
        reactiveOperations.executeSubscription(new WatchBookSubscription());
```

For SSE, the application can build an `HttpGraphQlClient` over its `WebClient`. For WebSocket and RSocket, it can build `WebSocketGraphQlClient` or `RSocketGraphQlClient` instead and pass either one to `ReactiveGraphQlOperations.fullDocument`:

```java
import dev.glisseo.izar.client.ReactiveGraphQlOperations;
import dev.glisseo.izar.client.GraphQlResult;
import com.example.generated.WatchBookSubscription;
import org.springframework.graphql.client.WebSocketGraphQlClient;
import org.springframework.web.reactive.socket.client.ReactorNettyWebSocketClient;
import reactor.core.publisher.Flux;

WebSocketGraphQlClient webSocketClient = WebSocketGraphQlClient.builder(
        "wss://books.example.com/graphql", new ReactorNettyWebSocketClient()).build();
ReactiveGraphQlOperations reactiveOperations =
        ReactiveGraphQlOperations.fullDocument(webSocketClient);
Flux<GraphQlResult<WatchBookSubscription.Data>> webSocketEvents =
        reactiveOperations.executeSubscription(new WatchBookSubscription());

import org.springframework.graphql.client.RSocketGraphQlClient;

RSocketGraphQlClient rsocketClient = RSocketGraphQlClient.builder()
        .tcp("books.example.com", 7000)
        .route("graphql")
        .build();
ReactiveGraphQlOperations rsocketOperations =
        ReactiveGraphQlOperations.fullDocument(rsocketClient);
Flux<GraphQlResult<WatchBookSubscription.Data>> rsocketEvents =
        rsocketOperations.executeSubscription(new WatchBookSubscription());
```

Spring retains ownership of authentication, headers, connection lifecycle, timeouts, and interceptors on each application-configured client. Each pushed response becomes one `GraphQlResult`, regardless of transport. Inspect `errors()` and `extensions()` on each event before calling `assertNoErrors()` when the application wants fail-fast handling. Connection or transport failures terminate the `Flux` as an error. Normal completion, cancellation, and downstream demand follow Spring GraphQL and Reactor. The adapter does not buffer, retry, or turn transport failures into GraphQL results.

`executeSubscription` rejects query and mutation operations. `SynchronousGraphQlOperations.execute` rejects subscriptions. Persisted-ID subscriptions use the reactive `WebClient` factory and HTTP SSE. The request still omits the operation document, and the server resolves it from the persisted ID before it starts the stream.

## Executing an operation

`execute` takes a generated `GraphQlOperation<TResponse>` and returns a `GraphQlResult<TResponse>` (synchronously) or `Mono<GraphQlResult<TResponse>>` (reactively):

```java
GraphQlResult<GetBookResult> result = bookServiceOperations.execute(new GetBook(isbn));
GetBookResult book = result.assertNoErrors();
```

`GraphQlResult` keeps `data`, the response's GraphQL `errors`, and its `extensions` together rather than collapsing them into a single success-or-failure outcome: a response with partial data and errors is still returned as one result, not thrown. `assertNoErrors()` is the fail-fast convenience, throwing `GraphQlOperationException` if the response carried any GraphQL error or no data at all; a caller that wants to inspect errors and partial data together uses `hasErrors()`, `errors()`, and `data()` directly instead.

A transport failure (for example, a refused connection, an unhandled non-2xx status, or a timeout) is not represented as a `GraphQlResult`. Spring's client exception path keeps it distinct from a GraphQL execution error. Persisted-ID HTTP transports also parse 4xx responses with the `application/graphql-response+json` content type as GraphQL responses, so their errors appear in `GraphQlResult`. A response with usable data that the generated operation cannot decode raises `GraphQlDecodingException`.

## Persisted-ID execution

`GraphQlExecutionMode.PERSISTED_ID` sends only the Apollo-compatible `persistedQuery` extension (`version` and `sha256Hash`, the same hash [izar-manifest](../izar-manifest/) derives for a manifest entry's ID) plus variables and operation name. The operation's document text never reaches the wire. Build the operations adapter from an application-configured `RestClient` or `WebClient` with the matching `persistedQuery` factory:

```java
RestClient restClient = RestClient.builder()
        .baseUrl("https://books.example.com/graphql")
        .build();
SynchronousGraphQlOperations operations =
        SynchronousGraphQlOperations.persistedQuery(restClient);
```

For a subscription over HTTP SSE, use the reactive factory with a `WebClient`:

```java
WebClient webClient = WebClient.builder()
        .baseUrl("https://books.example.com/graphql")
        .build();
ReactiveGraphQlOperations operations = ReactiveGraphQlOperations.persistedQuery(webClient);
Flux<GraphQlResult<WatchBookSubscription.Data>> events =
        operations.executeSubscription(new WatchBookSubscription());
```

An overload accepts an explicit `DecodingPolicy`. The paired factory prevents the persisted-ID transport and execution mode from being configured separately. A third overload accepts a `Consumer<GraphQlClient.Builder<?>>` for a caller that needs a `GraphQlClientInterceptor`, a `documentSource`, or a `blockingTimeout` on persisted-ID execution:

```java
SynchronousGraphQlOperations operations = SynchronousGraphQlOperations.persistedQuery(
        restClient, DecodingPolicy.LENIENT, builder -> builder.interceptor(myInterceptor));
```

The customizer can configure the builder but not replace the transport, so this still can't drift out of sync with `PERSISTED_ID` the way calling `PersistedIdGraphQlClient.syncBuilder`/`.reactiveBuilder` and the general constructor separately could. `PersistedIdGraphQlClient` remains available directly only for a caller that wants the raw `GraphQlClient`, decoupled from `SynchronousGraphQlOperations`/`ReactiveGraphQlOperations` entirely.

This mode only makes sense with a server that resolves operations by ID from its own registry, such as [izar-server](../izar-server/)'s allowlisting. Both execution modes target the same URL. A compatible server accepts full-document and hash-only requests on its ordinary GraphQL endpoint.

This client module places no requirement on the server beyond that: `FULL_DOCUMENT` mode works against any Spring GraphQL endpoint, whether or not it enforces an allowlist.

## What this module does not do

Izar constructs no HTTP client, connection pool, TLS configuration, or proxy setup of its own; every `GraphQlClient` it executes against comes from the application, built with Spring's own `RestClient`/`WebClient` builders. It adds no retry policy: `execute` makes exactly one attempt per call, and a caller that wants retries composes them itself, with Reactor operators on the reactive adapter or an ordinary loop around the synchronous one. It does not manage authentication, header injection, or observability; those stay on whichever builder configured the underlying transport.

## Related modules

- [Root README](../README.md) for the full module layout.
- [izar-operation](../izar-operation/) defines the `GraphQlOperation` contract this module executes.
- [izar-manifest](../izar-manifest/) derives the operation IDs persisted-ID execution sends.
- [izar-server](../izar-server/) enforces an allowlist on the receiving end, including hash-only persisted-ID requests.
- [izar-scalars](../izar-scalars/) supplies custom-scalar codecs a generated operation's `decode` can use.
