package dev.glisseo.izar.manifest.publication;

import dev.glisseo.izar.manifest.OperationManifest;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Base64;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.json.JsonMapper;

/**
 * Publishes a manifest to a controller's {@code POST /api/releases} endpoint, over HTTP Basic
 * authentication, for any caller that holds the manifest as a file or in memory: a Maven goal, a
 * Gradle task, or a plain CI script calling into this library directly. Build-tool agnostic, the
 * same way {@link dev.glisseo.izar.compiler} generation logic stays reusable across build
 * integrations.
 *
 * <p>This performs exactly the write half of publication. It does not poll, wait for, or otherwise
 * confirm that any running server has activated the revision it registers; see {@link
 * PublishedRelease}.
 */
public final class ManifestPublisher {

    private static final JsonMapper JSON = JsonMapper.builder().build();
    private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(30);

    private final HttpClient client;

    public ManifestPublisher() {
        this(HttpClient.newHttpClient());
    }

    public ManifestPublisher(HttpClient client) {
        this.client = client;
    }

    /**
     * Publishes {@code manifest} under {@code clientName} and {@code manifestVersion} to the
     * controller's release endpoint at {@code releasesUri} (its {@code /api/releases} path),
     * authenticating with HTTP Basic credentials for a publisher account.
     *
     * @throws ManifestPublicationException if the controller rejects the request (wrong
     *     credentials, a malformed or conflicting release) or cannot be reached at all
     */
    public PublishedRelease publish(
            URI releasesUri,
            String username,
            String password,
            String clientName,
            String manifestVersion,
            OperationManifest manifest) {
        String body = JSON.writeValueAsString(new PublicationRequest(clientName, manifestVersion, manifest));
        String credentials = Base64.getEncoder()
                .encodeToString((username + ":" + password).getBytes(StandardCharsets.UTF_8));

        HttpResponse<String> response;
        try {
            response = client.send(
                    HttpRequest.newBuilder(releasesUri)
                            .timeout(REQUEST_TIMEOUT)
                            .header("Content-Type", "application/json")
                            .header("Accept", "application/json")
                            .header("Authorization", "Basic " + credentials)
                            .POST(HttpRequest.BodyPublishers.ofString(body))
                            .build(),
                    HttpResponse.BodyHandlers.ofString());
        } catch (IOException e) {
            throw new ManifestPublicationException("Could not reach controller '" + releasesUri + "'.", e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new ManifestPublicationException("Interrupted while publishing to '" + releasesUri + "'.", e);
        }

        int status = response.statusCode();
        if (status == 201) {
            return readRelease(response.body());
        }
        String reason = switch (status) {
            case 401 -> "rejected the publisher credentials";
            case 400 -> "rejected the release as malformed";
            case 409 -> "rejected the release as conflicting";
            case 503 -> "reported a storage failure";
            default -> "responded unexpectedly";
        };
        throw new ManifestPublicationException(
                "Controller '" + releasesUri + "' " + reason + " (HTTP " + status + "): " + detailOf(response.body()));
    }

    private static PublishedRelease readRelease(String body) {
        try {
            return JSON.readValue(body, PublishedRelease.class);
        } catch (JacksonException e) {
            throw new ManifestPublicationException(
                    "Controller accepted the release but its response could not be parsed: " + e.getMessage(), e);
        }
    }

    /** Reads a {@code ProblemDetail}'s {@code detail} field, falling back to the raw body. */
    private static String detailOf(String body) {
        try {
            String detail = JSON.readTree(body).path("detail").asString("");
            return detail.isEmpty() ? body : detail;
        } catch (JacksonException e) {
            return body;
        }
    }

    private record PublicationRequest(String clientName, String manifestVersion, OperationManifest manifest) {}
}
