package dev.glisseo.izar.gradle;

import java.nio.file.Path;
import org.gradle.api.artifacts.ResolveException;

/** Turns a configured release into the local manifest file its coordinates name. */
@FunctionalInterface
public interface ReleaseResolver {

    /** @throws ResolveException if the coordinates cannot be resolved through the configured repositories */
    Path resolve(ReleaseEntry release);
}
