/**
 * Apollo-compatible persisted-query manifests and the snapshots built from them.
 *
 * <p>Manifest bodies and SHA-256 operation IDs derive from the exact final executable document
 * the client sends, after the compiler adds any required type discriminators. Hashing the
 * authored source file separately would not describe what actually executes.
 * {@link dev.glisseo.izar.manifest.OperationManifestInput} is a separate reader for compiler input;
 * it retains externally supplied IDs and bodies without applying the regular reader's hash check.
 *
 * <p>Merging is append-only: identical entries deduplicate, and a conflicting identity or a
 * mismatched hash is rejected rather than overwriting a registered operation.
 *
 * <p>{@link dev.glisseo.izar.manifest.source.ManifestSource} is the seam that supplies versioned
 * snapshots independently of how they travel, so an HTTP controller, a static file, and a test
 * fixture feed the same validation and activation behavior. {@link
 * dev.glisseo.izar.manifest.publication.ManifestPublisher} is the write-side counterpart: it
 * sends a manifest to a controller's release endpoint, build-tool agnostic so a Maven goal, a
 * Gradle task, or a plain script can all call it directly.
 *
 * <p>{@link dev.glisseo.izar.manifest.assembly.ReleaseAssembler} unions several client releases,
 * selected explicitly through a {@link dev.glisseo.izar.manifest.assembly.ReleaseSelection}, into
 * one {@link dev.glisseo.izar.manifest.assembly.AssembledManifest}: a portable manifest plus the
 * provenance and input lock describing exactly what was assembled. This merge is append-only in the same
 * sense: identical entries deduplicate, and a conflicting identity or a mismatched hash is rejected
 * rather than silently overwritten. It never touches a controller's published registry; a smaller
 * selection is how a release engineer explicitly retires a release from the next assembled
 * whitelist.
 *
 * <p>{@link dev.glisseo.izar.manifest.analysis.UsageIndex} and {@link
 * dev.glisseo.izar.manifest.analysis.SchemaImpactAnalyzer} compute which releases' stored
 * operations use a schema's types, fields, and arguments, and the structural diff between two
 * schema versions cross-referenced against that usage. Both are shared, unmodified, by the
 * controller's coverage and change-impact reports and by {@link
 * dev.glisseo.izar.manifest.analysis.CandidateChecker}'s pre-deployment check of a candidate
 * schema against an assembled selection. The CLI and the controller use the same analysis
 * implementation.
 *
 * <p>{@link dev.glisseo.izar.manifest.bundle.BundleWriter} packages an assembled selection into a
 * self-contained {@link dev.glisseo.izar.manifest.bundle.TransferBundle}: the assembled manifest,
 * provenance, and lock, plus every contributing release's original manifest, so an isolated
 * environment can verify it with no other input. {@link
 * dev.glisseo.izar.manifest.bundle.BundleVerifier} does that offline, by reassembling the bundle's
 * own embedded originals through the same {@link
 * dev.glisseo.izar.manifest.assembly.ReleaseAssembler} and comparing the result against what the
 * bundle stored, instead of duplicating assembly's own logic a second time. {@link
 * dev.glisseo.izar.manifest.bundle.BundleInstaller} then makes a verified bundle available at a
 * local install root as one atomically published unit, so a reader never observes a mixture of
 * files from two different revisions.
 */
@NullMarked
package dev.glisseo.izar.manifest;

import org.jspecify.annotations.NullMarked;
