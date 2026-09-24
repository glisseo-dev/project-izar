/**
 * Manifest loading adapters and their public source contracts.
 *
 * <p>{@link dev.glisseo.izar.manifest.source.ManifestSource} supplies a versioned snapshot without
 * coupling callers to file or HTTP transport. Use {@link ManifestSources} to create an adapter;
 * concrete transport classes are implementation details. Each adapter parses and validates the
 * manifest before returning it.
 */
@org.jspecify.annotations.NullMarked
package dev.glisseo.izar.manifest.source;
