package dev.glisseo.izar.maven;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.glisseo.izar.manifest.ManifestOperation;
import dev.glisseo.izar.manifest.OperationManifest;
import java.lang.reflect.Field;
import java.lang.reflect.Proxy;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.project.MavenProject;
import org.apache.maven.project.MavenProjectHelper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class AttachMojoTest {

    private static final OperationManifest MANIFEST = OperationManifest.of(
            List.of(ManifestOperation.of("GetBook", "query", "query GetBook { book { title } }")));

    @Test
    void attachesTheManifestWithDefaultClassifierAndExtension(@TempDir Path tempDir) throws Exception {
        Path manifestFile = writeManifest(tempDir.resolve("manifest.json"));
        AtomicReference<Object[]> attachment = new AtomicReference<>();

        AttachMojo mojo = mojo(manifestFile, attachment, false);

        mojo.execute();

        assertThat(attachment.get()).containsExactly(
                project(mojo), "json", "izar-manifest", manifestFile.toFile());
    }

    @Test
    void attachesTheManifestWithConfiguredClassifierAndExtension(@TempDir Path tempDir) throws Exception {
        Path manifestFile = writeManifest(tempDir.resolve("manifest.custom"));
        AtomicReference<Object[]> attachment = new AtomicReference<>();
        AttachMojo mojo = mojo(manifestFile, attachment, false);
        setField(mojo, "classifier", "approved-manifest");
        setField(mojo, "extension", "manifest");

        mojo.execute();

        assertThat(attachment.get()).containsExactly(
                project(mojo), "manifest", "approved-manifest", manifestFile.toFile());
    }

    @Test
    void failsWhenTheManifestIsMissing(@TempDir Path tempDir) throws Exception {
        AtomicReference<Object[]> attachment = new AtomicReference<>();
        AttachMojo mojo = mojo(tempDir.resolve("missing.json"), attachment, false);

        assertThatThrownBy(mojo::execute)
                .isInstanceOf(MojoExecutionException.class)
                .hasMessageContaining("missing.json");
        assertThat(attachment).hasValue(null);
    }

    @Test
    void failsWhenTheManifestIsInvalid(@TempDir Path tempDir) throws Exception {
        Path manifestFile = tempDir.resolve("invalid.json");
        Files.writeString(manifestFile, "not-json");
        AtomicReference<Object[]> attachment = new AtomicReference<>();
        AttachMojo mojo = mojo(manifestFile, attachment, false);

        assertThatThrownBy(mojo::execute)
                .isInstanceOf(MojoExecutionException.class)
                .hasMessageContaining("invalid.json")
                .hasMessageContaining("valid manifest");
        assertThat(attachment).hasValue(null);
    }

    @Test
    void skipDoesNotReadOrAttachTheManifest(@TempDir Path tempDir) throws Exception {
        AtomicReference<Object[]> attachment = new AtomicReference<>();
        AttachMojo mojo = mojo(tempDir.resolve("missing.json"), attachment, true);

        mojo.execute();

        assertThat(attachment).hasValue(null);
    }

    private static Path writeManifest(Path path) throws Exception {
        Files.writeString(path, MANIFEST.toJson());
        return path;
    }

    private static AttachMojo mojo(Path manifestFile, AtomicReference<Object[]> attachment, boolean skip)
            throws Exception {
        AttachMojo mojo = new AttachMojo();
        MavenProject project = new MavenProject();
        project.setGroupId("com.example");
        project.setArtifactId("catalog-client");
        project.setVersion("1.2.3");
        setField(mojo, "project", project);
        setField(mojo, "projectHelper", helper(attachment));
        setField(mojo, "manifestFile", manifestFile.toFile());
        setField(mojo, "classifier", "izar-manifest");
        setField(mojo, "extension", "json");
        setField(mojo, "skip", skip);
        return mojo;
    }

    private static MavenProject project(AttachMojo mojo) throws Exception {
        Field field = AttachMojo.class.getDeclaredField("project");
        field.setAccessible(true);
        return (MavenProject) field.get(mojo);
    }

    private static MavenProjectHelper helper(AtomicReference<Object[]> attachment) {
        return (MavenProjectHelper) Proxy.newProxyInstance(
                MavenProjectHelper.class.getClassLoader(),
                new Class<?>[] {MavenProjectHelper.class},
                (proxy, method, args) -> {
                    if (method.getName().equals("attachArtifact")) {
                        attachment.set(args);
                        return null;
                    }
                    throw new UnsupportedOperationException(method.toString());
                });
    }

    private static void setField(Object target, String name, Object value) throws Exception {
        Field field = AttachMojo.class.getDeclaredField(name);
        field.setAccessible(true);
        field.set(target, value);
    }
}
