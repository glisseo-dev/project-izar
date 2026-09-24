package dev.glisseo.izar.manifest.analysis;

/**
 * Provides the field usage computed for the currently active schema and registered releases.
 *
 * <p>The schema feature owns the implementation. This contract lives in izar-manifest so another
 * feature can consume it without depending on izar-controller and creating a reactor cycle.
 */
public interface CurrentSchemaUsage {
    /**
     * Returns the same usage index used by the schema feature's coverage and impact views. The
     * result is empty when no schema has been published.
     */
    UsageIndex currentUsage();
}
