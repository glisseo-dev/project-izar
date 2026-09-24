package dev.glisseo.izar.maven;

import dev.glisseo.izar.manifest.InvalidManifestException;
import dev.glisseo.izar.manifest.OperationManifest;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import javax.inject.Inject;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.project.MavenProject;
import org.apache.maven.project.MavenProjectHelper;

/**
 * Attaches the generated manifest to the current Maven project during {@code package}.
 *
 * <p>The attached artifact uses the project's own group, artifact, and version. Maven's normal
 * {@code install} and {@code deploy} lifecycle goals then publish it with the other project
 * artifacts. This goal only validates and attaches the file; it never resolves or writes to a
 * repository. Use {@link DeployMojo} when a manifest needs independent Maven coordinates.
 */
@Mojo(name = "attach", defaultPhase = LifecyclePhase.PACKAGE, threadSafe = true)
public class AttachMojo extends AbstractMojo {

    @Parameter(defaultValue = "${project}", readonly = true, required = true)
    private MavenProject project;

    @Inject
    private MavenProjectHelper projectHelper;

    /** A manifest written by {@link GenerateMojo}, or collected by another Apollo-compatible pipeline. */
    @Parameter(
            property = "izar.attach.manifestFile",
            defaultValue = "${project.build.directory}/izar/manifest.json",
            required = true)
    private File manifestFile;

    @Parameter(property = "izar.attach.classifier", defaultValue = "izar-manifest")
    private String classifier;

    @Parameter(property = "izar.attach.extension", defaultValue = "json")
    private String extension;

    @Parameter(property = "izar.attach.skip", defaultValue = "false")
    private boolean skip;

    @Override
    public void execute() throws MojoExecutionException {
        if (skip) {
            getLog().info("Izar manifest attachment skipped.");
            return;
        }

        OperationManifest manifest = readManifest();
        projectHelper.attachArtifact(project, extension, classifier, manifestFile);
        getLog().info("Attached manifest artifact " + project.getGroupId() + ":" + project.getArtifactId()
                + ":" + extension + ":" + classifier + ":" + project.getVersion() + " with "
                + manifest.operations().size() + " operation(s).");
    }

    private OperationManifest readManifest() throws MojoExecutionException {
        if (!manifestFile.isFile()) {
            throw new MojoExecutionException("No manifest file at " + manifestFile
                    + ". Run izar:generate or point izar.attach.manifestFile at a collected manifest.");
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
