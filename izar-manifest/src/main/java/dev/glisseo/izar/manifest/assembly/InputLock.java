package dev.glisseo.izar.manifest.assembly;

import java.util.List;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.json.JsonMapper;

/**
 * The exact resolved inputs one {@link ReleaseAssembler#assemble} call used: which releases, at
 * which digests, each contributing how many operations.
 *
 * <p>Reassembling from an unchanged lock, against unchanged release content, reproduces an
 * identical {@link AssembledManifest#manifest()} and {@link AssembledManifest#provenance()}: a
 * reviewed result can be reproduced later without re-resolving newer releases. Deliberately carries
 * no timestamp or other execution evidence, since neither changes what content was assembled.
 *
 * @param graphId the {@link ReleaseSelection#graphId()} this lock resolves
 * @param environment the {@link ReleaseSelection#environment()} this lock resolves
 * @param releases every resolved release, in {@link ClientRelease#compareTo} order
 */
public record InputLock(String graphId, String environment, List<LockedRelease> releases) {

    private static final JsonMapper JSON = JsonMapper.builder().build();

    public InputLock {
        requireNonBlank(graphId, "graphId");
        requireNonBlank(environment, "environment");
        if (releases == null) {
            throw new AssemblyException("An input lock's releases must not be null.");
        }
        releases = List.copyOf(releases);
    }

    private static void requireNonBlank(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            throw new AssemblyException("An input lock's " + fieldName + " must not be blank.");
        }
    }

    /** This lock's {@code graphId} and {@code environment}, combined into one display identity. */
    public String identity() {
        return graphId + "/" + environment;
    }

    /** Renders this lock as pretty-printed JSON. */
    public String toJson() {
        try {
            return JSON.writerWithDefaultPrettyPrinter().writeValueAsString(this);
        } catch (JacksonException e) {
            throw new AssemblyException("Could not write input lock as JSON.", e);
        }
    }

    /**
     * Parses a lock from its JSON form.
     *
     * @throws AssemblyException if {@code json} is not a well-formed input lock
     */
    public static InputLock fromJson(String json) {
        try {
            return JSON.readValue(json, InputLock.class);
        } catch (JacksonException e) {
            throw new AssemblyException("Could not parse input lock JSON: " + e.getMessage(), e);
        }
    }
}
