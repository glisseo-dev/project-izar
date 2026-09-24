package dev.glisseo.izar.compiler;

/**
 * An application- or library-supplied mapping for one custom GraphQL scalar, configured explicitly
 * for a generation run. The same mapping serves both generated inputs and generated responses.
 *
 * @param graphqlScalarName the scalar's name in the schema, e.g. {@code "DateTime"}
 * @param javaTypeName the fully qualified Java type generated code represents the scalar as, e.g.
 *     {@code "java.time.Instant"}; rendered as-is, so it must not rely on an import generated code
 *     does not add
 * @param codecClassName the fully qualified name of a public class with a public no-argument
 *     constructor implementing {@code dev.glisseo.izar.operation.ScalarCodec<T>} for {@code
 *     javaTypeName}
 */
public record ScalarMapping(String graphqlScalarName, String javaTypeName, String codecClassName) {

    public ScalarMapping {
        requireNonBlank(graphqlScalarName, "graphqlScalarName");
        requireNonBlank(javaTypeName, "javaTypeName");
        requireNonBlank(codecClassName, "codecClassName");
    }

    private static void requireNonBlank(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("A scalar mapping's " + fieldName + " must not be blank.");
        }
    }
}
