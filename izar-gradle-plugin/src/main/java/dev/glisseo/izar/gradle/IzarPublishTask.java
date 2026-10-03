package dev.glisseo.izar.gradle;

import dev.glisseo.izar.manifest.OperationManifest;
import dev.glisseo.izar.manifest.publication.ManifestPublicationException;
import dev.glisseo.izar.manifest.publication.ManifestPublisher;
import dev.glisseo.izar.manifest.publication.PublishedRelease;
import java.net.URI;
import javax.inject.Inject;
import org.gradle.api.DefaultTask;
import org.gradle.api.GradleException;
import org.gradle.api.file.RegularFileProperty;
import org.gradle.api.provider.Property;
import org.gradle.api.provider.ProviderFactory;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.InputFile;
import org.gradle.api.tasks.Optional;
import org.gradle.api.tasks.PathSensitive;
import org.gradle.api.tasks.PathSensitivity;
import org.gradle.api.tasks.TaskAction;
import org.gradle.work.DisableCachingByDefault;

/**
 * Publishes a manifest file to a controller's authenticated release endpoint: {@code gradle
 * izarPublish}.
 *
 * <p>No other task depends on it: publication is a deliberate release action against a chosen
 * controller URL, never a side effect of {@code build}. {@code manifestFile} accepts a manifest
 * {@link IzarGenerateTask} wrote or one assembled by an existing Apollo-compatible pipeline; this
 * task never regenerates one.
 *
 * <p>Holds no publication logic of its own: {@link PublishCredentials} resolves credentials and
 * {@link ManifestPublisher} performs the HTTP call.
 */
@DisableCachingByDefault(because = "Sends the manifest over HTTP, which is a side effect")
public abstract class IzarPublishTask extends DefaultTask {

    /** A manifest written by {@link IzarGenerateTask}, or collected by another Apollo-compatible pipeline. */
    @InputFile
    @PathSensitive(PathSensitivity.NONE)
    public abstract RegularFileProperty getManifestFile();

    /** The controller's base URL, for example {@code https://izar.example.internal}. */
    @Input
    public abstract Property<String> getUrl();

    @Input
    public abstract Property<String> getClientName();

    @Input
    public abstract Property<String> getManifestVersion();

    /**
     * Prefix of the Gradle properties {@code <prefix>Username} and {@code <prefix>Password} that hold
     * publisher credentials. The Gradle counterpart of the Maven plugin's {@code serverId}. The
     * {@code IZAR_PUBLISH_USERNAME} and {@code IZAR_PUBLISH_PASSWORD} environment variables take
     * precedence when both are set.
     */
    @Optional
    @Input
    public abstract Property<String> getCredentialsId();

    @Input
    public abstract Property<Boolean> getSkip();

    @Inject
    protected abstract ProviderFactory getProviders();

    public IzarPublishTask() {
        setGroup("izar");
        setDescription("Publishes the operation manifest to an Izar controller.");
        // A release is a side effect on a remote system, so it is never up to date.
        getOutputs().upToDateWhen(task -> false);
        SkipOption.apply(this, getSkip(), "izar.publish.skip", "Izar publication skipped.");
    }

    @TaskAction
    void publish() {
        OperationManifest manifest =
                ManifestFiles.read(getManifestFile().get().getAsFile().toPath(), "izar.publish.manifestFile");
        PublishCredentials.Credentials credentials = resolveCredentials();
        String controllerUrl = getUrl().get();
        URI releasesUri = URI.create(controllerUrl.replaceAll("/+$", "") + "/api/releases");

        PublishedRelease release;
        try {
            release = new ManifestPublisher()
                    .publish(
                            releasesUri,
                            credentials.username(),
                            credentials.password(),
                            getClientName().get(),
                            getManifestVersion().get(),
                            manifest);
        } catch (ManifestPublicationException e) {
            throw new GradleException(e.getMessage(), e);
        }

        getLogger().lifecycle("Published revision " + release.revision() + " with " + release.operationCount()
                + " operation(s) for " + release.clientName() + "@" + release.manifestVersion() + ".");
        getLogger().lifecycle("This confirms registration only, not server activation. Check GET " + controllerUrl
                + "/api/inventory for the published revision and GET " + controllerUrl
                + "/api/server-status for which servers have loaded it. Enforcement mode is local server "
                + "configuration the controller never sees; read the 'izar' extension on a GraphQL response "
                + "to see which policy evaluated it.");
    }

    private PublishCredentials.Credentials resolveCredentials() {
        try {
            return PublishCredentials.resolve(
                    System::getenv,
                    name -> getProviders().gradleProperty(name).getOrNull(),
                    getCredentialsId().getOrNull());
        } catch (IllegalStateException e) {
            throw new GradleException(e.getMessage(), e);
        }
    }
}
