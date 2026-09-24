package dev.glisseo.izar.manifest.assembly;

import dev.glisseo.izar.manifest.ManifestOperation;
import dev.glisseo.izar.manifest.OperationManifest;
import dev.glisseo.izar.manifest.source.ManifestSources;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ReleaseSelectionTest {

    @Test
    void rejectsAnEmptyReleaseList() {
        assertThatThrownBy(() -> new ReleaseSelection("nightsky", "production", List.of()))
                .isInstanceOf(AssemblyException.class)
                .hasMessageContaining("at least one");
    }

    @Test
    void rejectsABlankGraphId() {
        assertThatThrownBy(() -> new ReleaseSelection(
                        " ",
                        "production",
                        List.of(new SelectedRelease(
                                new ClientRelease("web-app", "1.0.0"), ManifestSources.file(Path.of("x"))))))
                .isInstanceOf(AssemblyException.class)
                .hasMessageContaining("graphId");
    }

    @Test
    void fromJsonResolvesManifestFilesRelativeToTheGivenBaseDirectory(@TempDir Path dir) throws IOException {
        ManifestOperation operation = ManifestOperation.of("GetBook", "query", "query GetBook { book { title } }");
        Files.createDirectories(dir.resolve("releases"));
        Files.writeString(
                dir.resolve("releases/web-app-1.2.0.json"), OperationManifest.of(List.of(operation)).toJson());

        String json =
                """
                {
                  "graph": "nightsky",
                  "environment": "production",
                  "releases": [
                    { "clientName": "web-app", "manifestVersion": "1.2.0", "manifestFile": "releases/web-app-1.2.0.json" }
                  ]
                }
                """;

        ReleaseSelection selection = ReleaseSelection.fromJson(json, dir);

        assertThat(selection.graphId()).isEqualTo("nightsky");
        assertThat(selection.environment()).isEqualTo("production");
        assertThat(selection.releases()).hasSize(1);
        assertThat(selection.releases().getFirst().release())
                .isEqualTo(new ClientRelease("web-app", "1.2.0"));
        assertThat(selection.releases().getFirst().source().load().manifest().operations())
                .containsExactly(operation);
    }

    @Test
    void fromJsonRejectsMalformedJson(@TempDir Path dir) {
        assertThatThrownBy(() -> ReleaseSelection.fromJson("not json", dir))
                .isInstanceOf(AssemblyException.class);
    }
}
