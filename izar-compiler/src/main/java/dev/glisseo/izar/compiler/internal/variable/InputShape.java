package dev.glisseo.izar.compiler.internal.variable;

import dev.glisseo.izar.compiler.internal.scalar.ScalarType;

/**
 * What a variable or input-object field's value is shaped like: a built-in scalar, an enum, a
 * nested input object, or a list of any of those. Mirrors {@code
 * dev.glisseo.izar.compiler.internal.selection.FieldShape} for the input direction.
 */
public sealed interface InputShape {

    boolean nullable();

    record ScalarShape(ScalarType scalar, boolean nullable) implements InputShape {}

    record EnumShape(InputEnumType enumType, boolean nullable) implements InputShape {}

    record InputObjectShape(InputObjectType objectType, boolean nullable) implements InputShape {}

    record ListShape(InputShape elementShape, boolean nullable) implements InputShape {}
}
