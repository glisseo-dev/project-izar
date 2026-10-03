package dev.glisseo.izar.manifest.analysis;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;

class OperationRefOrderTest {

    @Test
    void versionsOrderNumericallyNotLexicographically() {
        var refs = List.of(ref("catalog", "1.10"), ref("catalog", "1.9"), ref("catalog", "1.2"), ref("catalog", "1.9.1"));

        assertThat(refs.stream().sorted().map(OperationRef::manifestVersion))
                .containsExactly("1.2", "1.9", "1.9.1", "1.10");
    }

    @Test
    void clientNameOrdersBeforeVersion() {
        var refs = List.of(ref("writers", "1.0"), ref("catalog", "2.0"));

        assertThat(refs.stream().sorted().map(OperationRef::clientName)).containsExactly("catalog", "writers");
    }

    @Test
    void textualVersionsStillOrderTotally() {
        var refs = List.of(ref("c", "1.beta"), ref("c", "1.0"), ref("c", "1.00"));

        assertThat(refs.stream().sorted().map(OperationRef::manifestVersion)).containsExactly("1.0", "1.00", "1.beta");
    }

    private static OperationRef ref(String client, String version) {
        return new OperationRef("op", "Op", client, version);
    }
}
