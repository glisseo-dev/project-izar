package dev.glisseo.izar.scalars;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import org.junit.jupiter.api.Test;

class InstantScalarCodecTest {

    private final InstantScalarCodec codec = new InstantScalarCodec();

    @Test
    void roundTripsAnIsoInstantThroughItsWireStringRepresentation() {
        Instant instant = Instant.parse("2026-09-11T10:15:30.123456789Z");

        Object encoded = codec.encode(instant);
        assertThat(encoded).isEqualTo("2026-09-11T10:15:30.123456789Z");
        assertThat(codec.decode(encoded)).isEqualTo(instant);
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
