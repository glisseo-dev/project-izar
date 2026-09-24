package dev.glisseo.izar.manifest.source;

import dev.glisseo.izar.manifest.InvalidManifestException;
import dev.glisseo.izar.manifest.ManifestSnapshot;
import dev.glisseo.izar.manifest.ManifestSourceException;
/**
 * Supplies a candidate {@link ManifestSnapshot}, independently of how it travels.
 *
 * <p>A file source and an HTTP source both implement this contract so a server's schema
 * validation and registry activation behave identically regardless of where the candidate came
 * from. A source only loads and parses; it never validates against a schema, decides whether to
 * activate what it read, or remembers a previously loaded snapshot. Those are the caller's
 * responsibility, since only the caller knows the target schema and the currently active registry.
 */
public interface ManifestSource {

    /**
     * Loads the current candidate snapshot.
     *
     * @throws ManifestSourceException if the candidate could not be read at all
     * @throws InvalidManifestException if what was read is not a well-formed, self-consistent
     *     manifest
     */
    ManifestSnapshot load();
}
