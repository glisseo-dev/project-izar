package dev.glisseo.izar.maven;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import dev.glisseo.izar.manifest.ManifestOperation;
import dev.glisseo.izar.manifest.OperationManifest;
import dev.glisseo.izar.manifest.publication.PublishedRelease;
import java.io.IOException;
import java.lang.reflect.Field;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.Base64;
import java.util.List;
import java.util.function.UnaryOperator;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.settings.Server;
import org.apache.maven.settings.Settings;
import org.apache.maven.settings.building.SettingsProblem;
import org.apache.maven.settings.crypto.SettingsDecrypter;
import org.apache.maven.settings.crypto.SettingsDecryptionResult;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import tools.jackson.databind.json.JsonMapper;

/**
 * None of {@code controllerUrl}, {@code clientName}, {@code manifestVersion}, {@code
 * manifestFile}, {@code serverId}, or {@code skip} is ever overridden anywhere in the test suite:
 * {@code PublishFromDeveloperWorkflowTest} in izar-controller deliberately calls {@code
 * ManifestPublisher} directly, bypassing this Mojo's own parameter wiring entirely. These tests
 * drive {@link PublishMojo} itself to prove that wiring actually works.
 */
class PublishMojoTest {

    private static final JsonMapper JSON = JsonMapper.builder().build();
    private static final OperationManifest MANIFEST =
            OperationManifest.of(List.of(ManifestOperation.of("GetBook", "query", "query GetBook { book { title } }")));

    private HttpServer server;

    @AfterEach
    void stop() {
        if (server != null) server.stop(0);
    }

    @Test
    void publishesTheOverriddenManifestFileToTheOverriddenUrlClientNameAndVersion(@TempDir Path tempDir)
            throws Exception {
        Path manifestFile = tempDir.resolve("custom-manifest.json");
        Files.writeString(manifestFile, MANIFEST.toJson());
        String[] receivedPath = new String[1];
        String[] receivedBody = new String[1];
        int port = serve("/api/releases", exchange -> {
            receivedPath[0] = exchange.getRequestURI().getPath();
            receivedBody[0] = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
            respond(exchange, 201, JSON.writeValueAsString(
                    new PublishedRelease("catalog", "2026.09", 1, Instant.now(), "1")));
        });

        UnaryOperator<String> env = key -> switch (key) {
            case "IZAR_PUBLISH_USERNAME" -> "publisher";
            case "IZAR_PUBLISH_PASSWORD" -> "secret";
            default -> null;
        };
        PublishMojo mojo = mojo(manifestFile, "http://localhost:" + port, "catalog", "2026.09", null, false, env);

        mojo.execute();

        assertThat(receivedPath[0]).isEqualTo("/api/releases");
        var sent = JSON.readTree(receivedBody[0]);
        assertThat(sent.path("clientName").asString()).isEqualTo("catalog");
        assertThat(sent.path("manifestVersion").asString()).isEqualTo("2026.09");
    }

    @Test
    void resolvesCredentialsFromTheOverriddenServerIdWhenNoEnvironmentVariablesAreSet(@TempDir Path tempDir)
            throws Exception {
        Path manifestFile = tempDir.resolve("manifest.json");
        Files.writeString(manifestFile, MANIFEST.toJson());
        String[] authHeader = new String[1];
        int port = serve("/api/releases", exchange -> {
            authHeader[0] = exchange.getRequestHeaders().getFirst("Authorization");
            respond(exchange, 201, JSON.writeValueAsString(
                    new PublishedRelease("catalog", "2026.09", 1, Instant.now(), "1")));
        });
        Settings settings = new Settings();
        Server server = new Server();
        server.setId("izar-controller");
        server.setUsername("publisher");
        server.setPassword("secret");
        settings.addServer(server);

        PublishMojo mojo = mojo(manifestFile, "http://localhost:" + port, "catalog", "2026.09",
                "izar-controller", false, envWithNoCredentials());
        setField(mojo, "settings", settings);

        mojo.execute();

        assertThat(authHeader[0]).isEqualTo(
                "Basic " + Base64.getEncoder().encodeToString("publisher:secret".getBytes(StandardCharsets.UTF_8)));
    }

    @Test
    void failsNamingTheOverriddenManifestFileWhenItIsMissing(@TempDir Path tempDir) throws Exception {
        Path missingManifest = tempDir.resolve("does-not-exist.json");

        PublishMojo mojo = mojo(missingManifest, "http://localhost:1", "catalog", "2026.09", null, false,
                envWithNoCredentials());

        assertThatThrownBy(mojo::execute)
                .isInstanceOf(MojoExecutionException.class)
                .hasMessageContaining(missingManifest.toString());
    }

    @Test
    void skipNeverReadsTheManifestOrContactsTheController(@TempDir Path tempDir) throws Exception {
        // Both would fail immediately if reached: no manifest file exists at this path, and
        // nothing is listening on this port. Skip must short-circuit before either happens.
        Path missingManifest = tempDir.resolve("does-not-exist.json");

        PublishMojo mojo = mojo(missingManifest, "http://localhost:1", "catalog", "2026.09", null, true,
                envWithNoCredentials());

        mojo.execute();
    }

    private static UnaryOperator<String> envWithNoCredentials() {
        return key -> null;
    }

    private static PublishMojo mojo(
            Path manifestFile,
            String controllerUrl,
            String clientName,
            String manifestVersion,
            String serverId,
            boolean skip,
            UnaryOperator<String> environment)
            throws Exception {
        PublishMojo mojo = new PublishMojo();
        setField(mojo, "manifestFile", manifestFile.toFile());
        setField(mojo, "controllerUrl", controllerUrl);
        setField(mojo, "clientName", clientName);
        setField(mojo, "manifestVersion", manifestVersion);
        setField(mojo, "serverId", serverId);
        setField(mojo, "skip", skip);
        setField(mojo, "settings", new Settings());
        setField(mojo, "settingsDecrypter", identityDecrypter());
        setField(mojo, "environment", environment);
        return mojo;
    }

    private static SettingsDecrypter identityDecrypter() {
        return request -> fixedResult(
                request.getServers().isEmpty() ? null : request.getServers().get(0), List.of());
    }

    private static SettingsDecryptionResult fixedResult(Server server, List<SettingsProblem> problems) {
        return new SettingsDecryptionResult() {
            @Override
            public Server getServer() {
                return server;
            }

            @Override
            public List<Server> getServers() {
                return server == null ? List.of() : List.of(server);
            }

            @Override
            public org.apache.maven.settings.Proxy getProxy() {
                return null;
            }

            @Override
            public List<org.apache.maven.settings.Proxy> getProxies() {
                return List.of();
            }

            @Override
            public List<SettingsProblem> getProblems() {
                return problems;
            }
        };
    }

    private static void setField(Object target, String name, Object value) throws Exception {
        Field field = PublishMojo.class.getDeclaredField(name);
        field.setAccessible(true);
        field.set(target, value);
    }

    private int serve(String path, Handler handler) throws IOException {
        server = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
        server.createContext(path, exchange -> {
            try {
                handler.handle(exchange);
            } finally {
                exchange.close();
            }
        });
        server.start();
        return server.getAddress().getPort();
    }

    private static void respond(HttpExchange exchange, int status, String body) throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.sendResponseHeaders(status, bytes.length);
        exchange.getResponseBody().write(bytes);
    }

    @FunctionalInterface
    private interface Handler {
        void handle(HttpExchange exchange) throws IOException;
    }
}
