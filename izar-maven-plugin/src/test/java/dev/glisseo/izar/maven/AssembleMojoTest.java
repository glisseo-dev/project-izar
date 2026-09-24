package dev.glisseo.izar.maven;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.glisseo.izar.manifest.ManifestOperation;
import dev.glisseo.izar.manifest.OperationManifest;
import java.lang.reflect.Field;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import org.apache.maven.plugin.MojoExecutionException;
import org.eclipse.aether.DefaultRepositorySystemSession;
import org.eclipse.aether.RepositorySystem;
import org.eclipse.aether.artifact.Artifact;
import org.eclipse.aether.artifact.DefaultArtifact;
import org.eclipse.aether.deployment.DeployRequest;
import org.eclipse.aether.repository.RemoteRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Drives {@link AssembleMojo} against a real, local {@code file://} repository: fixture releases
 * are deployed into it first (exactly what a client build's {@link DeployMojo} run would leave
 * behind), then {@link AssembleMojo} resolves and assembles them, the same round trip issue 36's
 * external-consumer integration test exercises at the Maven-process level.
 */
class AssembleMojoTest {

    private static final OperationManifest CATALOG_MANIFEST = OperationManifest.of(
            List.of(ManifestOperation.of("GetBook", "query", "query GetBook { book { title } }")));
    private static final OperationManifest CHECKOUT_MANIFEST = OperationManifest.of(
            List.of(ManifestOperation.of("GetCart", "query", "query GetCart { cart { total } }")));

    @Test
    void resolvesEveryConfiguredReleaseAndAssemblesTheirUnion(@TempDir Path tempDir) throws Exception {
        RepositorySystem system = TestRepositorySystem.newRepositorySystem();
        DefaultRepositorySystemSession session =
                TestRepositorySystem.newSession(system, tempDir.resolve("local-repo"));
        Path remoteRepo = Files.createDirectory(tempDir.resolve("remote-repo"));
        deploy(system, session, remoteRepo, "com.example.manifests", "catalog", "2026.09", CATALOG_MANIFEST);
        deploy(system, session, remoteRepo, "com.example.manifests", "checkout", "2026.10", CHECKOUT_MANIFEST);

        Path outputDirectory = tempDir.resolve("assembled");
        AssembleMojo mojo = mojo(
                system, session, remoteRepo, outputDirectory, "storefront", "production", false,
                release("catalog", "2026.09", "com.example.manifests", "catalog", "2026.09"),
                release("checkout", "2026.10", "com.example.manifests", "checkout", "2026.10"));

        mojo.execute();

        String manifestJson = Files.readString(outputDirectory.resolve("manifest.json"));
        assertThat(manifestJson).contains("GetBook").contains("GetCart");
        String lockJson = Files.readString(outputDirectory.resolve("lock.json"));
        assertThat(lockJson).contains("storefront").contains("production").contains("catalog").contains("checkout");
        String provenanceJson = Files.readString(outputDirectory.resolve("provenance.json"));
        assertThat(provenanceJson).contains("catalog").contains("2026.09").contains("checkout").contains("2026.10");
    }

    @Test
    void failsNamingEveryUnresolvableReleaseInOneRun(@TempDir Path tempDir) throws Exception {
        RepositorySystem system = TestRepositorySystem.newRepositorySystem();
        DefaultRepositorySystemSession session =
                TestRepositorySystem.newSession(system, tempDir.resolve("local-repo"));
        Path remoteRepo = Files.createDirectory(tempDir.resolve("remote-repo"));
        // Neither release is ever deployed: both must be reported as unresolvable, not just the first.

        AssembleMojo mojo = mojo(
                system, session, remoteRepo, tempDir.resolve("assembled"), "storefront", "production", false,
                release("catalog", "2026.09", "com.example.manifests", "catalog", "2026.09"),
                release("checkout", "2026.10", "com.example.manifests", "checkout", "2026.10"));

        assertThatThrownBy(mojo::execute)
                .isInstanceOf(MojoExecutionException.class)
                .hasMessageContaining("catalog@2026.09")
                .hasMessageContaining("checkout@2026.10");
    }

    @Test
    void failsWhenTwoResolvedReleasesConflictOverOneOperation(@TempDir Path tempDir) throws Exception {
        RepositorySystem system = TestRepositorySystem.newRepositorySystem();
        DefaultRepositorySystemSession session =
                TestRepositorySystem.newSession(system, tempDir.resolve("local-repo"));
        Path remoteRepo = Files.createDirectory(tempDir.resolve("remote-repo"));
        // Same document body as CATALOG_MANIFEST's operation, so it hashes to the same operation
        // ID, but declared under a different name: exactly the "two releases disagree about what
        // one operation ID actually is" conflict ReleaseAssembler rejects.
        OperationManifest conflicting = OperationManifest.of(List.of(
                ManifestOperation.of("GetBookAlias", "query", "query GetBook { book { title } }")));
        deploy(system, session, remoteRepo, "com.example.manifests", "catalog", "2026.09", CATALOG_MANIFEST);
        deploy(system, session, remoteRepo, "com.example.manifests", "catalog-mirror", "2026.09", conflicting);

        AssembleMojo mojo = mojo(
                system, session, remoteRepo, tempDir.resolve("assembled"), "storefront", "production", false,
                release("catalog", "2026.09", "com.example.manifests", "catalog", "2026.09"),
                release("catalog-mirror", "2026.09", "com.example.manifests", "catalog-mirror", "2026.09"));

        assertThatThrownBy(mojo::execute)
                .isInstanceOf(MojoExecutionException.class)
                .hasMessageContaining("GetBook");
    }

    @Test
    void failsBeforeResolvingAnythingWhenAReleaseNamesAVersionRangeInsteadOfAnExactVersion(@TempDir Path tempDir)
            throws Exception {
        RepositorySystem system = TestRepositorySystem.newRepositorySystem();
        DefaultRepositorySystemSession session =
                TestRepositorySystem.newSession(system, tempDir.resolve("local-repo"));
        Path remoteRepo = Files.createDirectory(tempDir.resolve("remote-repo"));
        // Deployed so a passing resolution would be possible; the version range must still be
        // rejected before any repository call, never silently resolved to the newest match.
        deploy(system, session, remoteRepo, "com.example.manifests", "catalog", "2026.09", CATALOG_MANIFEST);

        AssembleMojo mojo = mojo(
                system, session, remoteRepo, tempDir.resolve("assembled"), "storefront", "production", false,
                release("catalog", "2026.09", "com.example.manifests", "catalog", "[2026.01,)"));

        assertThatThrownBy(mojo::execute)
                .isInstanceOf(MojoExecutionException.class)
                .hasMessageContaining("[2026.01,)")
                .hasMessageContaining("exact");
    }

    @Test
    void failsWhenAReleaseNamesTheLatestMetaVersion(@TempDir Path tempDir) throws Exception {
        RepositorySystem system = TestRepositorySystem.newRepositorySystem();
        DefaultRepositorySystemSession session =
                TestRepositorySystem.newSession(system, tempDir.resolve("local-repo"));
        Path remoteRepo = Files.createDirectory(tempDir.resolve("remote-repo"));

        AssembleMojo mojo = mojo(
                system, session, remoteRepo, tempDir.resolve("assembled"), "storefront", "production", false,
                release("catalog", "2026.09", "com.example.manifests", "catalog", "LATEST"));

        assertThatThrownBy(mojo::execute)
                .isInstanceOf(MojoExecutionException.class)
                .hasMessageContaining("LATEST");
    }

    @Test
    void failsWhenNoReleaseIsConfigured(@TempDir Path tempDir) throws Exception {
        RepositorySystem system = TestRepositorySystem.newRepositorySystem();
        DefaultRepositorySystemSession session =
                TestRepositorySystem.newSession(system, tempDir.resolve("local-repo"));
        Path remoteRepo = Files.createDirectory(tempDir.resolve("remote-repo"));

        AssembleMojo mojo = mojo(
                system, session, remoteRepo, tempDir.resolve("assembled"), "storefront", "production", false);

        assertThatThrownBy(mojo::execute).isInstanceOf(MojoExecutionException.class);
    }

    @Test
    void skipNeverResolvesOrAssemblesAnything(@TempDir Path tempDir) throws Exception {
        RepositorySystem system = TestRepositorySystem.newRepositorySystem();
        DefaultRepositorySystemSession session =
                TestRepositorySystem.newSession(system, tempDir.resolve("local-repo"));
        Path remoteRepo = Files.createDirectory(tempDir.resolve("remote-repo"));
        Path outputDirectory = tempDir.resolve("assembled");

        AssembleMojo mojo = mojo(
                system, session, remoteRepo, outputDirectory, "storefront", "production", true,
                release("catalog", "2026.09", "com.example.manifests", "catalog", "2026.09"));

        mojo.execute();

        assertThat(outputDirectory).doesNotExist();
    }

    private static void deploy(
            RepositorySystem system,
            DefaultRepositorySystemSession session,
            Path remoteRepo,
            String groupId,
            String artifactId,
            String version,
            OperationManifest manifest)
            throws Exception {
        Path manifestFile = Files.createTempFile(artifactId, ".json");
        Files.writeString(manifestFile, manifest.toJson());
        Artifact artifact = new DefaultArtifact(groupId, artifactId, "izar-manifest", "json", version)
                .setFile(manifestFile.toFile());
        RemoteRepository repository = TestRepositorySystem.fileRepository("test-repo", remoteRepo);
        system.deploy(session, new DeployRequest().addArtifact(artifact).setRepository(repository));
    }

    private static ReleaseArtifactParameter release(
            String clientName, String manifestVersion, String groupId, String artifactId, String version) {
        ReleaseArtifactParameter release = new ReleaseArtifactParameter();
        release.setClientName(clientName);
        release.setManifestVersion(manifestVersion);
        release.setGroupId(groupId);
        release.setArtifactId(artifactId);
        release.setVersion(version);
        return release;
    }

    private static AssembleMojo mojo(
            RepositorySystem system,
            DefaultRepositorySystemSession session,
            Path remoteRepo,
            Path outputDirectory,
            String graph,
            String environment,
            boolean skip,
            ReleaseArtifactParameter... releases)
            throws Exception {
        AssembleMojo mojo = new AssembleMojo();
        setField(mojo, "repositorySystem", system);
        setField(mojo, "repositorySystemSession", session);
        setField(mojo, "remoteRepositories", List.of(TestRepositorySystem.fileRepository("test-repo", remoteRepo)));
        setField(mojo, "graph", graph);
        setField(mojo, "environment", environment);
        setField(mojo, "releases", new ArrayList<>(List.of(releases)));
        setField(mojo, "outputDirectory", outputDirectory.toFile());
        setField(mojo, "skip", skip);
        return mojo;
    }

    private static void setField(Object target, String name, Object value) throws Exception {
        Field field = AssembleMojo.class.getDeclaredField(name);
        field.setAccessible(true);
        field.set(target, value);
    }
}
