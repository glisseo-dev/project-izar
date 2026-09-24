package dev.glisseo.izar.manifest.bundle;

import dev.glisseo.izar.manifest.ManifestOperation;
import dev.glisseo.izar.manifest.OperationManifest;
import dev.glisseo.izar.manifest.assembly.AssemblyException;
import dev.glisseo.izar.manifest.assembly.ClientRelease;
import dev.glisseo.izar.manifest.assembly.ReleaseSelection;
import dev.glisseo.izar.manifest.assembly.SelectedRelease;
import dev.glisseo.izar.manifest.source.ManifestSources;
import dev.glisseo.izar.manifest.source.ManifestSource;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class BundleWriterTest {

    private static final ManifestOperation GET_BOOK =
            ManifestOperation.of("GetBook", "query", "query GetBook { book { title } }");
    private static final ManifestOperation GET_AUTHOR =
            ManifestOperation.of("GetAuthor", "query", "query GetAuthor { author { name } }");

    private static final ClientRelease WEB_APP = new ClientRelease("web-app", "1.2.0");
    private static final ClientRelease BATCH_JOB = new ClientRelease("batch-job", "2026.1");

    @Test
    void writesADescriptorSelectionAndEmbeddedOriginalsAlongsideTheAssembledResult(@TempDir Path dir) throws IOException {
        ReleaseSelection selection = new ReleaseSelection(
                "nightsky",
                "production",
                List.of(selected(WEB_APP, dir, GET_BOOK, GET_AUTHOR), selected(BATCH_JOB, dir, GET_BOOK)));

        Path bundleDirectory = dir.resolve("bundle");
        TransferBundle bundle = new BundleWriter().write(selection, bundleDirectory);

        assertThat(bundle.descriptor().format()).isEqualTo(BundleDescriptor.FORMAT);
        assertThat(bundle.descriptor().identity()).isEqualTo("nightsky/production");
        assertThat(bundle.releases()).extracting(BundledRelease::release).containsExactly(BATCH_JOB, WEB_APP);

        assertThat(Files.readString(bundleDirectory.resolve("bundle.json"))).contains("izar-transfer-bundle");
        assertThat(OperationManifest.fromJson(Files.readString(bundleDirectory.resolve("manifest.json"))).operations())
                .extracting(ManifestOperation::name)
                .containsExactlyInAnyOrder("GetBook", "GetAuthor");
        assertThat(Files.exists(bundleDirectory.resolve("provenance.json"))).isTrue();
        assertThat(Files.exists(bundleDirectory.resolve("lock.json"))).isTrue();
        assertThat(Files.exists(bundleDirectory.resolve("selection.json"))).isTrue();
        assertThat(OperationManifest.fromJson(
                        Files.readString(bundleDirectory.resolve("releases/web-app/1.2.0/manifest.json")))
                        .operations())
                .extracting(ManifestOperation::name)
                .containsExactlyInAnyOrder("GetBook", "GetAuthor");
        assertThat(OperationManifest.fromJson(
                        Files.readString(bundleDirectory.resolve("releases/batch-job/2026.1/manifest.json")))
                        .operations())
                .extracting(ManifestOperation::name)
                .containsExactly("GetBook");
    }

    @Test
    void failsWhenAReleaseDoesNotUseALocalFileSource(@TempDir Path dir) {
        SelectedRelease httpBacked = new SelectedRelease(WEB_APP, ManifestSources.http(URI.create("https://example.invalid/manifest")));
        ReleaseSelection selection = new ReleaseSelection("nightsky", "production", List.of(httpBacked));

        assertThatThrownBy(() -> new BundleWriter().write(selection, dir.resolve("bundle")))
                .isInstanceOf(BundleException.class)
                .hasMessageContaining("web-app@1.2.0")
                .hasMessageContaining("local file source");
    }

    @Test
    void failsAssemblyBeforeBundlingWhenTheSelectionItselfIsInvalid(@TempDir Path dir) {
        ManifestSource missing = ManifestSources.file(dir.resolve("does-not-exist.json"));
        ReleaseSelection selection = new ReleaseSelection("nightsky", "production", List.of(new SelectedRelease(WEB_APP, missing)));

        assertThatThrownBy(() -> new BundleWriter().write(selection, dir.resolve("bundle")))
                .isInstanceOf(AssemblyException.class);
        assertThat(Files.exists(dir.resolve("bundle"))).isFalse();
    }

    private static SelectedRelease selected(ClientRelease release, Path dir, ManifestOperation... operations) {
        Path file = dir.resolve(release.clientName() + "-" + release.manifestVersion() + ".json");
        try {
            Files.writeString(file, OperationManifest.of(List.of(operations)).toJson());
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        return new SelectedRelease(release, ManifestSources.file(file));
    }
}
