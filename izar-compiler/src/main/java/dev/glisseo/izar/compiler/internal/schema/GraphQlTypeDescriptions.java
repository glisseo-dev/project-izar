package dev.glisseo.izar.compiler.internal.schema;

import graphql.schema.GraphQLNamedType;
import graphql.schema.GraphQLType;

/** Formats a schema type for a diagnostic message. */
public final class GraphQlTypeDescriptions {

    private GraphQlTypeDescriptions() {}

    public static String describe(GraphQLType type) {
        return type instanceof GraphQLNamedType named ? named.getName() : type.toString();
    }
}
