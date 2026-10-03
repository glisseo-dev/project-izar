# Izar compiler

`izar-compiler` reads a GraphQL schema and a set of authored `.graphql` operation files, and
generates a Java class per operation: a record model for the response shape, a builder for
variables, and the exact executable document to send. It has no dependency on Maven, Gradle, or
any other build tool. [`izar-maven-plugin`](../izar-maven-plugin/) binds this module's
`OperationCompiler` to Maven's `generate-sources` phase and its parameter conventions; it adds no
generation logic of its own. Most applications should depend on `izar-maven-plugin` and never call
this module directly. Depend on `izar-compiler` itself only when writing a different build-tool
adapter, or when invoking or testing compilation programmatically.

## Add the dependency

```xml
<dependency>
    <groupId>dev.glisseo.izar</groupId>
    <artifactId>izar-compiler</artifactId>
    <version>0.1.0-SNAPSHOT</version>
</dependency>
```

`0.1.0-SNAPSHOT` is a snapshot version; expect the API to move.

## Using it directly

`OperationCompiler.generate` takes the schema files, the operation files, an output directory,
and a base package, and returns an `OperationManifest`:

```java
OperationManifest manifest = new OperationCompiler()
        .generate(schemaFiles, operationFiles, outputDirectory, "com.example.graphql");
```

An overload takes a `List<ScalarMapping>` for schemas with custom scalars (see below). Every
operation file is attempted even after one fails, so a single call reports every problem it finds
rather than stopping at the first.

The overload that also takes a `GenerationMode` selects the generated response model. `IZAR` is
the default. `JACKSON` generates response records for Spring GraphQL's `toEntity` mapping, uses
`String` for output enums, and annotates interface and union types for Jackson subtype mapping.
Generated `JACKSON` operations implement `GraphQlRequest` and omit Izar's response decoder.
Generated `IZAR` operations implement `GraphQlOperation` and include that decoder. Both modes
generate input-variable builders. When generated `JACKSON` models use response keys that need
Java identifier sanitization or polymorphic response types, their source requires
`jackson-annotations` on the consumer's compile classpath. The consumer's Jackson configuration
must support any configured custom scalar Java response types.

In `JACKSON` mode, the operation class exposes a path constant for each root response key:

```java
var operation = new GetBookScalarsQuery();
var book = graphQlClient.document(operation.document())
        .variables(operation.variables())
        .retrieve(GetBookScalarsQuery.BOOK)
        .toEntity(GetBookScalarsQuery.Book.class);
```

For polymorphic response fields, the compiler adds a shared discriminator selection to operation
documents when one is not already present. Manifest input cannot change its document, so it needs
an unconditional, unaliased `__typename` in the shared selection.

## What it supports

Verified against the analyzers (`SelectionAnalyzer`, `VariableAnalyzer`, `InputSourceGenerator`)
and their tests:

- Queries, mutations, and subscriptions, with one named operation per `.graphql` file. A
  subscription generates a `Subscription` class and a manifest entry with type `"subscription"`.
- Variables of scalar, enum, input-object, and list type, including nested lists, rendered as a
  generated builder per input-object type and one for the operation's own variables. An
  input-object field left unset by the builder is omitted from the encoded variables so the server
  applies its own default; a required field left unset fails at `build()`. An input-object type
  that references itself, directly or through a mutual cycle with another input-object type,
  generates the same way.
- Aliases, named fragments, and inline fragments, merged into one field per distinct response key.
- Conditional selections behind `@include`/`@skip`: a response component reachable only through a
  conditional path is nullable even where the schema itself is non-null, since the server may omit
  it at runtime.
- Interfaces and unions. A field with no type-conditioned fragment maps to one shared object type.
  A field with at least one type-conditioned fragment maps to a sealed interface with one record
  per named concrete type. `IZAR` adds an `Unrecognized` record for any concrete type the fragment
  did not name. `JACKSON` maps an unfamiliar typename to its Jackson-annotated `Unrecognized`
  record. The type condition on a fragment must be the field's own interface or union type, or one
  of its concrete member types; a narrower interface condition is rejected.
