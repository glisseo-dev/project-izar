package dev.glisseo.izar.gradle;

import dev.glisseo.izar.manifest.assembly.AssembledManifest;
import dev.glisseo.izar.manifest.assembly.AssemblyException;
import dev.glisseo.izar.manifest.assembly.ClientRelease;
import dev.glisseo.izar.manifest.assembly.ExactVersion;
import dev.glisseo.izar.manifest.assembly.ReleaseAssembler;
import dev.glisseo.izar.manifest.assembly.ReleaseSelection;
import dev.glisseo.izar.manifest.assembly.SelectedRelease;
import dev.glisseo.izar.manifest.source.ManifestSources;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import org.gradle.api.DefaultTask;
import org.gradle.api.GradleException;
import org.gradle.api.artifacts.ResolveException;
import org.gradle.api.file.DirectoryProperty;
import org.gradle.api.provider.ListProperty;
import org.gradle.api.provider.Property;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.Internal;
import org.gradle.api.tasks.OutputDirectory;
import org.gradle.api.tasks.TaskAction;
import org.gradle.work.DisableCachingByDefault;

/**
 * Resolves exact client releases through the project's configured repositories and mirrors, then
 * assembles them into one pinned, union manifest with provenance and an input lock: {@code gradle
 * izarAssemble}. {@code izarDeploy} is the client-build half that publishes what this task later
 * resolves.
 *
 * <p>Holds no repository or assembly logic of its own. A {@link ReleaseResolver} turns each
 * configured {@link ReleaseEntry} into a local file, then this task builds a {@link
 * ReleaseSelection} exactly the way a purely local selection
 * is built and hands it to the same {@link ReleaseAssembler}.
 *
 * <p>Every configured release is resolved even after one fails, so a broken configuration reports
 * every missing coordinate in one run. Resolution completes in full, or nothing is assembled. Every
 * version must be exact, never a range, a dynamic version like {@code 1.+}, or a meta-version like
 * {@code LATEST}.
 *
 * <p>Declares no credential option. Resolution goes through the project's own {@code repositories
 * { ... }}, so repository credentials and mirrors come from ordinary Gradle configuration.
 */
@DisableCachingByDefault(because = "Resolves releases from Maven repositories, so the result depends on external state")
public abstract class IzarAssembleTask extends DefaultTask {

    /** The logical graph or enforcing endpoint this selection targets. */
    @Input
    public abstract Property<String> getGraph();

    /** The environment this selection applies to, for example {@code "production"}. */
    @Input
    public abstract Property<String> getEnvironment();

    /** Every client release this selection explicitly supports; never empty. */
    @Input
    public abstract ListProperty<ReleaseEntry> getReleases();

    @OutputDirectory
    public abstract DirectoryProperty getOutputDirectory();

    @Input
    public abstract Property<Boolean> getSkip();

    /** Supplied by {@link IzarPlugin}; resolves through the project's repositories. */
    @Internal
    public abstract Property<ReleaseResolver> getResolver();

    public IzarAssembleTask() {
        setGroup("izar");
        setDescription("Resolves exact client releases and assembles them into one union manifest.");
        // The resolved manifest content is not a declared input, so a remote republish must not go unnoticed.
        getOutputs().upToDateWhen(task -> false);
        notCompatibleWithConfigurationCache("it resolves release coordinates through the live project");
        SkipOption.apply(this, getSkip(), "izar.assemble.skip", "Izar assembly skipped.");
    }

    @TaskAction
    void assemble() {
        List<ReleaseEntry> releases = getReleases().get();
        if (releases.isEmpty()) {
            throw new GradleException("The izarAssemble task needs at least one release naming a client release "
                    + "and its Maven coordinates.");
        }
        for (ReleaseEntry release : releases) {
            try {
                ExactVersion.require(
                        release.version(),
                        "Release '" + release.clientName() + "@" + release.manifestVersion() + "'s version");
                rejectDynamicVersion(release);
            } catch (IllegalArgumentException e) {
                throw new GradleException(e.getMessage(), e);
            }
        }

        ReleaseResolver resolver = getResolver().get();
        List<SelectedRelease> selected = new ArrayList<>();
        List<String> errors = new ArrayList<>();
        for (ReleaseEntry release : releases) {
            try {
                selected.add(new SelectedRelease(
                        new ClientRelease(release.clientName(), release.manifestVersion()),
                        ManifestSources.file(resolver.resolve(release))));
            } catch (ResolveException e) {
                errors.add("Release '" + release.clientName() + "@" + release.manifestVersion() + "' ("
                        + release.coordinates() + "): " + rootMessage(e));
            }
        }
        if (!errors.isEmpty()) {
            throw new GradleException(
                    "Could not resolve every configured release through the configured repositories:\n"
                            + String.join("\n", errors));
        }

        try {
            AssembledManifest assembled = new ReleaseAssembler()
                    .assemble(new ReleaseSelection(getGraph().get(), getEnvironment().get(), selected));
            assembled.writeTo(getOutputDirectory().get().getAsFile().toPath());
            getLogger().lifecycle("Assembled " + assembled.manifest().operations().size() + " operation(s) from "
                    + assembled.lock().releases().size() + " release(s) into " + getOutputDirectory().get() + ".");
        } catch (AssemblyException e) {
            throw new GradleException(e.getMessage(), e);
        }
    }

    /** Gradle resolves {@code 1.+} and {@code latest.release} the way Maven resolves a range. */
    private static void rejectDynamicVersion(ReleaseEntry release) {
        String version = release.version();
        if (version.contains("+") || version.toLowerCase(Locale.ROOT).startsWith("latest.")) {
            throw new IllegalArgumentException("Release '" + release.clientName() + "@" + release.manifestVersion()
                    + "'s version '" + version + "' is not an exact version. A client release's manifest version "
                    + "must be pinned exactly, never a dynamic version like 1.+ or latest.release, so a resolved "
                    + "release can never be silently replaced by a newer one.");
        }
    }

    private static String rootMessage(Throwable e) {
        Throwable root = e;
        while (root.getCause() != null) {
            root = root.getCause();
        }
        return root.getMessage() != null ? root.getMessage() : e.getMessage();
    }
}
