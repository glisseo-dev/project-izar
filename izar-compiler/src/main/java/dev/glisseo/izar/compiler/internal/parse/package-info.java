/**
 * Reads authored {@code .graphql} files and validates one operation against the schema.
 *
 * <p>Classifies every input file as an operation or a fragment, resolves the fragments an
 * operation transitively references, and hands the validated, assembled document to later
 * analysis stages.
 */
@NullMarked
package dev.glisseo.izar.compiler.internal.parse;

import org.jspecify.annotations.NullMarked;
