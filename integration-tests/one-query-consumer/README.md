# One query consumer

A Maven consumer that generates immutable Java responses for
`GetBook` from [`src/main/graphql/schema.graphqls`](src/main/graphql/schema.graphqls)
and [`src/main/graphql/GetBook.graphql`](src/main/graphql/GetBook.graphql), plus
a generated `WatchBookSubscription` from
[`src/main/graphql/WatchBook.graphql`](src/main/graphql/WatchBook.graphql).
It also generates `GetServiceStatus` from a separate schema and operation
directory to demonstrate independent Maven plugin executions.
It executes the query through a synchronous Spring GraphQL client, maps the
result into [`BookSummary`](src/main/java/dev/glisseo/izar/examples/onequeryconsumer/BookSummary.java)
with MapStruct, and decodes the subscription's SSE, WebSocket, and RSocket events through the
reactive client.

This project is deliberately outside the root Maven reactor (see
[`../README.md`](../README.md)): it depends on Izar the way a real consumer
would, as installed artifacts with its own ordinary `spring-boot-starter-parent`.

## Running it

From the repository root, install the Izar modules this project depends on:

```bash
./mvnw install
```

```powershell
.\mvnw.cmd install
```

Then build and test this project on its own:

```bash
./mvnw -f integration-tests/one-query-consumer/pom.xml test
```

```powershell
.\mvnw.cmd -f integration-tests\one-query-consumer\pom.xml test
```

Use `verify` to also run the configured `package` phase. It attaches the status
server's manifest with classifier `izar-status-server-manifest`; the books
server's manifest remains unattached.

That single command:

1. Runs `izar-maven-plugin`'s `generate` goal twice at `generate-sources`, once
   for the books server and once for the status server. Each execution uses its
   own schema, operation directory, package, source directory, and manifest.
   `MultiServerGenerationIntegrationTest` checks that the generated source trees
   and manifests stay separate.
2. Compiles the generated source alongside this project's own code, including
   MapStruct's annotation processor generating `BookMapperImpl`.
3. Runs `OneQueryConsumerIntegrationTest`, which starts a real embedded Spring
   GraphQL server (`BookGraphQlController`, a test-only fixture) and executes
   the generated query against it over real HTTP, asserting the MapStruct-mapped
   `BookSummary`.
4. Runs `CreateBookVariableEncodingIntegrationTest`, which does the
   same for the generated `CreateBookMutation`, built from
   [`src/main/graphql/CreateBook.graphql`](src/main/graphql/CreateBook.graphql)
   and the schema's `BookInput` type.
5. Runs `CustomScalarRoundTripIntegrationTest`, which decodes the
   schema's `DateTime`, `Date`, and `BigDecimal` custom scalars over a server
   that registers real GraphQL Java Extended Scalars types for each.
6. Runs `ExecuteSubscriptionIntegrationTest`, which sends the generated
   subscription through Spring's HTTP GraphQL client to an SSE fixture and
   decodes two pushed events before normal stream completion.
7. Runs `ExecuteSubscriptionTransportsIntegrationTest`, which sends the same
   generated subscription through application-configured Spring GraphQL WebSocket
   and RSocket clients and decodes two events from each transport.
8. Runs `ExecutePersistedSubscriptionIntegrationTest`, which sends the generated
   subscription without its document through Izar's persisted-ID HTTP SSE path and
   decodes two events from the allowlisted server.

The WebSocket and RSocket dependencies are intentionally declared by this
consumer application in `pom.xml`, just as they would be in an application
that chooses those transports.

## Custom scalars

`izar-maven-plugin`'s `<scalarMappings>` configures an explicit Java type and
codec for each custom scalar an operation's selections or variables reach:

```xml
<scalarMappings>
  <scalarMapping>
    <graphqlScalarName>DateTime</graphqlScalarName>
    <javaTypeName>java.time.Instant</javaTypeName>
    <codecClassName>dev.glisseo.izar.scalars.InstantScalarCodec</codecClassName>
  </scalarMapping>
  <scalarMapping>
    <graphqlScalarName>Date</graphqlScalarName>
    <javaTypeName>java.time.LocalDate</javaTypeName>
    <codecClassName>dev.glisseo.izar.scalars.LocalDateScalarCodec</codecClassName>
  </scalarMapping>
  <scalarMapping>
    <graphqlScalarName>BigDecimal</graphqlScalarName>
    <javaTypeName>java.math.BigDecimal</javaTypeName>
    <codecClassName>dev.glisseo.izar.scalars.BigDecimalScalarCodec</codecClassName>
  </scalarMapping>
</scalarMappings>
```

