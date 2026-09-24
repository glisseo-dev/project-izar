package dev.glisseo.izar.compiler.internal.variable;

import java.util.List;

/**
 * A GraphQL enum type used in an input position, generated as a plain Java enum whose constant
 * names match the schema's enum values exactly, since that identity is what lets a generated
 * value's {@code name()} serve directly as its GraphQL wire representation.
 *
 * @param javaTypeName the generated enum's simple name, unique within its enclosing operation
 * @param values the enum's value names, in schema-declared order
 */
public record InputEnumType(String javaTypeName, List<String> values) {}
