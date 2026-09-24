package dev.glisseo.izar.manifest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class EnforcementReportTest {

    @Test
    void toExtensionValueMatchesTheDocumentedShape() {
        EnforcementReport report = new EnforcementReport("ENFORCE", true, "rev-1");

        assertThat(report.toExtensionValue())
                .isEqualTo(Map.of("mode", "ENFORCE", "registered", true, "revision", "rev-1"));
    }

    @Test
    void fromReadsBackWhatToExtensionValueProduced() {
        EnforcementReport original = new EnforcementReport("AUDIT", false, "rev-2");
        Map<String, Object> extensions = Map.of(EnforcementReport.EXTENSION_NAME, original.toExtensionValue());

        assertThat(EnforcementReport.from(extensions)).contains(original);
    }

    @Test
    void fromIsEmptyWhenTheExtensionIsAbsent() {
        assertThat(EnforcementReport.from(Map.of())).isEmpty();
    }

    @Test
    void fromIsEmptyWhenTheIzarValueIsNotShapedAsExpected() {
        Optional<EnforcementReport> result = EnforcementReport.from(Map.of(EnforcementReport.EXTENSION_NAME, "not-a-map"));

        assertThat(result).isEmpty();
    }

    @Test
    void rejectsABlankMode() {
        assertThatThrownBy(() -> new EnforcementReport(" ", true, "rev-1"))
                .isInstanceOf(InvalidManifestException.class)
                .hasMessageContaining("mode");
    }

    @Test
    void rejectsABlankRevision() {
        assertThatThrownBy(() -> new EnforcementReport("ENFORCE", true, " "))
                .isInstanceOf(InvalidManifestException.class)
                .hasMessageContaining("revision");
    }
}
