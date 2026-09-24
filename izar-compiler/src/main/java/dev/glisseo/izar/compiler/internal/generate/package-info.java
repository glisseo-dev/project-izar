/**
 * Renders selection and variable analysis trees into Java source text.
 *
 * <p>A pure function of its inputs: identical selection and variable shapes always produce
 * byte-identical source. Owns no analysis of its own; it only renders what {@code selection} and
 * {@code variable} already decided.
 */
@NullMarked
package dev.glisseo.izar.compiler.internal.generate;

import org.jspecify.annotations.NullMarked;
