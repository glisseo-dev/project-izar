package dev.glisseo.izar.compiler.internal.selection;

import dev.glisseo.izar.compiler.internal.scalar.ScalarType;

/**
 * What a selected field decodes to: a built-in scalar, a nested object selection, or a list of
 * either (nested to any depth). Mirrors {@code dev.glisseo.izar.compiler.internal.variable.InputShape} for
 * the output direction.
 *
 * <p>{@code nullable} reflects both the field's schema type and whether every selection path
 * contributing to it is conditional ({@code @include}/{@code @skip}): a schema-non-null field can
 * still be absent from a response at runtime if every occurrence that selects it is conditional,
 * so its generated shape is nullable regardless of the schema. List container and element
 * nullability, in contrast, come from the schema alone.
 */
public sealed interface FieldShape {

    boolean nullable();

    record ScalarField(ScalarType scalar, boolean nullable) implements FieldShape {}

    record EnumField(OutputEnumType enumType, boolean nullable) implements FieldShape {}

    record ObjectField(ObjectSelection selection, boolean nullable) implements FieldShape {}

    record PolymorphicField(PolymorphicSelection selection, boolean nullable) implements FieldShape {}

    record ListField(FieldShape elementShape, boolean nullable) implements FieldShape {}
}
