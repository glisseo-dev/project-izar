package dev.glisseo.izar.gradle;

import static org.assertj.core.api.Assertions.assertThat;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import dev.glisseo.izar.manifest.ManifestOperation;
import dev.glisseo.izar.manifest.OperationManifest;
import dev.glisseo.izar.manifest.publication.PublishedRelease;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.Base64;
import java.util.List;
import org.gradle.testkit.runner.BuildResult;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import tools.jackson.databind.json.JsonMapper;

/** Drives {@code izarPublish} against a loopback controller stub. */
class PublishTaskTest {

    private static final JsonMapper JSON = JsonMapper.builder().build();
    private static final OperationManifest MANIFEST =
            OperationManifest.of(List.of(ManifestOperation.of("GetBook", "query", "query GetBook { book { title } }")));

    private HttpServer server;
    private String receivedPath;
    private String receivedAuthorization;
    private String receivedBody;

    @AfterEach
    void stop() {
        if (server != null) server.stop(0);
    }

    @Test
    void publishesTheManifestWithCredentialsFromGradleProperties(@TempDir Path dir) throws Exception {
        int port = serveController();
        TestBuild build = new TestBuild(dir).buildScript("""
                plugins { id 'dev.glisseo.izar' }
                izar {
                    publish {
                        url = 'http://localhost:%d/'
                        manifestFile = file('manifest.json')
                        clientName = 'catalog'
                        manifestVersion = '2026.09'
                        credentialsId = 'controller'
                    }
                }
                """.formatted(port)).file("manifest.json", MANIFEST.toJson());

        BuildResult result = build.run("izarPublish", "-PcontrollerUsername=publisher", "-PcontrollerPassword=secret");

        assertThat(receivedPath).isEqualTo("/api/releases");
        assertThat(receivedAuthorization).isEqualTo(
                "Basic " + Base64.getEncoder().encodeToString("publisher:secret".getBytes(StandardCharsets.UTF_8)));
        var sent = JSON.readTree(receivedBody);
        assertThat(sent.path("clientName").asString()).isEqualTo("catalog");
        assertThat(sent.path("manifestVersion").asString()).isEqualTo("2026.09");
        assertThat(result.getOutput()).contains("Published revision 1").contains("registration only");
    }

    @Test
    void publishesTheManifestIzarGenerateWritesWithoutAnExplicitManifestFile(@TempDir Path dir) throws Exception {
        int port = serveController();
        TestBuild build = new TestBuild(dir).withGraphql().buildScript("""
                plugins { id 'dev.glisseo.izar' }
                izar {
                    basePackage = 'generated.book'
                    publish {
                        url = 'http://localhost:%d'
                        clientName = 'catalog'
                        manifestVersion = '2026.09'
                    }
                }
                """.formatted(port));

        BuildResult result = build.run("izarPublish", "-Pizar.publish.credentialsId=c", "-PcUsername=u", "-PcPassword=p");

        assertThat(result.getOutput()).contains(":izarGenerate");
        assertThat(JSON.readTree(receivedBody).toString()).contains("GetBook");
    }

    @Test
    void optionsComeFromGradlePropertiesToo(@TempDir Path dir) throws Exception {
        int port = serveController();
        TestBuild build = new TestBuild(dir)
                .buildScript("plugins { id 'dev.glisseo.izar' }\n")
                .file("collected/manifest.json", MANIFEST.toJson());

        build.run(
                "izarPublish",
                "-Pizar.publish.url=http://localhost:" + port,
                "-Pizar.publish.clientName=catalog",
                "-Pizar.publish.manifestVersion=2026.09",
                "-Pizar.publish.manifestFile=collected/manifest.json",
                "-Pizar.publish.credentialsId=controller",
                "-PcontrollerUsername=publisher",
                "-PcontrollerPassword=secret");

        assertThat(receivedPath).isEqualTo("/api/releases");
    }

    @Test
    void failsNamingTheMissingManifestFile(@TempDir Path dir) {
        TestBuild build = new TestBuild(dir).buildScript("""
                plugins { id 'dev.glisseo.izar' }
                izar {
                    publish {
                        url = 'http://localhost:1'
                        manifestFile = file('manifest.json')
                        clientName = 'catalog'
                        manifestVersion = '2026.09'
                    }
                }
                """);

        BuildResult result = build.runAndFail("izarPublish");

        assertThat(result.getOutput()).contains("manifest.json");
    }

    @Test
    void failsWhenNoCredentialsAreConfigured(@TempDir Path dir) throws Exception {
        TestBuild build = new TestBuild(dir).buildScript("""
                plugins { id 'dev.glisseo.izar' }
                izar {
                    publish {
                        url = 'http://localhost:1'
                        manifestFile = file('manifest.json')
                        clientName = 'catalog'
                        manifestVersion = '2026.09'
                    }
                }
                """).file("manifest.json", MANIFEST.toJson());

        BuildResult result = build.runAndFail("izarPublish");

        assertThat(result.getOutput()).contains("No publication credentials configured");
    }

    @Test
    void skipDoesNotContactTheController(@TempDir Path dir) throws Exception {
        TestBuild build = new TestBuild(dir).buildScript("""
                plugins { id 'dev.glisseo.izar' }
                izar {
                    publish {
                        url = 'http://localhost:1'
                        manifestFile = file('manifest.json')
                        clientName = 'catalog'
                        manifestVersion = '2026.09'
                        skip = true
                    }
                }
                """);

        BuildResult result = build.run("izarPublish");

        assertThat(result.getOutput()).contains("Izar publication skipped.");
    }

    @Test
    void buildDoesNotPublish(@TempDir Path dir) throws Exception {
        TestBuild build = new TestBuild(dir).withGraphql().buildScript("""
                plugins {
                    id 'java'
                    id 'dev.glisseo.izar'
                }
                izar { basePackage = 'generated.book' }
                """);

        BuildResult result = build.run("build", "--dry-run");

        assertThat(result.getOutput()).doesNotContain("izarPublish");
        assertThat(Files.exists(dir.resolve("build"))).isFalse();
    }

    private int serveController() throws IOException {
        server = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
        server.createContext("/api/releases", exchange -> {
            receivedPath = exchange.getRequestURI().getPath();
            receivedAuthorization = exchange.getRequestHeaders().getFirst("Authorization");
            receivedBody = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
            respond(exchange, JSON.writeValueAsString(new PublishedRelease("catalog", "2026.09", 1, Instant.now(), "1")));
        });
        server.start();
        return server.getAddress().getPort();
    }

    private static void respond(HttpExchange exchange, String body) throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().add("Content-Type", "application/json");
        exchange.sendResponseHeaders(201, bytes.length);
        exchange.getResponseBody().write(bytes);
        exchange.close();
    }
}
