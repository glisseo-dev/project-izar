package dev.glisseo.izar.manifest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class PersistedQueryExtensionTest {

    @Test
    void ofDerivesTheSameIdManifestGenerationWould() {
        String document = "query GetBook { book { title } }";

        PersistedQueryExtension extension = PersistedQueryExtension.of(document);

        assertThat(extension.version()).isEqualTo(PersistedQueryExtension.CURRENT_VERSION);
        assertThat(extension.sha256Hash()).isEqualTo(ManifestOperation.idFor(document));
    }

    @Test
    void toExtensionValueMatchesTheApolloCompatibleShape() {
        PersistedQueryExtension extension = new PersistedQueryExtension(1, "abc123");

        assertThat(extension.toExtensionValue()).isEqualTo(Map.of("version", 1, "sha256Hash", "abc123"));
    }

    @Test
    void fromReadsBackWhatToExtensionValueProduced() {
        PersistedQueryExtension original = PersistedQueryExtension.of("query GetBook { book { title } }");
        Map<String, Object> extensions = Map.of(PersistedQueryExtension.EXTENSION_NAME, original.toExtensionValue());

        assertThat(PersistedQueryExtension.from(extensions)).contains(original);
    }

    @Test
    void fromIsEmptyWhenTheExtensionIsAbsent() {
        assertThat(PersistedQueryExtension.from(Map.of())).isEmpty();
    }

    @Test
    void fromIsEmptyWhenThePersistedQueryValueIsNotShapedAsExpected() {
        Optional<PersistedQueryExtension> result =
                PersistedQueryExtension.from(Map.of(PersistedQueryExtension.EXTENSION_NAME, "not-a-map"));

        assertThat(result).isEmpty();
    }

    @Test
    void fromIsEmptyWhenThePersistedQueryVersionIsUnsupported() {
        Optional<PersistedQueryExtension> result = PersistedQueryExtension.from(Map.of(
                PersistedQueryExtension.EXTENSION_NAME, Map.of("version", 2, "sha256Hash", "abc123")));

        assertThat(result).isEmpty();
    }

    @Test
    void fromIsEmptyWhenThePersistedQueryVersionIsNotAnInteger() {
        Optional<PersistedQueryExtension> result = PersistedQueryExtension.from(Map.of(
                PersistedQueryExtension.EXTENSION_NAME, Map.of("version", 1.5, "sha256Hash", "abc123")));

        assertThat(result).isEmpty();
    }

    @Test
    void fromIsEmptyWhenThePersistedQueryHashIsBlank() {
        Optional<PersistedQueryExtension> result = PersistedQueryExtension.from(Map.of(
                PersistedQueryExtension.EXTENSION_NAME, Map.of("version", 1, "sha256Hash", " ")));

        assertThat(result).isEmpty();
    }

    @Test
    void rejectsABlankSha256Hash() {
        assertThatThrownBy(() -> new PersistedQueryExtension(1, " "))
                .isInstanceOf(InvalidManifestException.class)
                .hasMessageContaining("sha256Hash");
    }
}
