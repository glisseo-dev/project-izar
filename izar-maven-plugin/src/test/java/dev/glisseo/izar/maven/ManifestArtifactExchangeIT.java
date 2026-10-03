package dev.glisseo.izar.maven;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import dev.glisseo.izar.manifest.ManifestOperation;
import dev.glisseo.izar.manifest.OperationManifest;
import java.io.IOException;
import java.lang.reflect.Field;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import org.apache.maven.plugin.MojoExecutionException;
import org.eclipse.aether.DefaultRepositorySystemSession;
import org.eclipse.aether.RepositorySystem;
import org.eclipse.aether.repository.RemoteRepository;
import org.eclipse.aether.util.repository.AuthenticationBuilder;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Exercises the complete workflow end to end, from the outside, the way an actual client
 * build and an actual deployment build would: a fixture client release is deployed as a Maven
 * artifact through {@link DeployMojo} to a real (in-process, HTTP) Maven repository, then resolved
 * and assembled through {@link AssembleMojo} exactly as a separate deployment build would.
 *
 * <p>The repository requires HTTP Basic authentication, so this also exercises the credential path
 * authentication is supplied to the ambient {@link
 * org.eclipse.aether.RepositorySystemSession}/{@link RemoteRepository}, the same way a real Maven
 * run derives it from {@code settings.xml}, and neither {@link DeployMojo} nor {@link AssembleMojo}
 * ever sees, stores, or has any parameter capable of carrying a plaintext credential.
 */
class ManifestArtifactExchangeIT {

    private static final OperationManifest CATALOG_MANIFEST = OperationManifest.of(
            List.of(ManifestOperation.of("GetBook", "query", "query GetBook { book { title } }")));
    private static final OperationManifest CHECKOUT_MANIFEST = OperationManifest.of(
            List.of(ManifestOperation.of("GetCart", "query", "query GetCart { cart { total } }")));

    private HttpServer repository;

    @AfterEach
    void stopRepository() {
        if (repository != null) repository.stop(0);
    }

    @Test
    void publishesResolvesAndAssemblesFixtureReleasesThroughARealAuthenticatedRepository(@TempDir Path tempDir)
            throws Exception {
        Path storage = Files.createDirectory(tempDir.resolve("repository-storage"));
        int port = startAuthenticatedRepository(storage, "ci-publisher", "s3cret");
        String repositoryUrl = "http://localhost:" + port + "/releases";

        // The client build: two releases, each its own process in reality, sharing only the
        // repository. DeployMojo never touches izar-manifest's assembly seam at all.
        deploy(tempDir, "publisher-1", repositoryUrl, "com.example.manifests", "catalog", "2026.09",
                CATALOG_MANIFEST, "ci-publisher", "s3cret");
        deploy(tempDir, "publisher-2", repositoryUrl, "com.example.manifests", "checkout", "2026.10",
                CHECKOUT_MANIFEST, "ci-publisher", "s3cret");

        // The deployment build: a separate process, its own local repository, resolving both
        // releases by Maven coordinate and invoking the shared ReleaseAssembler.
        Path outputDirectory = tempDir.resolve("assembled");
        AssembleMojo assemble = assembleMojo(tempDir, "resolver", repositoryUrl, outputDirectory,
                "storefront", "production", "ci-publisher", "s3cret",
                release("catalog", "2026.09", "com.example.manifests", "catalog", "2026.09"),
                release("checkout", "2026.10", "com.example.manifests", "checkout", "2026.10"));

        assemble.execute();

        String manifestJson = Files.readString(outputDirectory.resolve("manifest.json"));
        assertThat(manifestJson).contains("GetBook").contains("GetCart");
        String lockJson = Files.readString(outputDirectory.resolve("lock.json"));
        assertThat(lockJson).contains("catalog").contains("checkout");
    }

