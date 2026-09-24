/**
 * Walks an operation's declared variables to decide the Java shape generated builders need.
 *
 * <p>Mirrors the {@code selection} package for the input direction: resolves every reachable
 * input object and enum type once, tolerating recursive input types. {@code SourceGenerator}
 * renders the trees this package builds; it does not build them itself.
 */
@NullMarked
package dev.glisseo.izar.compiler.internal.variable;

import org.jspecify.annotations.NullMarked;
