package dev.glisseo.izar.compiler.internal.selection;

import java.util.List;

/**
 * An object-typed selection set, generated as one immutable Java record.
 *
 * @param javaTypeName the generated record's simple name, unique within its enclosing operation
 * @param fields the selected fields, one per distinct response key, in first-seen order across
 *     every direct selection and expanded fragment that contributes to this selection set
 * @param implementsFragments the simple names of every leaf-only {@link FragmentInterface} this
 *     selection set includes through a direct, unconditional spread (see {@link
 *     FragmentInterfaceAnalyzer}); for a polymorphic branch or its unrecognized catch-all, this
 *     also includes a fragment spread unconditionally at the shared level of the polymorphic
 *     field's selection, since those fields are merged into every branch (and the catch-all) the
 *     same way
 */
public record ObjectSelection(String javaTypeName, List<FieldSelection> fields, List<String> implementsFragments) {}
