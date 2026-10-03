package dev.glisseo.izar.manifest.assembly;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class ExactVersionTest {

    @ParameterizedTest
    @ValueSource(strings = {"LATEST", "latest", "RELEASE", "Release", "[1.0,2.0)", "(1.0,)", "1.0,2.0"})
    void rejectsRangesAndMetaVersions(String version) {
        assertThatThrownBy(() -> ExactVersion.require(version, "izar.deploy.version"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("izar.deploy.version '" + version + "' is not an exact version");
    }

    @ParameterizedTest
    @ValueSource(strings = {"1.0.0", "2026.09", "1.0-SNAPSHOT", "1.0.0-rc.1"})
    void acceptsAnExactVersion(String version) {
        assertThatCode(() -> ExactVersion.require(version, "version")).doesNotThrowAnyException();
    }
}
