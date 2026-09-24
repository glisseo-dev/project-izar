package dev.glisseo.izar.maven;

import dev.glisseo.izar.manifest.InvalidManifestException;
import dev.glisseo.izar.manifest.publication.ManifestPublicationException;
import dev.glisseo.izar.manifest.publication.ManifestPublisher;
import dev.glisseo.izar.manifest.OperationManifest;
import dev.glisseo.izar.manifest.publication.PublishedRelease;
import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.nio.file.Files;
import java.util.function.UnaryOperator;
import javax.inject.Inject;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.settings.Settings;
import org.apache.maven.settings.crypto.SettingsDecrypter;
import org.jspecify.annotations.Nullable;

/**
 * Publishes a manifest file to a controller's authenticated release endpoint, so a developer or a
 * CI pipeline can release operations without the dashboard: {@code mvn izar:publish}.
 *
 * <p>Not bound to a lifecycle phase: publication is a deliberate, explicit release action, run on
 * demand against a chosen controller URL, never as a side effect of {@code mvn verify} or {@code
 * mvn install}. {@code manifestFile} accepts either a manifest {@link GenerateMojo} wrote or one
 * assembled by an existing Apollo-compatible artifact-collection pipeline; this goal never
 * regenerates one itself.
 *
 * <p>Holds no publication logic of its own: {@link PublishCredentials} resolves credentials and
 * {@link ManifestPublisher} performs the HTTP call, both reusable outside Maven. This Mojo only
 * binds Maven-specific configuration (parameters, {@code settings.xml}) onto those two seams and
 * turns their failures into a failed build.
 */
@Mojo(name = "publish", threadSafe = true)
public class PublishMojo extends AbstractMojo {

    @Parameter(defaultValue = "${settings}", readonly = true, required = true)
    private Settings settings;

    @Inject
    private SettingsDecrypter settingsDecrypter;

    /** A manifest written by {@link GenerateMojo}, or collected by another Apollo-compatible pipeline. */
    @Parameter(
            property = "izar.publish.manifestFile",
            defaultValue = "${project.build.directory}/izar/manifest.json",
            required = true)
    private File manifestFile;

    /** The controller's base URL, for example {@code https://izar.example.internal}. */
    @Parameter(property = "izar.publish.url", required = true)
    private String controllerUrl;

    @Parameter(property = "izar.publish.clientName", required = true)
    private String clientName;

    @Parameter(property = "izar.publish.manifestVersion", required = true)
    private String manifestVersion;

    /**
     * Id of a {@code <server>} in {@code settings.xml} holding publisher credentials. An
     * alternative to the {@code IZAR_PUBLISH_USERNAME}/{@code IZAR_PUBLISH_PASSWORD} environment
     * variables, which take precedence when both are set.
     */
    @Parameter(property = "izar.publish.serverId")
    private @Nullable String serverId;

    @Parameter(property = "izar.publish.skip", defaultValue = "false")
    private boolean skip;

    /** Not a {@code @Parameter}: overridden only by tests, so credential resolution is testable
     * against a fixed map instead of the real, ambient process environment. */
    private UnaryOperator<String> environment = System::getenv;

    @Override
    public void execute() throws MojoExecutionException {
        if (skip) {
            getLog().info("Izar publication skipped.");
            return;
        }

        OperationManifest manifest = readManifest();
        var credentials = resolveCredentials();
        URI releasesUri = URI.create(controllerUrl.replaceAll("/+$", "") + "/api/releases");

        PublishedRelease release;
        try {
            release = new ManifestPublisher()
                    .publish(
                            releasesUri,
                            credentials.username(),
                            credentials.password(),
                            clientName,
                            manifestVersion,
                            manifest);
        } catch (ManifestPublicationException e) {
            throw new MojoExecutionException(e.getMessage(), e);
        }

        getLog().info("Published revision " + release.revision() + " with " + release.operationCount()
                + " operation(s) for " + release.clientName() + "@" + release.manifestVersion() + ".");
        getLog().info("This confirms registration only, not server activation. Check GET " + controllerUrl
                + "/api/inventory for the published revision and GET " + controllerUrl
                + "/api/server-status for which servers have loaded it. Enforcement mode is local server "
                + "configuration the controller never sees; read the 'izar' extension on a GraphQL response "
                + "to see which policy evaluated it.");
    }

    private OperationManifest readManifest() throws MojoExecutionException {
        if (!manifestFile.isFile()) {
            throw new MojoExecutionException("No manifest file at " + manifestFile
                    + ". Run izar:generate or point izar.publish.manifestFile at a collected manifest.");
        }
        String json;
        try {
            json = Files.readString(manifestFile.toPath());
        } catch (IOException e) {
            throw new MojoExecutionException("Could not read manifest file " + manifestFile, e);
        }
        try {
            return OperationManifest.fromJson(json);
        } catch (InvalidManifestException e) {
            throw new MojoExecutionException("Manifest file " + manifestFile + " is not a valid manifest: "
                    + e.getMessage(), e);
        }
    }

    private PublishCredentials.Credentials resolveCredentials() throws MojoExecutionException {
        try {
            return PublishCredentials.resolve(environment, settings, settingsDecrypter, serverId);
        } catch (IllegalStateException e) {
            throw new MojoExecutionException(e.getMessage(), e);
        }
    }
}
