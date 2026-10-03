/**
 * Build-time generation of Java models, operation metadata, and executable documents.
 *
 * <p>GraphQL Java supplies parsing, schema handling, and validation. This module owns
 * selection-specific Java generation and the mapping behavior that goes with it. This root
 * package is the module's public API: {@link dev.glisseo.izar.compiler.OperationCompiler} is the
 * only entry point, {@link dev.glisseo.izar.compiler.OperationGenerationException} the only
 * exception it throws, and {@link dev.glisseo.izar.compiler.ScalarMapping} the only configuration
 * type a caller supplies. Everything else lives under {@link dev.glisseo.izar.compiler.internal},
 * one subpackage per pipeline stage, public only so the stages can call each other; no other
 * module depends on them directly:
 *
 * <ul>
 *   <li>{@code internal.schema} loads the schema.
 *   <li>{@code internal.parse} reads and validates authored operation files.
 *   <li>{@code internal.selection} decides the Java shape of a selection set.
 *   <li>{@code internal.variable} decides the Java shape of declared variables.
 *   <li>{@code internal.scalar} resolves scalars for both of the above.
 *   <li>{@code internal.document} assembles and rewrites the final executable document.
 *   <li>{@code internal.generate} renders selection and variable trees as Java source.
 *   <li>{@code internal.naming} formats GraphQL names as Java identifiers and literals.
 * </ul>
 *
 * <p>Authored operation files are never rewritten. The compiler produces a separate final
 * executable document, adding the type discriminators polymorphic decoding needs and inlining
 * the fragments an operation references, then derives operation IDs and manifest bodies from
 * that final document.
 *
 * <p>Generation reads a checked-in schema or a pinned schema artifact and performs no live
 * introspection. Identical inputs produce identical sources, documents, IDs, and manifests. An
 * unsupported operation feature or an unmapped custom scalar fails with a diagnostic rather
 * than producing a silently incomplete model.
 *
 * <p>The compiler has no Maven dependency, so it can run independently of the Maven plugin.
 */
@NullMarked
package dev.glisseo.izar.compiler;

import org.jspecify.annotations.NullMarked;
