package dev.glisseo.izar.gradle;

import javax.inject.Inject;
import org.gradle.api.Named;
import org.gradle.api.provider.Property;

/**
 * One entry of {@code izar { assemble { releases { ... } } }}. The entry name is the client name;
 * the properties pair its manifest version with the Maven coordinates that supply the manifest.
 * {@code classifier} and {@code extension} default the way {@code izarDeploy} does.
 */
public abstract class ReleaseSpec implements Named {

    private final String name;

    @Inject
    public ReleaseSpec(String name) {
        this.name = name;
        getClassifier().convention(IzarPlugin.DEFAULT_CLASSIFIER);
        getExtension().convention(IzarPlugin.DEFAULT_EXTENSION);
    }

    @Override
    public String getName() {
        return name;
    }

    public abstract Property<String> getManifestVersion();

    public abstract Property<String> getGroupId();

    public abstract Property<String> getArtifactId();

    public abstract Property<String> getVersion();

    public abstract Property<String> getClassifier();

    public abstract Property<String> getExtension();

    ReleaseEntry toEntry() {
        return new ReleaseEntry(
                name,
                getManifestVersion().get(),
                getGroupId().get(),
                getArtifactId().get(),
                getVersion().get(),
                getClassifier().get(),
                getExtension().get());
    }
}
