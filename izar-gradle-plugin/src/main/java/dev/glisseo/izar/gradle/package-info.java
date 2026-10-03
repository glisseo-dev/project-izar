/**
 * The Gradle adapter over {@code izar-compiler} and {@code izar-manifest}.
 *
 * <p>This package holds only option binding, source-set registration, Gradle-specific credential
 * resolution ({@link dev.glisseo.izar.gradle.PublishCredentials}), and Gradle-repository access
 * ({@link dev.glisseo.izar.gradle.IzarAssembleTask} and the {@code izarDeploy} wiring in {@link
 * dev.glisseo.izar.gradle.IzarPlugin}). Generation lives in the compiler, publication and assembly
 * in {@code izar-manifest}'s {@code ManifestPublisher} and {@code ReleaseAssembler}, the same seams
 * {@code izar-maven-plugin} binds.
 */
@NullMarked
package dev.glisseo.izar.gradle;

import org.jspecify.annotations.NullMarked;
