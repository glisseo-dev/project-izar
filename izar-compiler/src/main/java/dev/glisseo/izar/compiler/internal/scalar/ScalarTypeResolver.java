package dev.glisseo.izar.compiler.internal.scalar;

import dev.glisseo.izar.compiler.ScalarMapping;
import graphql.schema.GraphQLScalarType;

/**
 * Resolves a schema scalar to the {@link ScalarType} generated code renders: a {@link
 * BuiltinScalar} for one of the five built-ins, or a {@link CustomScalarType} built from a
 * configured {@link ScalarMapping}. Shared by {@code
 * dev.glisseo.izar.compiler.internal.selection.SelectionAnalyzer} (output fields) and {@code
 * dev.glisseo.izar.compiler.internal.variable.VariableAnalyzer} (variables and input object fields), since
 * the same mapping serves both.
 */
public final class ScalarTypeResolver {

    private ScalarTypeResolver() {}

    /** @throws UnmappedScalarException if {@code scalarType} is custom and unconfigured */
    public static ScalarType resolve(GraphQLScalarType scalarType, ScalarMappingRegistry scalarMappings) {
        BuiltinScalar builtin = BuiltinScalar.forScalarType(scalarType);
        if (builtin != null) {
            return builtin;
        }
        ScalarMapping mapping = scalarMappings.forName(scalarType.getName());
        if (mapping == null) {
            throw new UnmappedScalarException(scalarType.getName());
        }
        return new CustomScalarType(mapping.graphqlScalarName(), mapping.javaTypeName(), mapping.codecClassName());
    }
}
