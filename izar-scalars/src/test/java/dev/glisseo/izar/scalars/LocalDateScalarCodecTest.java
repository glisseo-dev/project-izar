package dev.glisseo.izar.scalars;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.LocalDate;
import org.junit.jupiter.api.Test;

class LocalDateScalarCodecTest {

    private final LocalDateScalarCodec codec = new LocalDateScalarCodec();

    @Test
    void roundTripsAnIsoDateThroughItsWireStringRepresentation() {
        LocalDate date = LocalDate.of(2026, 9, 11);

        Object encoded = codec.encode(date);
        assertThat(encoded).isEqualTo("2026-09-11");
        assertThat(codec.decode(encoded)).isEqualTo(date);
    }

    @Test
    void decodeFailsUsefullyForAMalformedString() {
        assertThatThrownBy(() -> codec.decode("not-a-date"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("not-a-date");
    }

    @Test
    void decodeFailsUsefullyForANonStringValue() {
        assertThatThrownBy(() -> codec.decode(42)).isInstanceOf(IllegalArgumentException.class);
    }
}
