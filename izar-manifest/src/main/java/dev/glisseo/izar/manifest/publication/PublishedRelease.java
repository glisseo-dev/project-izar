package dev.glisseo.izar.manifest.publication;

import java.time.Instant;

/**
 * A controller's acknowledgement of a {@link ManifestPublisher#publish} call: the release identity
 * it registered and the revision it now belongs to.
 *
 * <p>This is a registration receipt, not proof that any running server has activated {@code
 * revision} yet. A server replica only starts enforcing a revision after its own next refresh,
 * polling the controller independently of this call. Check a controller's {@code
 * GET /api/inventory} for the currently published revision and {@code GET /api/server-status} for
 * which replicas have loaded it.
 */
public record PublishedRelease(
        String clientName, String manifestVersion, int operationCount, Instant uploadedAt, String revision) {}
