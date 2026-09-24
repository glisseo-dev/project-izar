package dev.glisseo.izar.operation;

/**
 * Encodes and decodes one custom GraphQL scalar as a specific Java type {@code T}, generated code
 * calls into on both the input and output side of an operation that uses the scalar.
 *
 * <p>{@code T} must itself be immutable, or {@code decode} and {@code encode} must defensively
 * copy: a generated response record or built input is supposed to be immutable regardless of what
 * a caller does with a value afterward, and a mutable scalar representation breaks that guarantee
 * unless the codec protects it. {@code java.time} types and {@link java.math.BigDecimal} satisfy
 * this without any extra work; a mutable representation (a {@link java.util.Date}, for example)
 * needs a defensive copy on the way in and out.
 *
 * <p>A generated call site supplies one codec instance per distinct custom scalar, constructed
 * through the class's public no-argument constructor, and passes the already-extracted raw wire
 * value (never {@code null}: absence is handled by the generated call site before a codec is ever
 * invoked).
 *
 * @param <T> the Java representation applications see in generated records, builders, and
 *     accessors
 */
public interface ScalarCodec<T> {

    /**
     * Decodes {@code raw}, one value already extracted from a parsed GraphQL response (a {@link
     * String}, {@link Number}, {@link Boolean}, {@link java.util.List}, or {@link java.util.Map},
     * per how the scalar is represented on the wire).
     *
     * @throws RuntimeException if {@code raw} is not a valid representation of this scalar;
     *     {@link GraphQlDecoding#decodeScalar} wraps it with the scalar's name for a clearer
     *     diagnostic
     */
    T decode(Object raw);

    /**
     * Encodes {@code value} into the plain JSON-compatible representation sent as a GraphQL
     * variable.
     *
     * @throws RuntimeException if {@code value} cannot be represented in this scalar; {@link
     *     GraphQlEncoding#encodeScalar} wraps it with the scalar's name for a clearer diagnostic
     */
    Object encode(T value);
}
