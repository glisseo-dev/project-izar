package dev.glisseo.izar.manifest.assembly;

/**
 * Thrown when a {@link ReleaseAssembler} cannot produce a complete, self-consistent result: a
 * missing or unreadable release, a reused client release identity with different content, or
 * conflicting operation entries across selected releases.
 *
 * <p>Assembly is all-or-nothing. Whenever this is thrown, no {@link AssembledManifest} exists for
 * the attempted selection; a caller never receives a partial union to inspect or repair.
 */
public final class AssemblyException extends RuntimeException {

    public AssemblyException(String message) {
        super(message);
    }

    public AssemblyException(String message, Throwable cause) {
        super(message, cause);
    }
}
