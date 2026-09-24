package dev.glisseo.izar.compiler.internal.scalar;

import dev.glisseo.izar.compiler.internal.naming.JavaIdentifiers;

/**
 * A custom GraphQL scalar resolved against an application-configured {@code
 * dev.glisseo.izar.compiler.ScalarMapping}:
 * {@code javaTypeName} is rendered directly as the generated field or record component type, and
 * {@code codecClassName} names the {@code ScalarCodec} implementation generated code instantiates
 * once per operation to decode and encode it. Both are used as-is (never imported), so an
 * application should supply fully qualified names to avoid relying on any import generated code
 * does not add.
 */
public record CustomScalarType(String graphqlName, String javaTypeName, String codecClassName) implements ScalarType {

    /** The generated static field holding this scalar's codec instance. */
    public String codecFieldName() {
        return "CODEC_" + JavaIdentifiers.constantName(graphqlName);
    }
}
