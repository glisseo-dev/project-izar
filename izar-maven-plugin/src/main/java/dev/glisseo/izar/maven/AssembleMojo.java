package dev.glisseo.izar.maven;

import dev.glisseo.izar.manifest.assembly.AssembledManifest;
import dev.glisseo.izar.manifest.assembly.AssemblyException;
import dev.glisseo.izar.manifest.assembly.ClientRelease;
import dev.glisseo.izar.manifest.assembly.ReleaseAssembler;
import dev.glisseo.izar.manifest.assembly.ReleaseSelection;
import dev.glisseo.izar.manifest.assembly.SelectedRelease;
import dev.glisseo.izar.manifest.source.ManifestSources;
import java.io.File;
import java.util.ArrayList;
import java.util.List;
import javax.inject.Inject;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.eclipse.aether.RepositorySystem;
import org.eclipse.aether.RepositorySystemSession;
import org.eclipse.aether.artifact.DefaultArtifact;
import org.eclipse.aether.repository.RemoteRepository;
import org.eclipse.aether.resolution.ArtifactRequest;
import org.eclipse.aether.resolution.ArtifactResolutionException;
import org.eclipse.aether.resolution.ArtifactResult;

/**
 * Resolves exact client releases through configured Maven repositories and company mirrors, then
 * assembles them into one pinned, union manifest with provenance and an input lock: {@code mvn
 * izar:assemble}. This is the deployment-build half of issue 36; {@link DeployMojo} is the
 * client-build half that publishes what this goal later resolves.
 *
 * <p>Holds no repository or assembly logic of its own. Every configured {@link
 * ReleaseArtifactParameter} names Maven coordinates this goal resolves through the ambient {@link
 * RepositorySystem} and {@link RepositorySystemSession}, the same repositories and mirrors any
 * other dependency in this build resolves through. Once every release is resolved to a local file,
 * this goal builds a {@link ReleaseSelection} exactly the way {@code izar-cli}'s {@code
 * AssembleCommand} does for a purely local selection, and hands it to the same {@link
 * ReleaseAssembler}. Repository access and local assembly stay separate seams; this Mojo only
 * bridges them, per ADR 0027 and the ADR behind this goal.
 *
 * <p>Every configured release is resolved even after one fails, so a broken configuration reports
 * every missing or unresolvable coordinate in one run. Resolution completes in full, or nothing is
 * assembled at all: a partially resolved selection is never handed to {@link ReleaseAssembler}. Every
 * configured version must be exact, never a range or a meta-version like {@code LATEST}; see {@link
 * ExactMavenVersion}.
 *
 * <p>Declares no credential parameter. Resolution reuses {@code ${project.remoteProjectRepositories}}
 * by default, which already reflects this build's {@code settings.xml} mirrors, proxies, and
 * repository credentials; a deployment build points at the repositories that hold published
 * manifests the ordinary way, through its own {@code <repositories>} or an inherited settings
 * mirror, not through a plugin-specific repository list.
 */
@Mojo(name = "assemble", threadSafe = true)
public class AssembleMojo extends AbstractMojo {

    @Parameter(defaultValue = "${repositorySystemSession}", readonly = true, required = true)
    private RepositorySystemSession repositorySystemSession;

    @Parameter(defaultValue = "${project.remoteProjectRepositories}", readonly = true, required = true)
    private List<RemoteRepository> remoteRepositories;

    @Inject
    private RepositorySystem repositorySystem;

    /** The logical graph or enforcing endpoint this selection targets. */
    @Parameter(property = "izar.assemble.graph", required = true)
    private String graph;

    /** The environment this selection applies to, for example {@code "production"}. */
    @Parameter(property = "izar.assemble.environment", required = true)
    private String environment;

    /** Every client release this selection explicitly supports; never empty. */
    @Parameter(required = true)
    private List<ReleaseArtifactParameter> releases = new ArrayList<>();

    @Parameter(
            property = "izar.assemble.outputDirectory",
            defaultValue = "${project.build.directory}/izar/assembled",
            required = true)
    private File outputDirectory;

    @Parameter(property = "izar.assemble.skip", defaultValue = "false")
    private boolean skip;

    @Override
    public void execute() throws MojoExecutionException {
        if (skip) {
            getLog().info("Izar assembly skipped.");
            return;
        }
        if (releases.isEmpty()) {
            throw new MojoExecutionException(
                    "The assemble goal needs at least one <release> naming a client release and its Maven"
                            + " coordinates.");
        }
        for (ReleaseArtifactParameter release : releases) {
            ExactMavenVersion.require(release.getVersion(),
                    "Release '" + release.getClientName() + "@" + release.getManifestVersion() + "'s version");
        }

        List<SelectedRelease> selected = new ArrayList<>();
        List<String> errors = new ArrayList<>();
        for (ReleaseArtifactParameter release : releases) {
            try {
                selected.add(resolveOrThrow(release));
            } catch (ArtifactResolutionException e) {
                errors.add("Release '" + release.getClientName() + "@" + release.getManifestVersion() + "' ("
                        + release.coordinates() + "): " + e.getMessage());
            }
        }

        if (!errors.isEmpty()) {
            throw new MojoExecutionException(
                    "Could not resolve every configured release through the configured Maven repositories:\n"
                            + String.join("\n", errors));
        }

        try {
            AssembledManifest assembled =
                    new ReleaseAssembler().assemble(new ReleaseSelection(graph, environment, selected));
            assembled.writeTo(outputDirectory.toPath());
            getLog().info("Assembled " + assembled.manifest().operations().size() + " operation(s) from "
                    + assembled.lock().releases().size() + " release(s) into " + outputDirectory + ".");
        } catch (AssemblyException e) {
            throw new MojoExecutionException(e.getMessage(), e);
        }
    }

    private SelectedRelease resolveOrThrow(ReleaseArtifactParameter release) throws ArtifactResolutionException {
        ArtifactResult result = repositorySystem.resolveArtifact(
                repositorySystemSession,
                new ArtifactRequest(
                        new DefaultArtifact(
                                release.getGroupId(),
                                release.getArtifactId(),
                                release.getClassifier(),
                                release.getExtension(),
                                release.getVersion()),
                        remoteRepositories,
                        null));
        return new SelectedRelease(
                new ClientRelease(release.getClientName(), release.getManifestVersion()),
                ManifestSources.file(result.getArtifact().getFile().toPath()));
    }
}
