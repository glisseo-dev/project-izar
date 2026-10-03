# Izar client

Izar client runs the operation classes that [`izar-maven-plugin`](../izar-maven-plugin/) or [`izar-gradle-plugin`](../izar-gradle-plugin/) generate from your `.graphql` files. It wraps the Spring for GraphQL `GraphQlClient` your application already configures, and offers a synchronous and a reactive API with the same request encoding and response mapping. Add this module to every project that calls a GraphQL endpoint with generated operations.

Spring owns the HTTP stack. You build the `GraphQlClient`, with its URL, authentication, timeouts, and interceptors, and hand it to Izar. Izar never creates a client of its own.

## Add the dependency

```xml
<dependency>
    <groupId>dev.glisseo.izar</groupId>
    <artifactId>izar-client</artifactId>
    <version>0.1.0-SNAPSHOT</version>
</dependency>
```

This brings in [izar-operation](../izar-operation/) and [izar-manifest](../izar-manifest/), plus `spring-graphql`, `reactor-core`, and `spring-web`. `spring-webflux` is optional, so a synchronous-only project doesn't pull in the reactive stack. Add it when you use `ReactiveGraphQlOperations` over HTTP.

Your application chooses the transport support for subscriptions. WebSocket needs Spring's WebSocket support and a `WebSocketClient` implementation. RSocket needs Spring RSocket support. The integration tests, for example, use `spring-boot-starter-websocket`, `spring-boot-starter-rsocket`, and `reactor-netty-http`. Pick versions that match your Spring Boot setup.

## Wire up a client

Define an ordinary `GraphQlClient` bean and wrap it with `SynchronousGraphQlOperations.fullDocument`:

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

`ReactiveGraphQlOperations.fullDocument` takes a `GraphQlClient` built the same way, for example an `HttpGraphQlClient` over a `WebClient`. Its `execute` returns a `Mono` that runs the operation on subscription, like `GraphQlClient.RequestSpec.execute()`. Cancelling the `Mono` or composing it with `Mono#timeout` propagates to the transport as it does for any reactive Spring GraphQL call.

Both factories accept a `DecodingPolicy`. The default, `LENIENT`, decodes an enum value or polymorphic type your schema didn't know at generation time into the generated `Unrecognized` representation. `STRICT` fails decoding instead. You set the policy per `SynchronousGraphQlOperations` or `ReactiveGraphQlOperations` instance, so two clients can run the same generated operation with different policies.

## Execute an operation

`execute` takes a generated operation and returns a `GraphQlResult<TResponse>`, or a `Mono<GraphQlResult<TResponse>>` on the reactive API. Build the operation with its generated builder:

```java
GetBookQuery query = GetBookQuery.builder().isbn(isbn).build();
GetBookQuery.Data book = bookServiceOperations.execute(query).assertNoErrors();
```

A `GraphQlResult` holds `data()`, the response's GraphQL `errors()`, and its `extensions()` together. A response with partial data and errors comes back as one result and is not thrown. `assertNoErrors()` returns the data, or throws `GraphQlOperationException` when the response has any GraphQL error or no data. To handle errors next to partial data, use `hasErrors()`, `errors()`, and `data()`.

Transport failures, such as a refused connection, an unhandled non-2xx status, or a timeout, are not `GraphQlResult` values. They surface through Spring's client exceptions. Persisted-ID HTTP transports are the exception for 4xx responses with the `application/graphql-response+json` content type. They parse these as GraphQL responses, so the errors appear in the `GraphQlResult`. A response with usable data that the generated operation can't decode raises `GraphQlDecodingException`.

`execute` makes one attempt per call and adds no retry policy. Compose retries with Reactor operators on the reactive API or with a loop around the synchronous one.

## Execute a subscription

Call `executeSubscription` on `ReactiveGraphQlOperations` with a generated subscription. The call is the same on every transport:

```java
Flux<GraphQlResult<WatchBookSubscription.Data>> events =
        reactiveOperations.executeSubscription(new WatchBookSubscription());
```

For SSE, build an `HttpGraphQlClient` over your `WebClient`. For WebSocket or RSocket, build a `WebSocketGraphQlClient` or `RSocketGraphQlClient` and pass it to `ReactiveGraphQlOperations.fullDocument`:

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

Each pushed response becomes one `GraphQlResult`, on any transport. Check `errors()` and `extensions()` on each event, or call `assertNoErrors()` to fail fast. A connection or transport failure ends the `Flux` with an error. Completion, cancellation, and demand follow Spring GraphQL and Reactor. Izar doesn't buffer events, retry, or convert transport failures into GraphQL results. Authentication, headers, connection lifecycle, timeouts, and interceptors stay on the client you configured.

`executeSubscription` accepts only subscriptions, and `SynchronousGraphQlOperations.execute` rejects them. A persisted-ID subscription uses the reactive `WebClient` factory over HTTP SSE.

## Run operations by persisted ID

`GraphQlExecutionMode.PERSISTED_ID` sends the Apollo-compatible `persistedQuery` extension (`version` and `sha256Hash`, the hash that [izar-manifest](../izar-manifest/) uses as a manifest entry's ID), plus the variables and operation name. The document text never goes over the wire. Build the adapter from an application-configured `RestClient` or `WebClient` with the matching `persistedQuery` factory:

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

Each factory has an overload that takes a `DecodingPolicy`. A third overload takes a `Consumer<GraphQlClient.Builder<?>>` for a `GraphQlClientInterceptor`, a `documentSource`, or a `blockingTimeout`:

```java
SynchronousGraphQlOperations operations = SynchronousGraphQlOperations.persistedQuery(
        restClient, DecodingPolicy.LENIENT, builder -> builder.interceptor(myInterceptor));
```

The customizer configures the builder but can't replace the transport, so the transport and `PERSISTED_ID` mode always stay paired. Use `PersistedIdGraphQlClient` directly only when you want the raw `GraphQlClient`, separate from the operations adapters.

The server must resolve operations by ID from its own registry, for example through GraphQL Java's `PreparsedDocumentProvider`, as [`nightsky-default-sb-server`](../examples/nightsky-default-sb-server/) does. A compatible server accepts full-document and hash-only requests on the same GraphQL endpoint URL. `FULL_DOCUMENT` mode works against any Spring GraphQL endpoint.

## Related modules

- [izar-operation](../izar-operation/) defines the `GraphQlOperation` contract this module executes.
- [izar-manifest](../izar-manifest/) derives the operation IDs that persisted-ID execution sends.
- [izar-scalars](../izar-scalars/) supplies custom-scalar codecs for generated operations.
- The [root README](../README.md) lists all modules.
