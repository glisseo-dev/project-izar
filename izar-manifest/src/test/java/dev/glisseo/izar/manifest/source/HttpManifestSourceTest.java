package dev.glisseo.izar.manifest.source;

import dev.glisseo.izar.manifest.ManifestOperation;
import dev.glisseo.izar.manifest.ManifestSnapshot;
import dev.glisseo.izar.manifest.ManifestSourceException;
import dev.glisseo.izar.manifest.InvalidManifestException;
import dev.glisseo.izar.manifest.OperationManifest;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

class HttpManifestSourceTest {

    private static final JsonMapper JSON = JsonMapper.builder().build();
    private static final OperationManifest MANIFEST =
            OperationManifest.of(List.of(ManifestOperation.of("GetBook", "query", "query GetBook { book { title } }")));

    private HttpServer server;

    @AfterEach
    void stop() {
        if (server != null) server.stop(0);
    }

    @Test
    void loadsTheSnapshotServedAtThatUri() throws IOException {
        ManifestSnapshot snapshot = new ManifestSnapshot("7", MANIFEST);
        URI uri = serve("/snapshots/latest", 200, JSON.writeValueAsString(snapshot));

        ManifestSnapshot loaded = ManifestSources.http(uri).load();

        assertThat(loaded).isEqualTo(snapshot);
    }

    @Test
    void wrapsANonSuccessStatusAsAManifestSourceException() throws IOException {
        URI uri = serve("/snapshots/latest", 404, "not found");

        assertThatThrownBy(() -> ManifestSources.http(uri).load())
                .isInstanceOf(ManifestSourceException.class)
                .hasMessageContaining("404");
    }

    @Test
    void wrapsAnUnreachableHostAsAManifestSourceException() {
        URI uri = URI.create("http://localhost:1/snapshots/latest");

        assertThatThrownBy(() -> ManifestSources.http(uri).load()).isInstanceOf(ManifestSourceException.class);
    }

    @Test
    void letsAnInvalidManifestExceptionPropagateUnwrapped() throws IOException {
        URI uri = serve("/snapshots/latest", 200, "not valid json");

        assertThatThrownBy(() -> ManifestSources.http(uri).load()).isInstanceOf(InvalidManifestException.class);
    }

    private URI serve(String path, int status, String body) throws IOException {
        server = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
        server.createContext(path, exchange -> {
            byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(status, bytes.length);
            exchange.getResponseBody().write(bytes);
            exchange.close();
        });
        server.start();
        return URI.create("http://localhost:" + server.getAddress().getPort() + path);
    }
}
