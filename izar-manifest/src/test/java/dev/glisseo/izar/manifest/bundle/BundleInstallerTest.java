package dev.glisseo.izar.manifest.bundle;

import dev.glisseo.izar.manifest.ManifestOperation;
import dev.glisseo.izar.manifest.OperationManifest;
import dev.glisseo.izar.manifest.assembly.ClientRelease;
import dev.glisseo.izar.manifest.assembly.ReleaseSelection;
import dev.glisseo.izar.manifest.assembly.SelectedRelease;
import dev.glisseo.izar.manifest.source.ManifestSources;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class BundleInstallerTest {

    private static final ManifestOperation GET_BOOK =
            ManifestOperation.of("GetBook", "query", "query GetBook { book { title } }");
    private static final ManifestOperation GET_AUTHOR =
            ManifestOperation.of("GetAuthor", "query", "query GetAuthor { author { name } }");
    private static final ManifestOperation GET_REVIEW =
            ManifestOperation.of("GetReview", "query", "query GetReview { review { text } }");

    private static final ClientRelease WEB_APP = new ClientRelease("web-app", "1.2.0");

    @Test
    void installsAVerifiedBundleAndExposesItAsTheActiveRevision(@TempDir Path dir) throws IOException {
        Path bundleDirectory = writeBundle(dir, "bundle", GET_BOOK, GET_AUTHOR);
        Path installRoot = dir.resolve("install");

        InstalledBundle installed = new BundleInstaller().install(bundleDirectory, installRoot);

        assertThat(installed.installRoot()).isEqualTo(installRoot);
        assertThat(Files.exists(installed.manifestFile())).isTrue();
        assertThat(Files.exists(installed.provenanceFile())).isTrue();
        assertThat(Files.exists(installed.lockFile())).isTrue();
        assertThat(OperationManifest.fromJson(Files.readString(installed.manifestFile())).operations())
                .extracting(ManifestOperation::name)
                .containsExactlyInAnyOrder("GetBook", "GetAuthor");

        InstalledBundle active = new BundleInstaller().active(installRoot).orElseThrow();
        assertThat(active).isEqualTo(installed);
    }

    @Test
    void refusesToInstallAnUnverifiedBundleAndTouchesNothing(@TempDir Path dir) throws IOException {
        Path bundleDirectory = writeBundle(dir, "bundle", GET_BOOK);
        Files.delete(bundleDirectory.resolve("releases/web-app/1.2.0/manifest.json"));
        Path installRoot = dir.resolve("install");

        assertThatThrownBy(() -> new BundleInstaller().install(bundleDirectory, installRoot))
                .isInstanceOf(BundleException.class)
                .hasMessageContaining("unverified");
        assertThat(Files.exists(installRoot)).isFalse();
    }

    @Test
    void anInvalidReinstallNeverOverwritesTheAlreadyInstalledUnit(@TempDir Path dir) throws IOException {
        Path installRoot = dir.resolve("install");
        Path goodBundle = writeBundle(dir, "good-bundle", GET_BOOK, GET_AUTHOR);
        InstalledBundle firstInstall = new BundleInstaller().install(goodBundle, installRoot);
        String firstManifestContent = Files.readString(firstInstall.manifestFile());

        Path tamperedBundle = writeBundle(dir, "tampered-bundle", GET_REVIEW);
        Files.writeString(
                tamperedBundle.resolve("manifest.json"),
                OperationManifest.of(List.of(GET_BOOK, GET_REVIEW)).toJson(),
                StandardOpenOption.TRUNCATE_EXISTING);

        assertThatThrownBy(() -> new BundleInstaller().install(tamperedBundle, installRoot))
                .isInstanceOf(BundleException.class);

        InstalledBundle stillActive = new BundleInstaller().active(installRoot).orElseThrow();
        assertThat(stillActive).isEqualTo(firstInstall);
        assertThat(Files.readString(firstInstall.manifestFile())).isEqualTo(firstManifestContent);
    }

    @Test
    void installingASecondValidBundleActivatesItWithoutMutatingThePreviousRevision(@TempDir Path dir) throws IOException {
        Path installRoot = dir.resolve("install");
        Path firstBundle = writeBundle(dir, "first-bundle", GET_BOOK);
        InstalledBundle first = new BundleInstaller().install(firstBundle, installRoot);
        String firstManifestContent = Files.readString(first.manifestFile());

        Path secondBundle = writeBundle(dir, "second-bundle", GET_BOOK, GET_AUTHOR);
        InstalledBundle second = new BundleInstaller().install(secondBundle, installRoot);

        assertThat(second.revisionId()).isNotEqualTo(first.revisionId());
        assertThat(new BundleInstaller().active(installRoot).orElseThrow()).isEqualTo(second);
        // The first revision's own directory and content are untouched, not overwritten in place.
        assertThat(Files.exists(first.manifestFile())).isTrue();
        assertThat(Files.readString(first.manifestFile())).isEqualTo(firstManifestContent);
    }

    @Test
    void reinstallingTheSameBundleIsIdempotent(@TempDir Path dir) {
        Path installRoot = dir.resolve("install");
        Path bundleDirectory = writeBundle(dir, "bundle", GET_BOOK);

        InstalledBundle first = new BundleInstaller().install(bundleDirectory, installRoot);
        InstalledBundle second = new BundleInstaller().install(bundleDirectory, installRoot);

        assertThat(second.revisionId()).isEqualTo(first.revisionId());
    }

    @Test
    void activeIsEmptyWhenNothingHasBeenInstalled(@TempDir Path dir) {
        assertThat(new BundleInstaller().active(dir.resolve("install")).isEmpty()).isTrue();
    }

    @Test
    void stagingFailureLeavesAFreshInstallRootWithNoActiveRevision(@TempDir Path dir) throws IOException {
        Path bundleDirectory = writeBundle(dir, "bundle", GET_BOOK);
        Path installRoot = dir.resolve("install");
        // Block the "revisions" directory installation needs to create, forcing staging to fail
        // before anything about this install becomes visible.
        Files.createDirectories(installRoot);
        Files.writeString(installRoot.resolve("revisions"), "not a directory");

        assertThatThrownBy(() -> new BundleInstaller().install(bundleDirectory, installRoot)).isInstanceOf(BundleException.class);

        assertThat(new BundleInstaller().active(installRoot)).isEmpty();
    }

    private static Path writeBundle(Path dir, String name, ManifestOperation... operations) {
        Path releaseFile = dir.resolve(name + "-release.json");
        try {
            Files.writeString(releaseFile, OperationManifest.of(List.of(operations)).toJson());
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        ReleaseSelection selection = new ReleaseSelection(
                "nightsky", "production", List.of(new SelectedRelease(WEB_APP, ManifestSources.file(releaseFile))));
        Path bundleDirectory = dir.resolve(name);
        new BundleWriter().write(selection, bundleDirectory);
        return bundleDirectory;
    }
}
