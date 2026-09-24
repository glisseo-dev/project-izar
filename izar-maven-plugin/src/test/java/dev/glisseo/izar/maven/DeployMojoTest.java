package dev.glisseo.izar.maven;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.glisseo.izar.manifest.ManifestOperation;
import dev.glisseo.izar.manifest.OperationManifest;
import java.lang.reflect.Field;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.apache.maven.plugin.MojoExecutionException;
import org.eclipse.aether.DefaultRepositorySystemSession;
import org.eclipse.aether.RepositorySystem;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Drives {@link DeployMojo} against a real, local {@code file://} repository built by {@link
 * TestRepositorySystem}, the same way {@code PublishMojoTest} drives {@link PublishMojo} against a
 * real embedded HTTP server: no mocked repository client stands in for Aether.
 */
class DeployMojoTest {

    private static final OperationManifest MANIFEST = OperationManifest.of(
            List.of(ManifestOperation.of("GetBook", "query", "query GetBook { book { title } }")));

    @Test
    void deploysTheManifestFileUnderTheConfiguredMavenCoordinates(@TempDir Path tempDir) throws Exception {
        Path manifestFile = tempDir.resolve("manifest.json");
        Files.writeString(manifestFile, MANIFEST.toJson());
        Path remoteRepo = Files.createDirectory(tempDir.resolve("remote-repo"));

        DeployMojo mojo = mojo(tempDir, manifestFile, "com.example.manifests", "catalog", "2026.09",
                "izar-manifest", "json", "test-repo", remoteRepo.toUri().toString(), false);

        mojo.execute();

        Path deployed = remoteRepo.resolve(
                "com/example/manifests/catalog/2026.09/catalog-2026.09-izar-manifest.json");
        assertThat(deployed).exists();
        assertThat(Files.readString(deployed)).contains("GetBook");
    }

    @Test
    void failsBeforeDeployingWhenTheVersionIsAMetaVersionInsteadOfAnExactVersion(@TempDir Path tempDir)
            throws Exception {
        Path manifestFile = tempDir.resolve("manifest.json");
        Files.writeString(manifestFile, MANIFEST.toJson());
        Path remoteRepo = Files.createDirectory(tempDir.resolve("remote-repo"));

        DeployMojo mojo = mojo(tempDir, manifestFile, "com.example.manifests", "catalog", "RELEASE",
                "izar-manifest", "json", "test-repo", remoteRepo.toUri().toString(), false);

        assertThatThrownBy(mojo::execute)
                .isInstanceOf(MojoExecutionException.class)
                .hasMessageContaining("RELEASE")
                .hasMessageContaining("exact");
        assertThat(remoteRepo.resolve("com/example/manifests/catalog")).doesNotExist();
    }

    @Test
    void failsNamingTheManifestFileWhenItIsMissing(@TempDir Path tempDir) throws Exception {
        Path missingManifest = tempDir.resolve("does-not-exist.json");
        Path remoteRepo = Files.createDirectory(tempDir.resolve("remote-repo"));

        DeployMojo mojo = mojo(tempDir, missingManifest, "com.example.manifests", "catalog", "2026.09",
                "izar-manifest", "json", "test-repo", remoteRepo.toUri().toString(), false);

        assertThatThrownBy(mojo::execute)
                .isInstanceOf(MojoExecutionException.class)
                .hasMessageContaining(missingManifest.toString());
    }

    @Test
    void failsNamingTheRepositoryWhenTheTargetCannotBeReached(@TempDir Path tempDir) throws Exception {
        Path manifestFile = tempDir.resolve("manifest.json");
        Files.writeString(manifestFile, MANIFEST.toJson());

        // Port 1 is a reserved, unlistened port: connecting to it fails immediately and
        // deterministically, without depending on an actual unreachable host on the network.
        DeployMojo mojo = mojo(tempDir, manifestFile, "com.example.manifests", "catalog", "2026.09",
                "izar-manifest", "json", "test-repo", "http://127.0.0.1:1/repo", false);

        assertThatThrownBy(mojo::execute)
                .isInstanceOf(MojoExecutionException.class)
                .hasMessageContaining("test-repo");
    }

    @Test
    void skipNeverReadsTheManifestOrDeploysAnything(@TempDir Path tempDir) throws Exception {
        // The manifest file does not exist and the repository directory does not exist either:
        // both would fail immediately if reached. Skip must short-circuit before either happens.
        Path missingManifest = tempDir.resolve("does-not-exist.json");
        Path missingRepo = tempDir.resolve("does-not-exist-repo");

        DeployMojo mojo = mojo(tempDir, missingManifest, "com.example.manifests", "catalog", "2026.09",
                "izar-manifest", "json", "test-repo", missingRepo.toUri().toString(), true);

        mojo.execute();

        assertThat(missingRepo).doesNotExist();
    }

    private static DeployMojo mojo(
            Path tempDir,
            Path manifestFile,
            String groupId,
            String artifactId,
            String version,
            String classifier,
            String extension,
            String repositoryId,
            String repositoryUrl,
            boolean skip)
            throws Exception {
        RepositorySystem system = TestRepositorySystem.newRepositorySystem();
        DefaultRepositorySystemSession session =
                TestRepositorySystem.newSession(system, tempDir.resolve("local-repo"));

        DeployMojo mojo = new DeployMojo();
        setField(mojo, "repositorySystem", system);
        setField(mojo, "repositorySystemSession", session);
        setField(mojo, "manifestFile", manifestFile.toFile());
        setField(mojo, "groupId", groupId);
        setField(mojo, "artifactId", artifactId);
        setField(mojo, "version", version);
        setField(mojo, "classifier", classifier);
        setField(mojo, "extension", extension);
        setField(mojo, "repositoryId", repositoryId);
        setField(mojo, "repositoryUrl", repositoryUrl);
        setField(mojo, "skip", skip);
        return mojo;
    }

    private static void setField(Object target, String name, Object value) throws Exception {
        Field field = DeployMojo.class.getDeclaredField(name);
        field.setAccessible(true);
        field.set(target, value);
    }
}
