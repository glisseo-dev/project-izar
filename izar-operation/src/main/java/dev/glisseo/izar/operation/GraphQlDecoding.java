package dev.glisseo.izar.operation;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import org.jspecify.annotations.Nullable;

/**
 * Scalar and object decoding helpers generated {@link GraphQlOperation#decode} implementations
 * call.
 *
 * <p>These operate on the raw, already-parsed response tree ({@code Map<String, Object>},
 * {@code List<Object>}, and JSON scalar values) that {@code GraphQlResponse.getData()} returns.
 * Nullability of individual fields is handled by the generated call site, not by these helpers: a
 * generated decoder checks a field's value for {@code null} itself and only calls the matching
 * {@code asX} helper when a value is present. Each helper still accepts a {@code @Nullable}
 * value and fails with a clear message rather than a raw {@code NullPointerException} if that
 * expectation is ever violated, since the root {@code data} value itself is nullable.
 */
public final class GraphQlDecoding {

    private GraphQlDecoding() {}

    /** Decodes an object selection's value as a field-name-to-value map. */
    @SuppressWarnings("unchecked")
    public static Map<String, Object> asMap(@Nullable Object value) {
        if (value instanceof Map<?, ?> map) {
            return (Map<String, Object>) map;
        }
        throw new IllegalArgumentException("Expected a GraphQL object value, got " + describe(value));
    }

    /** Decodes a GraphQL {@code String} value. */
    public static String asString(@Nullable Object value) {
        if (value instanceof String string) {
            return string;
        }
        throw new IllegalArgumentException("Expected a GraphQL String value, got " + describe(value));
    }

    /** Decodes a GraphQL {@code Int} value. */
    public static Integer asInt(@Nullable Object value) {
        if (value instanceof Number number) {
            return number.intValue();
        }
        throw new IllegalArgumentException("Expected a GraphQL Int value, got " + describe(value));
    }

    /** Decodes a GraphQL {@code Float} value. */
    public static Double asFloat(@Nullable Object value) {
        if (value instanceof Number number) {
            return number.doubleValue();
        }
        throw new IllegalArgumentException("Expected a GraphQL Float value, got " + describe(value));
    }

    /** Decodes a GraphQL {@code Boolean} value. */
    public static Boolean asBoolean(@Nullable Object value) {
        if (value instanceof Boolean bool) {
            return bool;
        }
        throw new IllegalArgumentException("Expected a GraphQL Boolean value, got " + describe(value));
    }

    /** Decodes a GraphQL {@code ID} value. IDs are represented as {@link String} in Java. */
    public static String asID(@Nullable Object value) {
        if (value instanceof String string) {
            return string;
        }
        if (value instanceof Number number) {
            return number.toString();
        }
        throw new IllegalArgumentException("Expected a GraphQL ID value, got " + describe(value));
    }

    /**
     * Decodes a GraphQL enum value into either its known constant or an {@code Unrecognized}
     * representation, depending on {@code policy}.
     *
     * <p>A generated sealed interface for an output enum type has exactly two implementations: a
     * nested {@code Known} enum whose constant names match the schema's enum values, and an {@code
     * Unrecognized} record carrying the raw wire value. {@code onKnown} and {@code onUnrecognized}
     * construct the sealed result from each case; generated call sites pass {@code known -> known}
     * and a record constructor reference, since {@code Known} already implements the sealed
     * interface.
     *
     * @param onKnown wraps a matched {@code Known} constant into the sealed result type
     * @param onUnrecognized wraps the raw value into the sealed result type's {@code Unrecognized}
     *     case
     * @param graphqlEnumName the schema enum's name, for the strict-policy failure message
     * @throws IllegalArgumentException if {@code value} is not a GraphQL enum value, or if it is
     *     unrecognized and {@code policy} is {@link DecodingPolicy#STRICT}
     */
    public static <TSealed, TKnown extends Enum<TKnown>> TSealed decodeEnum(
            @Nullable Object value,
            Class<TKnown> knownType,
            Function<TKnown, TSealed> onKnown,
            Function<String, TSealed> onUnrecognized,
            String graphqlEnumName,
            DecodingPolicy policy) {
        String raw = asString(value);
        for (TKnown constant : knownType.getEnumConstants()) {
            if (constant.name().equals(raw)) {
                return onKnown.apply(constant);
            }
        }
        if (policy == DecodingPolicy.STRICT) {
            throw new IllegalArgumentException(
                    "Expected a known value of GraphQL enum '" + graphqlEnumName + "', got '" + raw + "'.");
        }
        return onUnrecognized.apply(raw);
    }

    /**
     * Decodes a custom scalar value through {@code codec}, the application- or library-supplied
     * mapping generated code was configured with for {@code graphqlScalarName}.
     *
     * @throws IllegalArgumentException if {@code value} is {@code null}, or if {@code codec}
     *     rejects it
     */
    public static <T> T decodeScalar(@Nullable Object value, ScalarCodec<T> codec, String graphqlScalarName) {
        if (value == null) {
            throw new IllegalArgumentException("Expected a GraphQL '" + graphqlScalarName + "' value, got null.");
        }
        try {
            return codec.decode(value);
        } catch (RuntimeException e) {
            throw new IllegalArgumentException(
                    "Could not decode GraphQL '"
                            + graphqlScalarName
                            + "' value ("
                            + describe(value)
                            + "): "
                            + e.getMessage(),
                    e);
        }
    }

    /**
     * Decodes a GraphQL list value, applying {@code elementDecoder} to every element including
     * {@code null} ones: whether a {@code null} element is valid, and how it decodes, is the
     * generated element decoder's own responsibility (it null-checks itself when the schema
     * permits nullable elements), the same way a top-level field's decode call does.
     */
    public static <T> List<T> decodeList(@Nullable Object value, Function<Object, T> elementDecoder) {
        if (!(value instanceof List<?> list)) {
            throw new IllegalArgumentException("Expected a GraphQL list value, got " + describe(value));
        }
        List<T> result = new ArrayList<>(list.size());
        for (Object element : list) {
            result.add(elementDecoder.apply(element));
        }
        return Collections.unmodifiableList(result);
    }

    private static String describe(@Nullable Object value) {
        return value == null ? "null" : value.getClass().getName();
    }
}
