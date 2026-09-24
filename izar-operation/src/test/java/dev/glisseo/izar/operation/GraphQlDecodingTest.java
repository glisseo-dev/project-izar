package dev.glisseo.izar.operation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class GraphQlDecodingTest {

    /** Mirrors what {@code SourceGenerator} emits for a selected output enum. */
    sealed interface Genre {
        enum Known implements Genre {
            FICTION,
            NONFICTION
        }

        record Unrecognized(String rawValue) implements Genre {}
    }

    @Test
    void decodeEnumReturnsTheKnownConstantForARecognizedValue() {
        Genre genre =
                GraphQlDecoding.decodeEnum(
                        "FICTION", Genre.Known.class, known -> known, Genre.Unrecognized::new, "Genre", DecodingPolicy.LENIENT);

        assertThat(genre).isEqualTo(Genre.Known.FICTION);
    }

    @Test
    void decodeEnumPreservesAnUnrecognizedValueUnderTheLenientPolicy() {
        Genre genre =
                GraphQlDecoding.decodeEnum(
                        "MYSTERY", Genre.Known.class, known -> known, Genre.Unrecognized::new, "Genre", DecodingPolicy.LENIENT);

        assertThat(genre).isEqualTo(new Genre.Unrecognized("MYSTERY"));
    }

    @Test
    void decodeEnumFailsUsefullyForAnUnrecognizedValueUnderTheStrictPolicy() {
        assertThatThrownBy(
                        () ->
                                GraphQlDecoding.decodeEnum(
                                        "MYSTERY",
                                        Genre.Known.class,
                                        known -> known,
                                        Genre.Unrecognized::new,
                                        "Genre",
                                        DecodingPolicy.STRICT))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Genre")
                .hasMessageContaining("MYSTERY");
    }

    @Test
    void decodeEnumFailsUsefullyWhenTheValueIsNotAString() {
        assertThatThrownBy(
                        () ->
                                GraphQlDecoding.decodeEnum(
                                        42,
                                        Genre.Known.class,
                                        known -> known,
                                        Genre.Unrecognized::new,
                                        "Genre",
                                        DecodingPolicy.LENIENT))
                .isInstanceOf(IllegalArgumentException.class);
    }

    private static final ScalarCodec<String> UPPERCASING_CODEC =
            new ScalarCodec<>() {
                @Override
                public String decode(Object raw) {
                    return ((String) raw).toUpperCase();
                }

                @Override
                public Object encode(String value) {
                    return value.toLowerCase();
                }
            };

    @Test
    void decodeScalarDelegatesToTheCodec() {
        assertThat(GraphQlDecoding.decodeScalar("abc", UPPERCASING_CODEC, "Custom")).isEqualTo("ABC");
    }

    @Test
    void decodeScalarFailsUsefullyForANullValue() {
        assertThatThrownBy(() -> GraphQlDecoding.decodeScalar(null, UPPERCASING_CODEC, "Custom"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Custom");
    }

    @Test
    void decodeScalarWrapsACodecFailureWithTheScalarName() {
        ScalarCodec<String> failing =
                new ScalarCodec<>() {
                    @Override
                    public String decode(Object raw) {
                        throw new NumberFormatException("not a number");
                    }

                    @Override
                    public Object encode(String value) {
                        return value;
                    }
                };

        assertThatThrownBy(() -> GraphQlDecoding.decodeScalar("abc", failing, "Money"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Money")
                .hasMessageContaining("not a number");
    }
}
