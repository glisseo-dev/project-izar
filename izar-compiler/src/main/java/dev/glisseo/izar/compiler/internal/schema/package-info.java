/**
 * Loads a checked-in schema and formats its types for diagnostics.
 *
 * <p>Builds the un-executable {@code GraphQLSchema} every later stage validates and resolves
 * field types against; never performs live introspection.
 */
@NullMarked
package dev.glisseo.izar.compiler.internal.schema;

import org.jspecify.annotations.NullMarked;
