package dev.glisseo.izar.scalars;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

class BigDecimalScalarCodecTest {

    private final BigDecimalScalarCodec codec = new BigDecimalScalarCodec();

    @Test
    void roundTripsAPrecisionSensitiveValueThroughItsWireStringRepresentation() {
        BigDecimal value = new BigDecimal("19.999999999999999999999999999");

        Object encoded = codec.encode(value);
        assertThat(encoded).isEqualTo("19.999999999999999999999999999");
        assertThat(codec.decode(encoded)).isEqualByComparingTo(value);
    }

    @Test
    void decodesAWireNumberWithoutLosingPrecision() {
        assertThat(codec.decode(new BigDecimal("100.5"))).isEqualByComparingTo("100.5");
    }

    @Test
    void decodeFailsUsefullyForAMalformedString() {
        assertThatThrownBy(() -> codec.decode("not-a-number"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("not-a-number");
    }

    @Test
    void decodeFailsUsefullyForAnIncompatibleValue() {
        assertThatThrownBy(() -> codec.decode(true)).isInstanceOf(IllegalArgumentException.class);
    }
}
