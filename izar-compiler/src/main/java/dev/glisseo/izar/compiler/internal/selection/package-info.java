/**
 * Walks a validated operation's or fragment's selection set to decide the Java shape it produces.
 *
 * <p>Merges every fragment spread and inline fragment in place, resolves polymorphic fields to a
 * sealed interface with one branch per concrete type, and decides which named fragments are
 * eligible for standalone Java interface generation. {@code SourceGenerator} renders the trees
 * this package builds; it does not build them itself.
 */
@NullMarked
package dev.glisseo.izar.compiler.internal.selection;

import org.jspecify.annotations.NullMarked;
