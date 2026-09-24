package dev.glisseo.izar.operation;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.function.Function;
import org.jspecify.annotations.Nullable;

/**
 * List helpers generated builder setters and {@code encode()} methods call.
 *
 * <p>{@link #copyList} takes the defensive copy a builder setter stores: an immutable snapshot
 * that a caller mutating their own source list afterward cannot affect, without rejecting the
 * {@code null} elements a nullable list element type permits. {@link #encodeList} and {@link
 * #encodeNonNullElementList} later turn a stored domain list into its wire representation,
 * applying {@code elementEncoder} to each element; they differ only in what a {@code null}
 * element means, matching whichever the schema's list element type permits.
 */
public final class GraphQlEncoding {

    private GraphQlEncoding() {}

    /** A null-tolerant immutable copy of {@code source}, or {@code null} if {@code source} is. */
    public static <T> @Nullable List<T> copyList(@Nullable List<? extends T> source) {
        if (source == null) {
            return null;
        }
        return Collections.unmodifiableList(new ArrayList<>(source));
    }

    /**
     * Encodes {@code source} into its wire representation: each non-null element passed through
     * {@code elementEncoder}, each {@code null} element left as {@code null}, or {@code null} if
     * {@code source} itself is.
     */
    public static <T> @Nullable List<Object> encodeList(
            @Nullable List<T> source, Function<T, Object> elementEncoder) {
        if (source == null) {
            return null;
        }
        List<Object> result = new ArrayList<>(source.size());
        for (T element : source) {
            result.add(element == null ? null : elementEncoder.apply(element));
        }
        return Collections.unmodifiableList(result);
    }

    /**
     * Encodes a custom scalar value through {@code codec}, the application- or library-supplied
     * mapping generated code was configured with for {@code graphqlScalarName}.
     *
     * @throws IllegalArgumentException if {@code codec} rejects {@code value}
     */
    public static <T> Object encodeScalar(T value, ScalarCodec<T> codec, String graphqlScalarName) {
        try {
            return codec.encode(value);
        } catch (RuntimeException e) {
            throw new IllegalArgumentException(
                    "Could not encode GraphQL '" + graphqlScalarName + "' value " + value + ": " + e.getMessage(), e);
        }
    }

    /**
     * Encodes {@code source} the same way {@link #encodeList} does, except a {@code null}
     * element is a validation failure rather than a value to pass through: the schema's list
     * element type is non-null, so a builder must catch this at {@code build()} rather than let
     * it reach a request.
     *
     * @throws IllegalStateException if any element of {@code source} is {@code null}
     */
    public static <T> @Nullable List<Object> encodeNonNullElementList(
            @Nullable List<T> source, Function<T, Object> elementEncoder) {
        if (source == null) {
            return null;
        }
        List<Object> result = new ArrayList<>(source.size());
        for (T element : source) {
            if (element == null) {
                throw new IllegalStateException(
                        "A list element was null, but the schema declares this list's elements non-null.");
            }
            result.add(elementEncoder.apply(element));
        }
        return Collections.unmodifiableList(result);
    }
}