- Enums, on both the output and input side. `IZAR` generates output enums as sealed types with an
  `Unrecognized` fallback controlled by `DecodingPolicy`. `JACKSON` uses `String` for output enum
  values. Both modes generate input enums as Java enums.
- Custom scalars, through an explicit `ScalarMapping` (see below). GraphQL's five built-in scalars
  need no mapping.

## Custom scalars

A `ScalarMapping` pairs a GraphQL scalar name with the Java type generated code represents it as,
and the fully qualified name of a class implementing `ScalarCodec<T>` from
[`izar-operation`](../izar-operation/) for that type. Supply one `ScalarMapping` per custom scalar
the operation set uses; a scalar with no matching mapping fails generation, naming the scalar and
what to configure. `IZAR` uses the codec to decode response values and encode variables. `JACKSON`
uses the codec to encode variables; the application's Jackson configuration maps response values.
[`izar-maven-plugin`](../izar-maven-plugin/) exposes this as `<scalarMappings>`
configuration; see that module's README for the XML shape.
[`izar-scalars`](../izar-scalars/) is an optional library of ready-made mappings for common
scalars, and is a test-only dependency of this module (it isn't required at runtime).

## Determinism and failure behavior

The compiler reads a checked-in schema, or a pinned schema artifact, from local files. It never
performs live introspection. Identical schema files, operation files, and scalar mappings always
produce byte-identical generated sources, executable documents, operation IDs, and manifests.

An authored `.graphql` operation file is never rewritten. The compiler validates it against the
schema with `graphql-java`'s own validator, then builds a separate final executable document: it
inlines every fragment the operation transitively references and adds `__typename` discriminator
fields when the generation mode needs one the operation did not already select. Operation IDs and
[`izar-manifest`](../izar-manifest/) manifest bodies are derived from that final document, not
from the operation file's own text, so two operations that reference the same fragments
differently but resolve to the same wire document also resolve to the same ID.

`OperationCompiler.generateFromManifest` accepts a version 1 Apollo-compatible
manifest as an alternative input. Each entry must contain exactly one named
operation, and its `name` and `type` must match the operation in `body`. The
compiler validates each operation against the supplied schema, keeps the
operation ID and body exactly as provided, and generates the same Java models.
Imported IDs may use any nonblank format. If the manifest body does not select
the `__typename` discriminator required by its generation mode, generation
fails with a diagnostic instead of injecting one. JACKSON requires an
unaliased, unconditional `__typename` in the shared selection. IZAR accepts it
there or on every concrete branch. `validateManifest` runs the same checks
without writing Java files.

An unsupported operation feature or an unmapped custom scalar fails generation with an
`OperationGenerationException` naming the file and the problem, rather than emitting an incomplete
or best-effort model. The exception carries every diagnostic found across every operation file in
the run, not just the first.

## Boundaries

- No dependency on Maven, Gradle, or any other build tool: [`izar-maven-plugin`](../izar-maven-plugin/)
  is the only place build-lifecycle binding lives.
- No GraphQL parser of its own. Parsing, schema construction, and operation validation all go
  through `graphql-java`.
- No live schema introspection. The schema must already exist as a file on disk.

## Related modules

- [Root README](../README.md)
- [`izar-maven-plugin`](../izar-maven-plugin/): the Maven adapter most users should depend on
  instead of this module.
- [`izar-operation`](../izar-operation/): the runtime contract (`GraphQlOperation`, `ScalarCodec`,
  `DecodingPolicy`) generated code implements against.
- [`izar-manifest`](../izar-manifest/): the manifest format this module's output feeds.
- [`izar-scalars`](../izar-scalars/): optional ready-made scalar mappings.
