package dev.glisseo.izar.manifest.publication;

import dev.glisseo.izar.manifest.ManifestOperation;
import dev.glisseo.izar.manifest.OperationManifest;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

class ManifestPublisherTest {

    private static final JsonMapper JSON = JsonMapper.builder().build();
    private static final OperationManifest MANIFEST =
            OperationManifest.of(List.of(ManifestOperation.of("GetBook", "query", "query GetBook { book { title } }")));

    private HttpServer server;

    @AfterEach
    void stop() {
        if (server != null) server.stop(0);
    }

    @Test
    void returnsTheRegisteredReleaseOnSuccess() throws IOException {
        var release = new PublishedRelease("catalog", "1.0", 1, Instant.parse("2026-09-10T00:00:00Z"), "1");
        URI uri = serve("/api/releases", exchange -> {
            assertThat(exchange.getRequestMethod()).isEqualTo("POST");
            assertThat(exchange.getRequestHeaders().getFirst("Authorization"))
                    .isEqualTo("Basic " + Base64.getEncoder().encodeToString("publisher:secret".getBytes(StandardCharsets.UTF_8)));
            respond(exchange, 201, JSON.writeValueAsString(release));
        });

        PublishedRelease result =
                new ManifestPublisher().publish(uri, "publisher", "secret", "catalog", "1.0", MANIFEST);

        assertThat(result).isEqualTo(release);
    }

    @Test
    void sendsTheManifestEnvelopeTheControllerExpects() throws IOException {
        String[] receivedBody = new String[1];
        URI uri = serve("/api/releases", exchange -> {
            receivedBody[0] = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
            respond(exchange, 201, JSON.writeValueAsString(
                    new PublishedRelease("catalog", "1.0", 1, Instant.now(), "1")));
        });

        new ManifestPublisher().publish(uri, "publisher", "secret", "catalog", "1.0", MANIFEST);

        var sent = JSON.readTree(receivedBody[0]);
        assertThat(sent.path("clientName").asString()).isEqualTo("catalog");
        assertThat(sent.path("manifestVersion").asString()).isEqualTo("1.0");
        assertThat(sent.path("manifest").path("operations")).hasSize(1);
    }

    @Test
    void wrapsRejectedCredentialsAsAManifestPublicationException() throws IOException {
        URI uri = serve("/api/releases", exchange -> respond(exchange, 401, ""));

        assertThatThrownBy(() -> new ManifestPublisher().publish(uri, "publisher", "wrong", "catalog", "1.0", MANIFEST))
                .isInstanceOf(ManifestPublicationException.class)
                .hasMessageContaining("401");
    }

    @Test
    void includesTheControllersProblemDetailOnAConflict() throws IOException {
        URI uri = serve("/api/releases", exchange -> respond(
                exchange, 409, "{\"detail\":\"This client name and manifest version already identify a different release.\"}"));

        assertThatThrownBy(() -> new ManifestPublisher().publish(uri, "publisher", "secret", "catalog", "1.0", MANIFEST))
                .isInstanceOf(ManifestPublicationException.class)
                .hasMessageContaining("409")
                .hasMessageContaining("already identify a different release");
    }

    @Test
    void wrapsAStorageFailureAsAManifestPublicationException() throws IOException {
        URI uri = serve("/api/releases", exchange -> respond(exchange, 503, "{\"detail\":\"Controller storage is unavailable.\"}"));

        assertThatThrownBy(() -> new ManifestPublisher().publish(uri, "publisher", "secret", "catalog", "1.0", MANIFEST))
                .isInstanceOf(ManifestPublicationException.class)
                .hasMessageContaining("503");
    }

    @Test
    void wrapsAnUnreachableControllerAsAManifestPublicationException() {
        URI uri = URI.create("http://localhost:1/api/releases");

        assertThatThrownBy(() -> new ManifestPublisher().publish(uri, "publisher", "secret", "catalog", "1.0", MANIFEST))
                .isInstanceOf(ManifestPublicationException.class);
    }

    private URI serve(String path, Handler handler) throws IOException {
        server = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
        server.createContext(path, exchange -> {
            try {
                handler.handle(exchange);
            } finally {
                exchange.close();
            }
        });
        server.start();
        return URI.create("http://localhost:" + server.getAddress().getPort() + path);
    }

    private static void respond(HttpExchange exchange, int status, String body) {
        try {
            byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(status, bytes.length);
            exchange.getResponseBody().write(bytes);
        } catch (IOException e) {
            throw new java.io.UncheckedIOException(e);
        }
    }

    @FunctionalInterface
    private interface Handler {
        void handle(HttpExchange exchange) throws IOException;
    }
}
