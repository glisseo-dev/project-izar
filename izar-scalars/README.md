# Izar scalars

Izar scalars provides ready-made `ScalarCodec` implementations for three custom scalars that GraphQL Java Extended Scalars commonly supplies on the server: `DateTime`, `Date`, and `BigDecimal`. Add it when your schema uses one of them, so you don't write the codec yourself.

## Add the dependency

```xml
<dependency>
    <groupId>dev.glisseo.izar</groupId>
    <artifactId>izar-scalars</artifactId>
    <version>0.1.0-SNAPSHOT</version>
</dependency>
```

Its only dependency is [izar-operation](../izar-operation/).

## Codecs

| GraphQL scalar | Java type | Codec class |
| --- | --- | --- |
| `DateTime` | `java.time.Instant` | `InstantScalarCodec` |
| `Date` | `java.time.LocalDate` | `LocalDateScalarCodec` |
| `BigDecimal` | `java.math.BigDecimal` | `BigDecimalScalarCodec` |

`InstantScalarCodec` and `LocalDateScalarCodec` convert through the ISO-8601 string that each type's `parse` and `toString` produce. They throw `IllegalArgumentException` with the offending value when the wire value isn't a string or doesn't parse.

`BigDecimalScalarCodec` decodes a `BigDecimal`, `String`, or `Number` into a `BigDecimal`. It reads a `Number` through `toString()` instead of `doubleValue()`, so a `Double` or `Long` that the JSON parser already produced keeps its precision. It encodes with `toPlainString()`, which never uses scientific notation.

## Map a scalar to a codec

A codec takes effect once you name it in the plugin's scalar mappings. Each mapping pairs the GraphQL scalar name, the fully qualified Java type, and the codec class. This example uses the Maven plugin's `<scalarMappings>`:

```xml
<configuration>
  <basePackage>com.example.graphql</basePackage>
  <scalarMappings>
    <scalarMapping>
      <graphqlScalarName>BigDecimal</graphqlScalarName>
      <javaTypeName>java.math.BigDecimal</javaTypeName>
      <codecClassName>dev.glisseo.izar.scalars.BigDecimalScalarCodec</codecClassName>
    </scalarMapping>
  </scalarMappings>
</configuration>
```

Add one `<scalarMapping>` for each custom scalar that an operation's selections or variables reach. `javaTypeName` must be fully qualified, because generated code adds no import for it. For the Gradle syntax, see [izar-gradle-plugin](../izar-gradle-plugin/#map-custom-scalars). An operation that reaches a custom scalar with no mapping fails generation and names the scalar.

## Write your own codec

For any other scalar, implement `dev.glisseo.izar.operation.ScalarCodec<T>` in a class with a public no-argument constructor. [izar-operation](../izar-operation/) documents the interface with an example. Your codec doesn't need izar-scalars. GraphQL Java Extended Scalars defines more scalars, such as `Time`, `UUID`, and `Long`, and each of them needs a codec you supply.

See the [root README](../README.md) for the other Izar modules.
