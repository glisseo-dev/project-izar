package dev.glisseo.izar.compiler.internal.variable;

/**
 * One named input value: an operation variable, or a field of a generated input-object type.
 *
 * @param graphqlName the variable or input field's declared name
 * @param shape what the value is shaped like
 * @param hasDefault whether the operation (for a variable) or the schema (for an input field)
 *     already supplies a usable default, so an untouched builder property is a valid omission
 *     rather than a required-input failure
 */
public record InputFieldDefinition(String graphqlName, InputShape shape, boolean hasDefault) {}