    @Test
    void failsNamingTheMissingReleaseWhenOnlySomeReleasesWerePublished(@TempDir Path tempDir) throws Exception {
        Path storage = Files.createDirectory(tempDir.resolve("repository-storage"));
        int port = startAuthenticatedRepository(storage, "ci-publisher", "s3cret");
        String repositoryUrl = "http://localhost:" + port + "/releases";
        deploy(tempDir, "publisher-1", repositoryUrl, "com.example.manifests", "catalog", "2026.09",
                CATALOG_MANIFEST, "ci-publisher", "s3cret");
        // "checkout" is never published: assembly must fail naming it, not silently drop it.

        AssembleMojo assemble = assembleMojo(tempDir, "resolver", repositoryUrl, tempDir.resolve("assembled"),
                "storefront", "production", "ci-publisher", "s3cret",
                release("catalog", "2026.09", "com.example.manifests", "catalog", "2026.09"),
                release("checkout", "2026.10", "com.example.manifests", "checkout", "2026.10"));

        assertThatThrownBy(assemble::execute)
                .isInstanceOf(MojoExecutionException.class)
                .hasMessageContaining("checkout@2026.10");
    }

    @Test
    void deployFailsAndLeaksNoCredentialWhenTheRepositoryRejectsTheWrongPassword(@TempDir Path tempDir)
            throws Exception {
        Path storage = Files.createDirectory(tempDir.resolve("repository-storage"));
        int port = startAuthenticatedRepository(storage, "ci-publisher", "s3cret");
        String repositoryUrl = "http://localhost:" + port + "/releases";

        MojoExecutionException failure = org.junit.jupiter.api.Assertions.assertThrows(
                MojoExecutionException.class,
                () -> deploy(tempDir, "publisher-wrong-password", repositoryUrl, "com.example.manifests",
                        "catalog", "2026.09", CATALOG_MANIFEST, "ci-publisher", "wrong-password"));

        assertThat(failure.getMessage()).doesNotContain("wrong-password").doesNotContain("s3cret");
        assertThat(storage.resolve("com/example/manifests/catalog/2026.09")).doesNotExist();
    }

    @Test
    void assembleFailsAndLeaksNoCredentialWhenTheRepositoryRejectsMissingCredentials(@TempDir Path tempDir)
            throws Exception {
        Path storage = Files.createDirectory(tempDir.resolve("repository-storage"));
        int port = startAuthenticatedRepository(storage, "ci-publisher", "s3cret");
        String repositoryUrl = "http://localhost:" + port + "/releases";
        deploy(tempDir, "publisher-1", repositoryUrl, "com.example.manifests", "catalog", "2026.09",
                CATALOG_MANIFEST, "ci-publisher", "s3cret");

        AssembleMojo assemble = assembleMojo(tempDir, "resolver-no-auth", repositoryUrl,
                tempDir.resolve("assembled"), "storefront", "production", null, null,
                release("catalog", "2026.09", "com.example.manifests", "catalog", "2026.09"));

        MojoExecutionException failure =
                org.junit.jupiter.api.Assertions.assertThrows(MojoExecutionException.class, assemble::execute);

        assertThat(failure.getMessage()).doesNotContain("s3cret");
    }

    @Test
    void noMojoOrParameterBeanDeclaresAFieldCapableOfCarryingAPlaintextCredential() {
        for (Class<?> type : List.of(DeployMojo.class, AssembleMojo.class, ReleaseArtifactParameter.class)) {
            for (Field field : type.getDeclaredFields()) {
                String name = field.getName().toLowerCase(java.util.Locale.ROOT);
                assertThat(name)
                        .withFailMessage("%s declares a field named '%s', which could carry a plaintext credential",
                                type.getSimpleName(), field.getName())
                        .doesNotContain("password")
                        .doesNotContain("secret")
                        .doesNotContain("token");
            }
        }
    }

    private void deploy(
            Path tempDir,
            String localRepoName,
            String repositoryUrl,
            String groupId,
            String artifactId,
            String version,
            OperationManifest manifest,
            String username,
            String password)
            throws Exception {
        RepositorySystem system = TestRepositorySystem.newRepositorySystem();
        DefaultRepositorySystemSession session =
                TestRepositorySystem.newSession(system, tempDir.resolve(localRepoName));
        // Stands in for what a live Maven run already does: resolve a <server> entry matching
        // this repository's id into session-level authentication, before DeployMojo ever runs.
        // DeployMojo itself never sees username or password.
        session.setAuthenticationSelector(repo -> username == null
                ? null
                : new AuthenticationBuilder().addUsername(username).addPassword(password).build());
        Path manifestFile = tempDir.resolve(localRepoName + "-manifest.json");
        Files.writeString(manifestFile, manifest.toJson());

        DeployMojo mojo = new DeployMojo();
        setField(DeployMojo.class, mojo, "repositorySystem", system);
        setField(DeployMojo.class, mojo, "repositorySystemSession", session);
        setField(DeployMojo.class, mojo, "manifestFile", manifestFile.toFile());
        setField(DeployMojo.class, mojo, "groupId", groupId);
        setField(DeployMojo.class, mojo, "artifactId", artifactId);
        setField(DeployMojo.class, mojo, "version", version);
        setField(DeployMojo.class, mojo, "classifier", "izar-manifest");
        setField(DeployMojo.class, mojo, "extension", "json");
        setField(DeployMojo.class, mojo, "repositoryId", "fixture-repo");
        setField(DeployMojo.class, mojo, "repositoryUrl", repositoryUrl);
        setField(DeployMojo.class, mojo, "skip", false);
        mojo.execute();
    }

