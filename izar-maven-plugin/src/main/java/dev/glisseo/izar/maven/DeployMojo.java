package dev.glisseo.izar.maven;

import dev.glisseo.izar.manifest.InvalidManifestException;
import dev.glisseo.izar.manifest.OperationManifest;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import javax.inject.Inject;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.eclipse.aether.RepositorySystem;
import org.eclipse.aether.RepositorySystemSession;
import org.eclipse.aether.artifact.Artifact;
import org.eclipse.aether.artifact.DefaultArtifact;
import org.eclipse.aether.deployment.DeployRequest;
import org.eclipse.aether.deployment.DeploymentException;
import org.eclipse.aether.repository.RemoteRepository;

/**
 * Publishes a manifest file as a versioned Maven artifact to a configured repository, so a
 * deployment build can later resolve exact client releases the same way it resolves any other
 * dependency: {@code mvn izar:deploy}.
 *
 * <p>This is a different exchange from {@link PublishMojo}'s {@code publish} goal. {@code publish}
 * registers a release with a controller's authenticated {@code /api/releases} endpoint over HTTP.
 * {@code deploy} instead uploads the manifest to an ordinary Maven repository under coordinates a
 * deployment build names explicitly. Neither goal depends on the other; a project can
 * use one, both, or neither.
 *
 * <p>The target coordinates ({@code groupId}, {@code artifactId}, {@code version}) are independent
 * of this project's own Maven coordinates: they are usually the client release identity ({@code
 * clientName} and {@code manifestVersion}) that a deployment build names in its {@link
 * AssembleMojo} configuration when resolving this artifact. Because that identity is independent, this
 * goal deploys directly through {@code RepositorySystem.deploy}, the same seam {@code mvn deploy}
 * itself uses, rather than attaching a secondary artifact to this project's own reactor build,
 * which would force the manifest's version to always match the project's version.
 *
 * <p>Declares no credential parameter of any kind. {@code repositoryId} names a repository the
 * ambient {@link RepositorySystemSession} already resolves against {@code settings.xml} servers,
 * mirrors, and proxies. Maven uses the same configuration when deploying project artifacts, so
 * repository credentials do not need plugin parameters.
 */
@Mojo(name = "deploy", threadSafe = true)
public class DeployMojo extends AbstractMojo {

    @Parameter(defaultValue = "${repositorySystemSession}", readonly = true, required = true)
    private RepositorySystemSession repositorySystemSession;

    @Inject
    private RepositorySystem repositorySystem;

    /** A manifest written by {@link GenerateMojo}, or collected by another Apollo-compatible pipeline. */
    @Parameter(
            property = "izar.deploy.manifestFile",
            defaultValue = "${project.build.directory}/izar/manifest.json",
            required = true)
    private File manifestFile;

    // groupId, artifactId, version, classifier, and extension mirror ReleaseArtifactParameter's
    // fields, but as top-level @Parameters rather than a reused nested bean: this Mojo has exactly
    // one coordinate to configure, and Maven's parameter configurator populates a nested bean only
    // for list elements like assemble's <releases>, not a single top-level parameter group.
    @Parameter(property = "izar.deploy.groupId", required = true)
    private String groupId;

    /** Typically the client name, but this goal never assumes so; {@code assemble} names it explicitly. */
    @Parameter(property = "izar.deploy.artifactId", required = true)
    private String artifactId;

    /** Typically the manifest version, but this goal never assumes so; {@code assemble} names it explicitly. */
    @Parameter(property = "izar.deploy.version", required = true)
    private String version;

    @Parameter(property = "izar.deploy.classifier", defaultValue = "izar-manifest")
    private String classifier;

    @Parameter(property = "izar.deploy.extension", defaultValue = "json")
    private String extension;

    /** Id of a {@code <repository>} or {@code <server>} entry Maven resolves policy and credentials from. */
    @Parameter(property = "izar.deploy.repositoryId", required = true)
    private String repositoryId;

    @Parameter(property = "izar.deploy.repositoryUrl", required = true)
    private String repositoryUrl;

    @Parameter(property = "izar.deploy.skip", defaultValue = "false")
    private boolean skip;

    @Override
    public void execute() throws MojoExecutionException {
        if (skip) {
            getLog().info("Izar manifest deployment skipped.");
            return;
        }

        ExactMavenVersion.require(version, "izar.deploy.version");
        OperationManifest manifest = readManifest();
        Artifact artifact = new DefaultArtifact(groupId, artifactId, classifier, extension, version)
                .setFile(manifestFile);
        RemoteRepository targetRepository = repositorySystem.newDeploymentRepository(
                repositorySystemSession,
                new RemoteRepository.Builder(repositoryId, "default", repositoryUrl).build());

        try {
            repositorySystem.deploy(
                    repositorySystemSession,
                    new DeployRequest().addArtifact(artifact).setRepository(targetRepository));
        } catch (DeploymentException e) {
            throw new MojoExecutionException("Could not deploy manifest artifact '" + artifact
                    + "' to repository '" + repositoryId + "' (" + repositoryUrl + "): " + e.getMessage(), e);
        }

        getLog().info("Deployed manifest artifact " + artifact + " with " + manifest.operations().size()
                + " operation(s) to '" + repositoryId + "' (" + repositoryUrl + ").");
    }

    private OperationManifest readManifest() throws MojoExecutionException {
        if (!manifestFile.isFile()) {
            throw new MojoExecutionException("No manifest file at " + manifestFile
                    + ". Run izar:generate or point izar.deploy.manifestFile at a collected manifest.");
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
}
