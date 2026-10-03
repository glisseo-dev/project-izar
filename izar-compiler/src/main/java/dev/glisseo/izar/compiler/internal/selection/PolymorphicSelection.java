package dev.glisseo.izar.compiler.internal.selection;

import java.util.List;

/**
 * An interface- or union-typed selection set that distinguishes concrete types, generated as a
 * sealed interface: one record per concrete type named by a type-conditioned fragment, plus an
 * {@code Unrecognized} record for any other concrete type the live server may return. Every
 * branch's fields already include the selection's shared fields merged in, so each is renderable
 * as an ordinary {@link ObjectSelection}, the same as ordinary object-typed fields.
 *
 * <p>A field whose selection contains no type-conditioned fragment does not produce a {@code
 * PolymorphicSelection} at all: {@link SelectionAnalyzer} generates a plain {@link ObjectSelection}
 * of shared fields instead, since no concrete-type distinction was asked for.
 *
 * @param javaTypeName the generated sealed interface's simple name, unique within its enclosing
 *     operation
 * @param branches one merged record per concrete type named by a type-conditioned fragment
 * @param unrecognizedBranch shared fields plus the raw type name, for any concrete type not named
 *     by {@code branches}
 * @param discriminatorResponseKey the response key decode reads the concrete type name from;
 *     {@code __typename} unless that key was already in use for something else in this selection
 * @param hoistedSharedFields the subset of shared fields declared as abstract accessors directly
 *     on the sealed interface, so callers can read them without a {@code switch}; every branch
 *     (including {@code unrecognizedBranch}) still declares the same field among its own {@link
 *     ObjectSelection#fields()}, satisfying the interface method through its record accessor. A
 *     shared field is only hoisted when every branch resolved it identically, so one a branch
 *     re-selects with extra sub-fields (narrowing its type) is left out and stays branch-only.
 */
public record PolymorphicSelection(
        String javaTypeName,
        List<PolymorphicBranch> branches,
        ObjectSelection unrecognizedBranch,
        String discriminatorResponseKey,
        List<FieldSelection> hoistedSharedFields) {}
