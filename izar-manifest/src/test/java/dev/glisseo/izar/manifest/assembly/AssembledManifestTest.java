package dev.glisseo.izar.manifest.assembly;

import dev.glisseo.izar.manifest.ManifestOperation;
import dev.glisseo.izar.manifest.OperationManifest;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class AssembledManifestTest {

    @Test
    void writesManifestProvenanceAndLockAsSeparateFiles(@TempDir Path dir) throws Exception {
        ManifestOperation operation = ManifestOperation.of("GetBook", "query", "query GetBook { book { title } }");
        ClientRelease release = new ClientRelease("web-app", "1.2.0");
        AssembledManifest assembled = new AssembledManifest(
                OperationManifest.of(List.of(operation)),
                List.of(new OperationProvenance(operation.id(), List.of(release))),
                new InputLock("nightsky", "production", List.of(new LockedRelease(release, "abc123", 1))));

        Path outputDirectory = dir.resolve("out");
        assembled.writeTo(outputDirectory);

        assertThat(OperationManifest.fromJson(Files.readString(outputDirectory.resolve("manifest.json"))))
                .isEqualTo(assembled.manifest());
        assertThat(Files.readString(outputDirectory.resolve("provenance.json")))
                .contains(operation.id())
                .contains("web-app")
                .contains("1.2.0");
        assertThat(InputLock.fromJson(Files.readString(outputDirectory.resolve("lock.json"))))
                .isEqualTo(assembled.lock());
    }

    @Test
    void writingTwiceLeavesNoTemporaryFilesAndOverwritesCleanly(@TempDir Path dir) throws Exception {
        ManifestOperation operation = ManifestOperation.of("GetBook", "query", "query GetBook { book { title } }");
        ClientRelease release = new ClientRelease("web-app", "1.2.0");
        AssembledManifest assembled = new AssembledManifest(
                OperationManifest.of(List.of(operation)),
                List.of(new OperationProvenance(operation.id(), List.of(release))),
                new InputLock("nightsky", "production", List.of(new LockedRelease(release, "abc123", 1))));

        Path outputDirectory = dir.resolve("out");
        assembled.writeTo(outputDirectory);
        assembled.writeTo(outputDirectory);

        try (var entries = Files.list(outputDirectory)) {
            assertThat(entries.map(p -> p.getFileName().toString()))
                    .containsExactlyInAnyOrder("manifest.json", "provenance.json", "lock.json");
        }
    }

    @Test
    void readFromIsTheInverseOfWriteTo(@TempDir Path dir) {
        ManifestOperation operation = ManifestOperation.of("GetBook", "query", "query GetBook { book { title } }");
        ClientRelease release = new ClientRelease("web-app", "1.2.0");
        AssembledManifest assembled = new AssembledManifest(
                OperationManifest.of(List.of(operation)),
                List.of(new OperationProvenance(operation.id(), List.of(release))),
                new InputLock("nightsky", "production", List.of(new LockedRelease(release, "abc123", 1))));

        Path outputDirectory = dir.resolve("out");
        assembled.writeTo(outputDirectory);

        assertThat(AssembledManifest.readFrom(outputDirectory)).isEqualTo(assembled);
    }

    @Test
    void readFromFailsWhenAFileIsMissing(@TempDir Path dir) {
        assertThatThrownBy(() -> AssembledManifest.readFrom(dir.resolve("does-not-exist")))
                .isInstanceOf(AssemblyException.class);
    }
}