`InstantScalarCodec` comes from the optional `izar-scalars` library, which
also supplies `LocalDateScalarCodec` and `BigDecimalScalarCodec` for GraphQL
Java Extended Scalars' `Date` and `BigDecimal`. An application can just as
well supply its own class implementing `dev.glisseo.izar.operation.ScalarCodec<T>`
with a public no-argument constructor, naming it as `codecClassName` instead;
`izar-scalars` is never required. A custom scalar with no configured mapping
fails generation with the scalar's name and what to configure; `javaTypeName`
should be fully qualified, since generated code does not add an import for it.

## Variable builders

`izar-maven-plugin` also generates an immutable builder for every operation
variable and every GraphQL input object or enum type it reaches. A generated
input builder distinguishes three states for each property:

```java
import dev.glisseo.izar.generated.books.CreateBookMutation;
import dev.glisseo.izar.generated.books.CreateBookMutation.BookInput;
import dev.glisseo.izar.generated.books.CreateBookMutation.Genre;

// Untouched: 'pageCount' and 'tags' are left out of the request entirely, and
// 'genre' is left out too: the schema declares a default ('genre: Genre =
// FICTION'), so the server applies FICTION without the client sending it.
BookInput input = BookInput.builder()
        .title("Dune")
        .build();

// Explicit null: distinct from leaving 'pageCount' untouched above. The
// request carries "pageCount": null rather than omitting the key.
BookInput clearedPageCount = BookInput.builder()
        .title("Dune")
        .pageCount(null)
        .build();

// Explicit value: overrides the schema default instead of relying on it.
BookInput nonfiction = BookInput.builder()
        .title("Dune")
        .genre(Genre.NONFICTION)
        .build();

CreateBookMutation.builder().input(input).build();
```

`BookInput.title` is non-null with no default, so building without calling
`.title(...)` throws `IllegalStateException` before any request is sent,
the same check that applies to the top-level `$input` variable on
`CreateBookMutation` itself. A list property (`tags`) is defensively copied on
the way in: mutating the `List` you passed to `.tags(...)`, or the `List` a
built value later returns, has no effect on the built value.

`CreateBookVariableEncodingIntegrationTest` proves all of this against a real
server: it reads `BookGraphQlController.lastObservedInput`, the exact argument
map GraphQL Java's own input coercion handed the resolver, so the omission,
explicit-null, and default-application cases above are distinguished by what
the server actually received.

## Application-controlled resilience

Izar's adapters make exactly one attempt per `execute()` call; they add no
retry policy of their own. [`ResilientBookQueries`](src/main/java/dev/glisseo/izar/examples/onequeryconsumer/ResilientBookQueries.java)
shows what an application adds on top, using ordinary
[Spring Retry](https://github.com/spring-projects/spring-retry) rather than
an Izar-specific mechanism:

```java
private final RetryTemplate retryTemplate = RetryTemplate.builder()
        .maxAttempts(3)
        .fixedBackoff(Duration.ofMillis(10))
        .retryOn(GraphQlOperationException.class)
        .build();

<TResponse> TResponse executeQueryWithRetry(GraphQlOperation<TResponse> query) {
    return retryTemplate.execute(context -> operations.execute(query).assertNoErrors());
}
```

Only the query method is wrapped. `executeMutationWithoutRetry` calls
`operations.execute(mutation)` directly, with no `retryTemplate` involved at
all. A query has no side effect, so retrying it after a failed attempt is
safe: at worst, the server does the same read twice. A mutation is not safe
to retry by default: if the server applies it but the response is lost before
reaching the client, a blanket retry resends the same "create" and risks a
second book, not just a second request. Making that safe needs the mutation
to carry its own idempotency key, or the caller to check whether the first
attempt already succeeded, neither of which a generic retry policy can supply
on the operation's behalf, so the safe default, used here, is no retry.

`ApplicationControlledResilienceIntegrationTest` proves both halves against
the real fixture server: it retries `GetBrokenBookQuery` (which always fails)
and asserts `BookGraphQlController.brokenBookCallCount == 3`, the server-side
evidence that the *application's* retry policy, not Izar, drove three
attempts; and it executes `CreateBookMutation` once and confirms the server
saw exactly one request.
