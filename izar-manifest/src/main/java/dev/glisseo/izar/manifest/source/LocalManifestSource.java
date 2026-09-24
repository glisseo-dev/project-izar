package dev.glisseo.izar.manifest.source;

import java.nio.file.Path;

/** A manifest source backed by a local file whose exact bytes can be embedded in a bundle. */
public interface LocalManifestSource extends ManifestSource {

    /** Returns the file whose bytes are loaded and hashed by this source. */
    Path file();
}
