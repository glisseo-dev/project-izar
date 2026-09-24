package dev.glisseo.izar.manifest.bundle;

import dev.glisseo.izar.manifest.ManifestOperation;
import dev.glisseo.izar.manifest.OperationManifest;
import dev.glisseo.izar.manifest.assembly.ClientRelease;
import dev.glisseo.izar.manifest.assembly.ReleaseSelection;
import dev.glisseo.izar.manifest.assembly.SelectedRelease;
import dev.glisseo.izar.manifest.source.ManifestSources;
import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class BundleVerifierTest {

    private static final ManifestOperation GET_BOOK =
            ManifestOperation.of("GetBook", "query", "query GetBook { book { title } }");
    private static final ManifestOperation GET_AUTHOR =
            ManifestOperation.of("GetAuthor", "query", "query GetAuthor { author { name } }");

    private static final ClientRelease WEB_APP = new ClientRelease("web-app", "1.2.0");
    private static final ClientRelease BATCH_JOB = new ClientRelease("batch-job", "2026.1");

    @Test
    void verifiesAFreshlyWrittenBundle(@TempDir Path dir) {
        Path bundleDirectory = writeBundle(dir);

        BundleVerificationReport report = new BundleVerifier().verify(bundleDirectory);

        assertThat(report.valid()).isTrue();
        assertThat(report.problems()).isEmpty();
        TransferBundle bundle = report.bundle().orElseThrow();
        assertThat(bundle.assembled().manifest().operations()).extracting(ManifestOperation::name)
                .containsExactlyInAnyOrder("GetBook", "GetAuthor");
        assertThat(bundle.releases()).extracting(BundledRelease::release).containsExactly(BATCH_JOB, WEB_APP);
    }

    @Test
    void verifiesAfterBeingCopiedToASeparateLocalLocation(@TempDir Path original, @TempDir Path mirrored) throws IOException {
        Path bundleDirectory = writeBundle(original);
        Path mirroredBundle = mirrored.resolve("mirrored-bundle");
        copyRecursively(bundleDirectory, mirroredBundle);

        BundleVerificationReport report = new BundleVerifier().verify(mirroredBundle);

        assertThat(report.valid()).isTrue();
        assertThat(report.bundle()).isPresent();
    }

    @Test
    void ignoresExtraFilesTheBundleDoesNotClaimToVerify(@TempDir Path dir) throws IOException {
        Path bundleDirectory = writeBundle(dir);
        Files.createDirectories(bundleDirectory.resolve("evidence"));
        Files.writeString(bundleDirectory.resolve("evidence/approval.txt"), "not verified by this build");

        BundleVerificationReport report = new BundleVerifier().verify(bundleDirectory);

        assertThat(report.valid()).isTrue();
    }

    @Test
    void failsWithAMissingBundleDescriptor(@TempDir Path dir) throws IOException {
        Path bundleDirectory = writeBundle(dir);
        Files.delete(bundleDirectory.resolve("bundle.json"));

        BundleVerificationReport report = new BundleVerifier().verify(bundleDirectory);

        assertThat(report.valid()).isFalse();
        assertThat(report.problems()).anyMatch(p -> p.contains("bundle.json"));
    }

    @Test
    void failsOnAnUnsupportedBundleFormat(@TempDir Path dir) throws IOException {
        Path bundleDirectory = writeBundle(dir);
        Files.writeString(
                bundleDirectory.resolve("bundle.json"),
                new BundleDescriptor("some-other-format", 1, "nightsky", "production").toJson());

        BundleVerificationReport report = new BundleVerifier().verify(bundleDirectory);

        assertThat(report.valid()).isFalse();
        assertThat(report.problems()).anyMatch(p -> p.contains("Unsupported bundle format"));
    }

    @Test
    void failsOnAnUnsupportedBundleVersionWithTheCorrectFormat(@TempDir Path dir) throws IOException {
        Path bundleDirectory = writeBundle(dir);
        Files.writeString(
                bundleDirectory.resolve("bundle.json"),
                new BundleDescriptor(BundleDescriptor.FORMAT, 99, "nightsky", "production").toJson());

        BundleVerificationReport report = new BundleVerifier().verify(bundleDirectory);

        assertThat(report.valid()).isFalse();
        assertThat(report.problems()).anyMatch(p -> p.contains("Unsupported bundle format") && p.contains("99"));
    }

    @Test
    void failsWhenTheDescriptorIdentityDoesNotMatchTheLock(@TempDir Path dir) throws IOException {
        Path bundleDirectory = writeBundle(dir);
        Files.writeString(
                bundleDirectory.resolve("bundle.json"),
                BundleDescriptor.of("nightsky", "staging").toJson());

        BundleVerificationReport report = new BundleVerifier().verify(bundleDirectory);

        assertThat(report.valid()).isFalse();
        assertThat(report.problems()).anyMatch(p -> p.contains("nightsky/staging") && p.contains("nightsky/production"));
    }

    @Test
    void failsWhenAnEmbeddedReleaseManifestIsMissing(@TempDir Path dir) throws IOException {
        Path bundleDirectory = writeBundle(dir);
        Files.delete(bundleDirectory.resolve("releases/batch-job/2026.1/manifest.json"));

        BundleVerificationReport report = new BundleVerifier().verify(bundleDirectory);

        assertThat(report.valid()).isFalse();
        assertThat(report.problems()).anyMatch(p -> p.contains("batch-job@2026.1") && p.contains("missing"));
    }

    @Test
    void failsWhenAnEmbeddedReleaseManifestIsTampered(@TempDir Path dir) throws IOException {
        Path bundleDirectory = writeBundle(dir);
        Path tampered = bundleDirectory.resolve("releases/web-app/1.2.0/manifest.json");
        Files.writeString(tampered, OperationManifest.of(List.of(GET_BOOK)).toJson(), StandardOpenOption.TRUNCATE_EXISTING);

        BundleVerificationReport report = new BundleVerifier().verify(bundleDirectory);

        assertThat(report.valid()).isFalse();
        assertThat(report.problems()).anyMatch(p -> p.contains("web-app@1.2.0") && p.contains("digest"));
    }

    @Test
    void failsWhenTheAssembledManifestIsTamperedIndependentlyOfTheEmbeddedOriginals(@TempDir Path dir) throws IOException {
        Path bundleDirectory = writeBundle(dir);
        // Tamper with the top-level assembled manifest.json without touching any embedded original
        // or the lock: the recomputed union from the (unchanged) originals will no longer match it.
        Files.writeString(
                bundleDirectory.resolve("manifest.json"),
                OperationManifest.of(List.of(GET_BOOK)).toJson(),
                StandardOpenOption.TRUNCATE_EXISTING);

        BundleVerificationReport report = new BundleVerifier().verify(bundleDirectory);

        assertThat(report.valid()).isFalse();
        assertThat(report.problems()).anyMatch(p -> p.contains("does not match"));
    }

    @Test
    void failsWhenTheSelectionDocumentIntroducesAConflictingIdentity(@TempDir Path dir) throws IOException {
        Path bundleDirectory = writeBundle(dir);
        Files.writeString(
                bundleDirectory.resolve("selection.json"),
                """
                {
                  "graph": "nightsky",
                  "environment": "production",
                  "releases": [
                    { "clientName": "web-app", "manifestVersion": "1.2.0", "manifestFile": "releases/web-app/1.2.0/manifest.json" },
                    { "clientName": "web-app", "manifestVersion": "1.2.0", "manifestFile": "releases/batch-job/2026.1/manifest.json" }
                  ]
                }
                """);

        BundleVerificationReport report = new BundleVerifier().verify(bundleDirectory);

        assertThat(report.valid()).isFalse();
        assertThat(report.problems()).anyMatch(p -> p.contains("reassemble consistently"));
    }

    private static Path writeBundle(Path dir) {
        ReleaseSelection selection = new ReleaseSelection(
                "nightsky",
                "production",
                List.of(selected(WEB_APP, dir, GET_BOOK, GET_AUTHOR), selected(BATCH_JOB, dir, GET_BOOK)));
        Path bundleDirectory = dir.resolve("bundle");
        new BundleWriter().write(selection, bundleDirectory);
        return bundleDirectory;
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

    private static void copyRecursively(Path source, Path target) throws IOException {
        try (var stream = Files.walk(source)) {
            for (Path path : (Iterable<Path>) stream::iterator) {
                Path destination = target.resolve(source.relativize(path));
                if (Files.isDirectory(path)) {
                    Files.createDirectories(destination);
                } else {
                    Files.createDirectories(destination.getParent());
                    Files.copy(path, destination);
                }
            }
        }
    }
}
