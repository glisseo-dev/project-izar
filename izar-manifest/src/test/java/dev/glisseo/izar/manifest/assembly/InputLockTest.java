package dev.glisseo.izar.manifest.assembly;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import org.junit.jupiter.api.Test;

class InputLockTest {

    private static final InputLock LOCK = new InputLock(
            "nightsky",
            "production",
            List.of(new LockedRelease(new ClientRelease("web-app", "1.2.0"), "abc123", 2)));

    @Test
    void toJsonThenFromJsonRoundTrips() {
        assertThat(InputLock.fromJson(LOCK.toJson())).isEqualTo(LOCK);
    }

    @Test
    void rejectsABlankEnvironment() {
        assertThatThrownBy(() -> new InputLock("nightsky", " ", List.of()))
                .isInstanceOf(AssemblyException.class)
                .hasMessageContaining("environment");
    }

    @Test
    void fromJsonRejectsMalformedJson() {
        assertThatThrownBy(() -> InputLock.fromJson("not json")).isInstanceOf(AssemblyException.class);
    }
}
