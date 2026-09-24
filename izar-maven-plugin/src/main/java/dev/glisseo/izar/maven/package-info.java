/**
 * The Maven lifecycle adapter over {@code izar-compiler} and {@code izar-manifest}.
 *
 * <p>This package holds only parameter binding, phase binding, source-root registration,
 * Maven-specific credential resolution ({@link dev.glisseo.izar.maven.PublishCredentials}), and
 * Maven-repository resolution ({@link dev.glisseo.izar.maven.DeployMojo}, {@link
 * dev.glisseo.izar.maven.AssembleMojo}). Generation lives in the compiler, publication and
 * assembly in {@code izar-manifest}'s {@code ManifestPublisher} and {@code ReleaseAssembler}, so
 * another build integration can reuse any of them without a Maven session.
 */
@NullMarked
package dev.glisseo.izar.maven;

import org.jspecify.annotations.NullMarked;
