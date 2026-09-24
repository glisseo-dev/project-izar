/**
 * Optional custom-scalar mappings that applications select explicitly.
 *
 * <p>Each {@link dev.glisseo.izar.scalars.InstantScalarCodec}-style class pairs a Java type with
 * encoding and decoding behavior that round-trips through generated inputs and responses, matching
 * a representative GraphQL Java Extended Scalars server-side scalar ({@code DateTime}, {@code
 * Date}, and {@code BigDecimal}). Every mapping here uses an immutable Java representation ({@code
 * java.time} types and {@link java.math.BigDecimal}), so no defensive copying is needed to keep
 * generated models immutable.
 *
 * <p>Nothing here is applied automatically: an application configures a {@code
 * dev.glisseo.izar.compiler.ScalarMapping} naming the GraphQL scalar, the Java type, and one of
 * these codec classes (or its own, which needs no dependency on this module at all) explicitly.
 * Generation fails on an unmapped custom scalar rather than guessing a representation.
 */
@NullMarked
package dev.glisseo.izar.scalars;

import org.jspecify.annotations.NullMarked;
