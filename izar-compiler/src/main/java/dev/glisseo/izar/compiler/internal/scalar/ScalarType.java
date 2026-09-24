package dev.glisseo.izar.compiler.internal.scalar;

/**
 * What a scalar-typed field or variable decodes to and encodes from: one of the five GraphQL
 * built-ins, or an application-configured {@link CustomScalarType}. Shared by {@code
 * dev.glisseo.izar.compiler.internal.selection.FieldShape} and {@code
 * dev.glisseo.izar.compiler.internal.variable.InputShape}, since a scalar mapping serves both directions
 * identically.
 */
public sealed interface ScalarType permits BuiltinScalar, CustomScalarType {

    /** The Java type a value of this scalar has, decoded or ready to encode. */
    String javaTypeName();
}
