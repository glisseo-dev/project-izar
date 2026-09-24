package dev.glisseo.izar.compiler.internal.selection;

import java.util.List;

/**
 * A GraphQL enum type used in an output position, generated as a sealed interface with a nested
 * {@code Known} enum (constant names matching the schema's values) and an {@code Unrecognized}
 * record that preserves a value the schema at generation time did not know about. Mirrors {@code
 * dev.glisseo.izar.compiler.internal.variable.InputEnumType} for the input direction, where
 * evolving-server compatibility is not a concern.
 *
 * @param javaTypeName the generated sealed interface's simple name, unique within its enclosing
 *     operation
 * @param graphqlName the schema enum's name, used in decode failure messages
 * @param values the enum's value names, in schema-declared order
 */
public record OutputEnumType(String javaTypeName, String graphqlName, List<String> values) {}
