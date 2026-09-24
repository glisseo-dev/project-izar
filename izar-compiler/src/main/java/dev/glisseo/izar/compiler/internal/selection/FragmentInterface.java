package dev.glisseo.izar.compiler.internal.selection;

import java.util.List;

/**
 * A named fragment made entirely of leaf fields (scalars, {@code __typename}, and lists of either),
 * generated as a standalone Java interface with one no-argument accessor per field. Unlike every
 * other generated type in this compiler, it is not nested inside one operation's class: it is
 * written once per compiler invocation as its own top-level file, so every record (across every
 * operation) that merges the fragment's fields in through an unconditional spread can {@code
 * implements} the same type. See {@link FragmentInterfaceAnalyzer} for exactly which fragments
 * qualify and why.
 *
 * @param javaTypeName the generated interface's simple name, taken verbatim from the GraphQL
 *     fragment name (already unique across the whole compiler invocation, since {@code
 *     FragmentLibrary} rejects two fragments sharing a name)
 * @param fields the fragment's own selected fields, one per response key, exactly as {@link
 *     FieldSelection} already models a merged record's fields
 */
public record FragmentInterface(String javaTypeName, List<FieldSelection> fields) {}
