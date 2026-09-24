package dev.glisseo.izar.manifest.source;

import dev.glisseo.izar.manifest.ManifestOperation;
import dev.glisseo.izar.manifest.ManifestSnapshot;
import dev.glisseo.izar.manifest.ManifestSourceException;
import dev.glisseo.izar.manifest.InvalidManifestException;
import dev.glisseo.izar.manifest.OperationManifest;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class FileManifestSourceTest {

    private static final OperationManifest MANIFEST =
            OperationManifest.of(List.of(ManifestOperation.of("GetBook", "query", "query GetBook { book { title } }")));

    @Test
    void loadsTheManifestWrittenAtThatPath(@TempDir Path dir) throws IOException {
        Path file = dir.resolve("manifest.json");
        Files.writeString(file, MANIFEST.toJson());

        ManifestSnapshot snapshot = ManifestSources.file(file).load();

        assertThat(snapshot.manifest()).isEqualTo(MANIFEST);
    }

    @Test
    void twoLoadsOfUnchangedContentAgreeOnRevision(@TempDir Path dir) throws IOException {
        Path file = dir.resolve("manifest.json");
        Files.writeString(file, MANIFEST.toJson());
        LocalManifestSource source = ManifestSources.file(file);

        assertThat(source.load().revision()).isEqualTo(source.load().revision());
    }

    @Test
    void editingTheFileChangesTheRevision(@TempDir Path dir) throws IOException {
        Path file = dir.resolve("manifest.json");
        Files.writeString(file, MANIFEST.toJson());
        LocalManifestSource source = ManifestSources.file(file);
        String firstRevision = source.load().revision();

        OperationManifest edited = OperationManifest.of(List.of(
                ManifestOperation.of("GetBook", "query", "query GetBook { book { title } }"),
                ManifestOperation.of("GetAuthor", "query", "query GetAuthor { author { name } }")));
        Files.writeString(file, edited.toJson());

        assertThat(source.load().revision()).isNotEqualTo(firstRevision);
    }

    @Test
    void wrapsAMissingFileAsAManifestSourceException(@TempDir Path dir) {
        Path missing = dir.resolve("does-not-exist.json");

        assertThatThrownBy(() -> ManifestSources.file(missing).load())
                .isInstanceOf(ManifestSourceException.class)
                .hasMessageContaining(missing.toString());
    }

    @Test
    void letsAnInvalidManifestExceptionPropagateUnwrapped(@TempDir Path dir) throws IOException {
        Path file = dir.resolve("manifest.json");
        Files.writeString(file, "not valid json");

        assertThatThrownBy(() -> ManifestSources.file(file).load()).isInstanceOf(InvalidManifestException.class);
    }
}
