/**
 * Resolves GraphQL scalars to the Java type and codec generated code uses.
 *
 * <p>Covers the five GraphQL built-ins and application-configured custom scalar mappings alike.
 * Consulted by both the selection and variable analysis stages, since one mapping serves output
 * decoding and input encoding identically.
 */
@NullMarked
package dev.glisseo.izar.compiler.internal.scalar;

import org.jspecify.annotations.NullMarked;