    private AssembleMojo assembleMojo(
            Path tempDir,
            String localRepoName,
            String repositoryUrl,
            Path outputDirectory,
            String graph,
            String environment,
            String username,
            String password,
            ReleaseArtifactParameter... releases)
            throws Exception {
        RepositorySystem system = TestRepositorySystem.newRepositorySystem();
        DefaultRepositorySystemSession session =
                TestRepositorySystem.newSession(system, tempDir.resolve(localRepoName));
        RemoteRepository.Builder builder = new RemoteRepository.Builder("fixture-repo", "default", repositoryUrl);
        if (username != null) {
            builder.setAuthentication(
                    new AuthenticationBuilder().addUsername(username).addPassword(password).build());
        }

        AssembleMojo mojo = new AssembleMojo();
        setField(AssembleMojo.class, mojo, "repositorySystem", system);
        setField(AssembleMojo.class, mojo, "repositorySystemSession", session);
        setField(AssembleMojo.class, mojo, "remoteRepositories", List.of(builder.build()));
        setField(AssembleMojo.class, mojo, "graph", graph);
        setField(AssembleMojo.class, mojo, "environment", environment);
        setField(AssembleMojo.class, mojo, "releases", new ArrayList<>(List.of(releases)));
        setField(AssembleMojo.class, mojo, "outputDirectory", outputDirectory.toFile());
        setField(AssembleMojo.class, mojo, "skip", false);
        return mojo;
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

    private static void setField(Class<?> type, Object target, String name, Object value) throws Exception {
        Field field = type.getDeclaredField(name);
        field.setAccessible(true);
        field.set(target, value);
    }

    /** A minimal file-backed Maven repository: GET/PUT under one directory, gated by HTTP Basic auth. */
    private int startAuthenticatedRepository(Path storage, String username, String password) throws IOException {
        String expectedAuth =
                "Basic " + Base64.getEncoder().encodeToString((username + ":" + password).getBytes(StandardCharsets.UTF_8));
        repository = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
        repository.createContext("/releases", exchange -> {
            try {
                handle(exchange, storage, expectedAuth);
            } finally {
                exchange.close();
            }
        });
        repository.start();
        return repository.getAddress().getPort();
    }

    private static void handle(HttpExchange exchange, Path storage, String expectedAuth) throws IOException {
        String authorization = exchange.getRequestHeaders().getFirst("Authorization");
        if (!expectedAuth.equals(authorization)) {
            exchange.getResponseHeaders().add("WWW-Authenticate", "Basic realm=\"fixture-repo\"");
            exchange.sendResponseHeaders(401, -1);
            return;
        }

        String path = exchange.getRequestURI().getPath().substring("/releases/".length());
        Path target = storage.resolve(path);
        switch (exchange.getRequestMethod()) {
            case "PUT" -> {
                Files.createDirectories(target.getParent());
                Files.write(target, exchange.getRequestBody().readAllBytes());
                exchange.sendResponseHeaders(201, -1);
            }
            case "GET", "HEAD" -> {
                if (!Files.isRegularFile(target)) {
                    exchange.sendResponseHeaders(404, -1);
                } else {
                    byte[] bytes = Files.readAllBytes(target);
                    exchange.sendResponseHeaders(200, bytes.length);
                    exchange.getResponseBody().write(bytes);
                }
            }
            default -> exchange.sendResponseHeaders(405, -1);
        }
    }
}
