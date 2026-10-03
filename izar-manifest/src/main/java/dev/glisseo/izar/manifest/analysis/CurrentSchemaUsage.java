package dev.glisseo.izar.manifest.analysis;

/**
 * Provides the field usage computed for the currently active schema and registered releases.
 *
 * <p>The schema feature owns the implementation. This contract lives in izar-manifest so another
 * feature can consume it without depending on the application that hosts the schema feature, which
 * would create a module cycle.
 */
public interface CurrentSchemaUsage {
    /**
     * Returns the same usage index used by the schema feature's coverage and impact views, over
     * the releases {@code scope} selects. The result is empty when no schema has been published.
     */
    UsageIndex currentUsage(ReleaseScope scope);

    /** Usage over every registered release, {@link ReleaseScope#ALL}. */
    default UsageIndex currentUsage() {
        return currentUsage(ReleaseScope.ALL);
    }
}
