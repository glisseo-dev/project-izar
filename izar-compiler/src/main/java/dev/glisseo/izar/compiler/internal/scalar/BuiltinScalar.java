package dev.glisseo.izar.compiler.internal.scalar;

import graphql.schema.GraphQLScalarType;
import org.jspecify.annotations.Nullable;

/**
 * The five GraphQL built-in scalars. A custom scalar is resolved through {@code
 * dev.glisseo.izar.compiler.ScalarMapping} instead.
 */
public enum BuiltinScalar implements ScalarType {
    STRING,
    INT,
    FLOAT,
    BOOLEAN,
    ID;

    /** The Java type a decoded value of this scalar has. */
    @Override
    public String javaTypeName() {
        return switch (this) {
            case STRING, ID -> "String";
            case INT -> "Integer";
            case FLOAT -> "Double";
            case BOOLEAN -> "Boolean";
        };
    }

    /** The {@code GraphQlDecoding} method that decodes a value of this scalar. */
    public String decodeMethodName() {
        return switch (this) {
            case STRING -> "asString";
            case INT -> "asInt";
            case FLOAT -> "asFloat";
            case BOOLEAN -> "asBoolean";
            case ID -> "asID";
        };
    }

    /** The built-in scalar matching {@code scalarType}'s name, or {@code null} if it's custom. */
    static @Nullable BuiltinScalar forScalarType(GraphQLScalarType scalarType) {
        return switch (scalarType.getName()) {
            case "String" -> STRING;
            case "Int" -> INT;
            case "Float" -> FLOAT;
            case "Boolean" -> BOOLEAN;
            case "ID" -> ID;
            default -> null;
        };
    }
}
