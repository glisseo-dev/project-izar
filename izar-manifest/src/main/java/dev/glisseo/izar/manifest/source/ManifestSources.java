package dev.glisseo.izar.manifest.source;

import dev.glisseo.izar.manifest.source.internal.FileManifestSource;
import dev.glisseo.izar.manifest.source.internal.HttpManifestSource;
import java.net.URI;
import java.net.http.HttpClient;
import java.nio.file.Path;

/** Factories for the supported manifest source adapters. */
public final class ManifestSources {

    private ManifestSources() {}

    /** Creates a source that reads a manifest from {@code file}. */
    public static LocalManifestSource file(Path file) {
        return new FileManifestSource(file);
    }

    /** Creates a source that reads a manifest snapshot from {@code uri}. */
    public static ManifestSource http(URI uri) {
        return new HttpManifestSource(uri);
    }

    /** Creates an HTTP source using the supplied client. */
    public static ManifestSource http(HttpClient client, URI uri) {
        return new HttpManifestSource(client, uri);
    }
}
