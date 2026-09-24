package dev.glisseo.izar.operation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class GraphQlEncodingTest {

    @Test
    void copyListReturnsNullForANullSource() {
        assertThat(GraphQlEncoding.copyList(null)).isNull();
    }

    @Test
    void copyListPreservesNullElements() {
        List<String> source = new ArrayList<>();
        source.add("a");
        source.add(null);

        assertThat(GraphQlEncoding.copyList(source)).containsExactly("a", null);
    }

    @Test
    void copyListIsolatesTheResultFromLaterMutationOfTheSource() {
        List<String> source = new ArrayList<>();
        source.add("a");

        List<String> copy = GraphQlEncoding.copyList(source);
        source.add("b");

        assertThat(copy).containsExactly("a");
    }

    @Test
    void copyListResultRejectsMutation() {
        List<String> copy = GraphQlEncoding.copyList(List.of("a"));

        assertThatThrownBy(() -> copy.add("b")).isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void encodeListReturnsNullForANullSource() {
        assertThat(GraphQlEncoding.encodeList(null, element -> element)).isNull();
    }

    @Test
    void encodeListAppliesTheEncoderToEachNonNullElementAndLeavesNullElementsAlone() {
        List<String> source = new ArrayList<>();
        source.add("a");
        source.add(null);

        List<Object> encoded = GraphQlEncoding.encodeList(source, String::toUpperCase);

        assertThat(encoded).containsExactly("A", null);
    }

    @Test
    void encodeListResultRejectsMutation() {
        List<Object> encoded = GraphQlEncoding.encodeList(List.of("a"), element -> element);

        assertThatThrownBy(() -> encoded.add("b")).isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void encodeNonNullElementListReturnsNullForANullSource() {
        assertThat(GraphQlEncoding.encodeNonNullElementList(null, element -> element)).isNull();
    }

    @Test
    void encodeNonNullElementListAppliesTheEncoderToEachElement() {
        List<String> source = List.of("a", "b");

        List<Object> encoded = GraphQlEncoding.encodeNonNullElementList(source, String::toUpperCase);

        assertThat(encoded).containsExactly("A", "B");
    }

    @Test
    void encodeNonNullElementListRejectsANullElement() {
        List<String> source = new ArrayList<>();
        source.add("a");
        source.add(null);

        assertThatThrownBy(() -> GraphQlEncoding.encodeNonNullElementList(source, element -> element))
                .isInstanceOf(IllegalStateException.class);
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
    void encodeScalarDelegatesToTheCodec() {
        assertThat(GraphQlEncoding.encodeScalar("ABC", UPPERCASING_CODEC, "Custom")).isEqualTo("abc");
    }

    @Test
    void encodeScalarWrapsACodecFailureWithTheScalarName() {
        ScalarCodec<String> failing =
                new ScalarCodec<>() {
                    @Override
                    public String decode(Object raw) {
                        return (String) raw;
                    }

                    @Override
                    public Object encode(String value) {
                        throw new IllegalStateException("cannot represent this value");
                    }
                };

        assertThatThrownBy(() -> GraphQlEncoding.encodeScalar("x", failing, "Money"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Money")
                .hasMessageContaining("cannot represent this value");
    }
}
