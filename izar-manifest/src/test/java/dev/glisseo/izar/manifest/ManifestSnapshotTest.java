package dev.glisseo.izar.manifest;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import org.junit.jupiter.api.Test;

class ManifestSnapshotTest {

    private static final OperationManifest MANIFEST =
            OperationManifest.of(List.of(ManifestOperation.of("GetBook", "query", "query GetBook { book { title } }")));

    @Test
    void rejectsABlankRevision() {
        assertThatThrownBy(() -> new ManifestSnapshot(" ", MANIFEST))
                .isInstanceOf(InvalidManifestException.class)
                .hasMessageContaining("revision");
    }

    @Test
    void rejectsANullManifest() {
        assertThatThrownBy(() -> new ManifestSnapshot("rev-1", null))
                .isInstanceOf(InvalidManifestException.class)
                .hasMessageContaining("manifest");
    }
}
