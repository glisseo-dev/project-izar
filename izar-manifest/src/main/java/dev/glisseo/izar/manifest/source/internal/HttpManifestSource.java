package dev.glisseo.izar.manifest.source.internal;

import dev.glisseo.izar.manifest.InvalidManifestException;
import dev.glisseo.izar.manifest.ManifestSnapshot;
import dev.glisseo.izar.manifest.ManifestSourceException;
import dev.glisseo.izar.manifest.source.LocalManifestSource;
import dev.glisseo.izar.manifest.source.ManifestSource;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.json.JsonMapper;

/**
 * A {@link ManifestSource} that fetches the current candidate snapshot over HTTP, from a
 * controller's versioned snapshot endpoint or any static host serving the identical {@link
 * ManifestSnapshot} JSON body a file source would read from disk.
 *
 * <p>Reading a published snapshot needs no credentials: a controller's own publication endpoint is
 * the trusted boundary (only it can add to what a snapshot contains), and serving what it already
 * published back out is public by design. A deployment that wants authenticated or otherwise
 * customized reads supplies its own {@link HttpClient} and points {@code uri} at whatever that
 * client is configured to reach.
 *
 * <p>Publishing a manifest to a controller and a running server activating it are two separate
 * events: this source only performs the read half. A candidate this source returns still has to
 * pass schema validation and an atomic swap before any request is affected, exactly as {@link
 * LocalManifestSource} does, and different server replicas polling independently activate on their
 * own schedules rather than as one global transaction.
 */
public final class HttpManifestSource implements ManifestSource {

    private static final JsonMapper JSON = JsonMapper.builder().build();
    private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(10);

    private final HttpClient client;
    private final URI uri;

    public HttpManifestSource(URI uri) {
        this(HttpClient.newHttpClient(), uri);
    }

    public HttpManifestSource(HttpClient client, URI uri) {
        this.client = client;
        this.uri = uri;
    }

    @Override
    public ManifestSnapshot load() {
        HttpResponse<String> response;
        try {
            response = client.send(
                    HttpRequest.newBuilder(uri)
                            .timeout(REQUEST_TIMEOUT)
                            .header("Accept", "application/json")
                            .GET()
                            .build(),
                    HttpResponse.BodyHandlers.ofString());
        } catch (IOException e) {
            throw new ManifestSourceException("Could not reach manifest source '" + uri + "'.", e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new ManifestSourceException("Interrupted while reading manifest source '" + uri + "'.", e);
        }
        if (response.statusCode() != 200) {
            throw new ManifestSourceException(
                    "Manifest source '" + uri + "' responded with HTTP " + response.statusCode() + ".");
        }
        try {
            return JSON.readValue(response.body(), ManifestSnapshot.class);
        } catch (JacksonException e) {
            throw new InvalidManifestException(
                    "Could not parse manifest snapshot from '" + uri + "': " + e.getMessage(), e);
        }
    }
}
