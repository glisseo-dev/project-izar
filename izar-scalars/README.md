# Izar scalars

Izar scalars is an optional library of pre-built `ScalarCodec` implementations for the custom scalars GraphQL Java Extended Scalars typically supplies on the server: `DateTime`, `Date`, and `BigDecimal`. Reach for it when a schema uses one of these three scalars, instead of writing a codec by hand for a mapping this library already covers.

## Add the dependency

```xml
<dependency>
    <groupId>dev.glisseo.izar</groupId>
    <artifactId>izar-scalars</artifactId>
    <version>0.1.0-SNAPSHOT</version>
</dependency>
```

It brings in [izar-operation](../izar-operation/) transitively; nothing else.

## The three codecs

| GraphQL scalar | Java type | Codec class |
| --- | --- | --- |
| `DateTime` | `java.time.Instant` | `InstantScalarCodec` |
| `Date` | `java.time.LocalDate` | `LocalDateScalarCodec` |
| `BigDecimal` | `java.math.BigDecimal` | `BigDecimalScalarCodec` |

`InstantScalarCodec` and `LocalDateScalarCodec` both round-trip through the ISO-8601 string each type's own `parse`/`toString` already produces, and throw `IllegalArgumentException` naming the offending value when the wire value isn't a string or doesn't parse.

`BigDecimalScalarCodec` decodes a `BigDecimal`, `String`, or `Number` value into a `BigDecimal`. It reads a `Number` through `toString()` rather than `doubleValue()`, so a value the JSON parser already produced as a `Double` or `Long` doesn't lose precision before the codec sees it. It encodes with `toPlainString()`, never scientific notation.

All three Java types are immutable, so none of these codecs defensively copies on the way in or out.

## Configuring a mapping

Nothing here applies until an application names a mapping in [izar-maven-plugin](../izar-maven-plugin/)'s `<scalarMappings>`, pairing the GraphQL scalar name, the fully qualified Java type, and a codec class:

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

Add one `<scalarMapping>` per custom scalar an operation's selections or variables reach. `javaTypeName` must be fully qualified; generated code adds no import for it.

## Writing your own codec instead

A scalar with no mapping shipped here, a domain-specific one, needs its own class implementing `dev.glisseo.izar.operation.ScalarCodec<T>` with a public no-argument constructor. See [izar-operation](../izar-operation/) for the interface and a worked example. That codec carries no dependency on izar-scalars at all; the two libraries are unrelated implementations of the same interface.

## What this module does not do

- Nothing here is applied automatically. Depending on izar-scalars without naming a `<scalarMapping>` for a given scalar has no effect.
- It does not change generation behavior for scalars it doesn't cover: an unmapped custom scalar still fails generation, naming the scalar and what to configure.
- It ships no codec beyond the three above. GraphQL Java Extended Scalars defines more (`Time`, `UUID`, `Long`, and others); a schema using one of those still needs an application-supplied codec until a mapping for it is added here.

See the [root README](../README.md) for how this module fits into the rest of Izar.
