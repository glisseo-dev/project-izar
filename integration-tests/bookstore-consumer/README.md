# Bookstore consumer

A Maven consumer that generates immutable Java responses for
`GetBook` from [`src/main/graphql/schema.graphqls`](src/main/graphql/schema.graphqls)
and [`src/main/graphql/GetBook.graphql`](src/main/graphql/GetBook.graphql), plus
a generated `WatchBookSubscription` from
[`src/main/graphql/WatchBook.graphql`](src/main/graphql/WatchBook.graphql).
It also generates `GetServiceStatus` from a separate schema and operation
directory, and generates `GetBookGenreAndSearchResults` in Jackson mode for
Spring GraphQL's `toEntity` mapping.
It executes the query through a synchronous Spring GraphQL client, maps the
result into [`BookSummary`](src/main/java/dev/glisseo/izar/integrationtests/bookstoreconsumer/BookSummary.java)
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
./mvnw -f integration-tests/bookstore-consumer/pom.xml test
```

```powershell
.\mvnw.cmd -f integration-tests\bookstore-consumer\pom.xml test
```

Use `verify` to also run the configured `package` phase. It attaches the status
server's manifest with classifier `izar-status-server-manifest`; the books
server's manifest remains unattached.

That single command:

1. Runs `izar-maven-plugin`'s `generate` goal three times at `generate-sources`, once
   for the books server and once for the status server. Each execution uses its
   own schema, operation directory, package, source directory, and manifest.
   The third execution generates the books operations in Jackson mode.
   `MultiServerGenerationIntegrationTest` checks that the source trees and
   manifests stay separate.
2. Compiles the generated source alongside this project's own code, including
   MapStruct's annotation processor generating `BookMapperImpl`.
3. Runs `FetchBookIntegrationTest`, which starts a real embedded Spring
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
8. Runs `ExecutePersistedOperationsIntegrationTest` and
   `ExecutePersistedSubscriptionIntegrationTest`, which send generated queries, a
   mutation, and a subscription by persisted ID only, over `RestClient`, `WebClient`,
   and HTTP SSE. A plain Spring GraphQL server resolves each ID through GraphQL Java's
   `PreparsedDocumentProvider` and records the query text it receives, so the tests
   prove that the client sends the ID and never the document.
9. Runs `JacksonGenerationIntegrationTest`, which maps an enum and both concrete
   branches of a GraphQL union from a real HTTP response with Spring GraphQL's
   `toEntity` methods.

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
