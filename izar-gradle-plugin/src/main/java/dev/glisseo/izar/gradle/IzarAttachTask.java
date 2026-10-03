package dev.glisseo.izar.gradle;

import org.gradle.api.DefaultTask;
import org.gradle.api.file.RegularFileProperty;
import org.gradle.api.provider.Property;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.InputFile;
import org.gradle.api.tasks.PathSensitive;
import org.gradle.api.tasks.PathSensitivity;
import org.gradle.api.tasks.TaskAction;
import org.gradle.work.DisableCachingByDefault;

/**
 * Validates a manifest and attaches it to every {@code maven-publish} publication of the project
 * under a classifier, so {@code publishToMavenLocal} and {@code publish} ship it with the other
 * project artifacts. {@link IzarPlugin} registers it as {@code izarAttach} once {@code izar {
 * attach { enabled = true } }} is set; register more instances to attach several manifests, each
 * with a distinct classifier.
 *
 * <p>The attached artifact uses the owning project's group, name, and version. This task never
 * resolves or writes to a repository. Use {@code izarDeploy} when a manifest needs independent
 * coordinates.
 */
@DisableCachingByDefault(because = "Registers a file as a publication artifact and produces no output of its own")
public abstract class IzarAttachTask extends DefaultTask {

    /** A manifest written by {@link IzarGenerateTask}, or collected by another Apollo-compatible pipeline. */
    @InputFile
    @PathSensitive(PathSensitivity.NONE)
    public abstract RegularFileProperty getManifestFile();

    @Input
    public abstract Property<String> getClassifier();

    @Input
    public abstract Property<String> getExtension();

    @Input
    public abstract Property<String> getCoordinates();

    @Input
    public abstract Property<Boolean> getSkip();

    public IzarAttachTask() {
        setGroup("izar");
        setDescription("Validates the operation manifest and attaches it to the project's Maven publications.");
        SkipOption.apply(this, getSkip(), "izar.attach.skip", "Izar manifest attachment skipped.");
    }

    @TaskAction
    void attach() {
        var manifest = ManifestFiles.read(getManifestFile().get().getAsFile().toPath(), "izar.attach.manifestFile");
        getLogger().lifecycle("Attached manifest artifact " + getCoordinates().get() + " with "
                + manifest.operations().size() + " operation(s).");
    }
}
