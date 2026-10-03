package dev.glisseo.izar.manifest.assembly;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class ClientReleaseTest {

    @Test
    void ordersByClientNameThenManifestVersion() {
        ClientRelease batchJob = new ClientRelease("batch-job", "2026.1");
        ClientRelease webApp120 = new ClientRelease("web-app", "1.2.0");
        ClientRelease webApp130 = new ClientRelease("web-app", "1.3.0");

        assertThat(batchJob).isLessThan(webApp120);
        assertThat(webApp120).isLessThan(webApp130);
    }

    @Test
    void toStringCombinesNameAndVersion() {
        assertThat(new ClientRelease("web-app", "1.2.0")).hasToString("web-app@1.2.0");
    }

    @Test
    void rejectsABlankClientName() {
        assertThatThrownBy(() -> new ClientRelease(" ", "1.2.0"))
                .isInstanceOf(AssemblyException.class)
                .hasMessageContaining("clientName");
    }

    @ParameterizedTest
    @ValueSource(strings = {"..", ".", "../x", "a/b", "a\\b", "c:d"})
    void rejectsAClientNameThatIsNotASafePathSegment(String clientName) {
        assertThatThrownBy(() -> new ClientRelease(clientName, "1.2.0"))
                .isInstanceOf(AssemblyException.class)
                .hasMessageContaining("clientName")
                .hasMessageContaining("path segment");
    }

    @ParameterizedTest
    @ValueSource(strings = {"..", ".", "../x", "a/b", "a\\b", "c:d"})
    void rejectsAManifestVersionThatIsNotASafePathSegment(String manifestVersion) {
        assertThatThrownBy(() -> new ClientRelease("web-app", manifestVersion))
                .isInstanceOf(AssemblyException.class)
                .hasMessageContaining("manifestVersion")
                .hasMessageContaining("path segment");
    }

    @Test
    void rejectsABlankManifestVersion() {
        assertThatThrownBy(() -> new ClientRelease("web-app", " "))
                .isInstanceOf(AssemblyException.class)
                .hasMessageContaining("manifestVersion");
    }
}
