/**
 * Schema usage, change-impact, and candidate-schema analysis.
 *
 * <p>The analysis types consume manifest operations and release provenance, then return immutable
 * reports for the CLI and controller. They do not own transport or release storage.
 */
@org.jspecify.annotations.NullMarked
package dev.glisseo.izar.manifest.analysis;
