package dev.glisseo.izar.gradle;

import dev.glisseo.izar.manifest.assembly.ExactVersion;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.gradle.api.GradleException;
import org.gradle.api.Plugin;
import org.gradle.api.Project;
import org.gradle.api.Task;
import org.gradle.api.artifacts.Configuration;
import org.gradle.api.artifacts.repositories.MavenArtifactRepository;
import org.gradle.api.credentials.PasswordCredentials;
import org.gradle.api.file.Directory;
import org.gradle.api.file.ProjectLayout;
import org.gradle.api.file.RegularFile;
import org.gradle.api.plugins.JavaPluginExtension;
import org.gradle.api.provider.Property;
import org.gradle.api.provider.Provider;
import org.gradle.api.provider.ProviderFactory;
import org.gradle.api.publish.PublishingExtension;
import org.gradle.api.publish.maven.MavenPublication;
import org.gradle.api.publish.maven.tasks.PublishToMavenLocal;
import org.gradle.api.publish.maven.tasks.PublishToMavenRepository;
import org.gradle.api.tasks.SourceSet;
import org.gradle.api.tasks.TaskProvider;

/**
 * Applies Izar to a Gradle build: an {@code izar { ... }} extension and the {@code izarGenerate},
 * {@code izarPublish}, {@code izarAttach}, {@code izarDeploy}, and {@code izarAssemble} tasks,
 * mirroring the goals of {@code izar-maven-plugin}.
 *
 * <p>The tasks hold the real options. The extension only supplies their defaults, so a second
 * GraphQL server is a second task instance, the way Maven uses a second execution. Every
 * extension option can also be set with a Gradle property named {@code izar.<option>}, the
 * counterpart of Maven's {@code -Dizar.<option>} user properties.
 */
public class IzarPlugin implements Plugin<Project> {

    static final String DEPLOY_PUBLICATION = "izarManifest";
    static final String DEFAULT_CLASSIFIER = "izar-manifest";
    static final String DEFAULT_EXTENSION = "json";

    @Override
    public void apply(Project project) {
        IzarExtension izar = project.getExtensions().create("izar", IzarExtension.class);
        ProviderFactory providers = project.getProviders();
        ProjectLayout layout = project.getLayout();

        setTaskConventions(project, izar, providers);
        TaskProvider<IzarGenerateTask> generate = project.getTasks().register("izarGenerate", IzarGenerateTask.class);
        // The generated manifest is the default input of publish, attach, and deploy. Deriving it from
        // the task output makes each of them run izarGenerate first, so they never read a stale file.
        setExtensionConventions(izar, providers, layout, generate.flatMap(IzarGenerateTask::getManifestFile));

        project.getTasks().register("izarPublish", IzarPublishTask.class);
        project.getTasks().register("izarAssemble", IzarAssembleTask.class);
        project.getTasks().register("izarDeploy", task -> {
            task.setGroup("izar");
            task.setDescription("Deploys the operation manifest as a versioned Maven artifact.");
        });

        // The source directory carries the task dependency, so compileJava runs izarGenerate first.
        project.getPlugins().withId("java", plugin -> project.getExtensions()
                .getByType(JavaPluginExtension.class)
                .getSourceSets()
                .getByName(SourceSet.MAIN_SOURCE_SET_NAME)
                .getJava()
                .srcDir(generate.flatMap(IzarGenerateTask::getOutputDirectory)));

        // Attachment and deployment read option values the build script sets after this plugin
        // applies, and may create publications the script declares, so they wire up afterwards.
        project.afterEvaluate(evaluated -> {
            if (izar.getAttach().getEnabled().get()) {
                evaluated.getTasks().register("izarAttach", IzarAttachTask.class);
            }
            attachToPublications(evaluated);
            configureDeploy(evaluated, izar);
        });
    }

    private static void setExtensionConventions(
            IzarExtension izar,
            ProviderFactory providers,
            ProjectLayout layout,
            Provider<RegularFile> generatedManifest) {
        Directory projectDirectory = layout.getProjectDirectory();

        izar.getSchema().convention(file(providers, projectDirectory, "izar.schema")
                .orElse(projectDirectory.file("src/main/graphql/schema.graphqls")));
        izar.getOperationDirectory().convention(directory(providers, projectDirectory, "izar.operations")
                .orElse(projectDirectory.dir("src/main/graphql")));
        izar.getManifestInput().convention(file(providers, projectDirectory, "izar.manifestInput"));
        izar.getOutputDirectory().convention(directory(providers, projectDirectory, "izar.outputDirectory")
                .orElse(layout.getBuildDirectory().dir("generated-sources/izar")));
        izar.getManifestFile().convention(file(providers, projectDirectory, "izar.manifestFile")
                .orElse(layout.getBuildDirectory().file("izar/manifest.json")));
        izar.getBasePackage().convention(providers.gradleProperty("izar.basePackage"));
        izar.getGenerationMode().convention(providers.gradleProperty("izar.generationMode").orElse("IZAR"));
        izar.getSkip().convention(bool(providers, "izar.skip"));

        PublishOptions publish = izar.getPublish();
        publish.getManifestFile()
                .convention(manifestOption(providers, projectDirectory, "izar.publish.manifestFile", generatedManifest));
        publish.getUrl().convention(providers.gradleProperty("izar.publish.url"));
        publish.getClientName().convention(providers.gradleProperty("izar.publish.clientName"));
        publish.getManifestVersion().convention(providers.gradleProperty("izar.publish.manifestVersion"));
        publish.getCredentialsId().convention(providers.gradleProperty("izar.publish.credentialsId"));
        publish.getSkip().convention(bool(providers, "izar.publish.skip"));

        AttachOptions attach = izar.getAttach();
        attach.getEnabled().convention(bool(providers, "izar.attach.enabled"));
        attach.getManifestFile()
                .convention(manifestOption(providers, projectDirectory, "izar.attach.manifestFile", generatedManifest));
        attach.getClassifier().convention(providers.gradleProperty("izar.attach.classifier").orElse(DEFAULT_CLASSIFIER));
        attach.getExtension().convention(providers.gradleProperty("izar.attach.extension").orElse(DEFAULT_EXTENSION));
        attach.getSkip().convention(bool(providers, "izar.attach.skip"));

        DeployOptions deploy = izar.getDeploy();
        deploy.getManifestFile()
                .convention(manifestOption(providers, projectDirectory, "izar.deploy.manifestFile", generatedManifest));
        deploy.getGroupId().convention(providers.gradleProperty("izar.deploy.groupId"));
        deploy.getArtifactId().convention(providers.gradleProperty("izar.deploy.artifactId"));
        deploy.getVersion().convention(providers.gradleProperty("izar.deploy.version"));
        deploy.getClassifier().convention(providers.gradleProperty("izar.deploy.classifier").orElse(DEFAULT_CLASSIFIER));
        deploy.getExtension().convention(providers.gradleProperty("izar.deploy.extension").orElse(DEFAULT_EXTENSION));
        deploy.getRepositoryId().convention(providers.gradleProperty("izar.deploy.repositoryId"));
        deploy.getRepositoryUrl().convention(providers.gradleProperty("izar.deploy.repositoryUrl"));
        deploy.getSkip().convention(bool(providers, "izar.deploy.skip"));

        AssembleOptions assemble = izar.getAssemble();
        assemble.getGraph().convention(providers.gradleProperty("izar.assemble.graph"));
        assemble.getEnvironment().convention(providers.gradleProperty("izar.assemble.environment"));
        assemble.getOutputDirectory().convention(directory(providers, projectDirectory, "izar.assemble.outputDirectory")
                .orElse(layout.getBuildDirectory().dir("izar/assembled")));
        assemble.getSkip().convention(bool(providers, "izar.assemble.skip"));
    }

    private static void setTaskConventions(Project project, IzarExtension izar, ProviderFactory providers) {
        project.getTasks().withType(IzarGenerateTask.class).configureEach(task -> {
            task.getSchema().convention(izar.getSchema());
            task.getOperationDirectory().convention(izar.getOperationDirectory());
            task.getManifestInput().convention(izar.getManifestInput());
            task.getOutputDirectory().convention(izar.getOutputDirectory());
            task.getManifestFile().convention(izar.getManifestFile());
            task.getBasePackage().convention(izar.getBasePackage());
            task.getGenerationMode().convention(izar.getGenerationMode());
            task.getSkip().convention(izar.getSkip());
            task.getScalarMappings().convention(providers.provider(() -> izar.getScalarMappings().stream()
                    .map(spec -> new ScalarMappingEntry(
                            spec.getName(), spec.getJavaTypeName().get(), spec.getCodecClassName().get()))
                    .toList()));
        });

        project.getTasks().withType(IzarPublishTask.class).configureEach(task -> {
            PublishOptions publish = izar.getPublish();
            task.getManifestFile().convention(publish.getManifestFile());
            task.getUrl().convention(publish.getUrl());
            task.getClientName().convention(publish.getClientName());
            task.getManifestVersion().convention(publish.getManifestVersion());
            task.getCredentialsId().convention(publish.getCredentialsId());
            task.getSkip().convention(publish.getSkip());
        });

        project.getTasks().withType(IzarAttachTask.class).configureEach(task -> {
            AttachOptions attach = izar.getAttach();
            task.getManifestFile().convention(attach.getManifestFile());
            task.getClassifier().convention(attach.getClassifier());
            task.getExtension().convention(attach.getExtension());
            task.getSkip().convention(attach.getSkip());
            task.getCoordinates().convention(providers.provider(() -> project.getGroup() + ":" + project.getName()
                    + ":" + task.getExtension().get() + ":" + task.getClassifier().get() + ":" + project.getVersion()));
        });

        project.getTasks().withType(IzarAssembleTask.class).configureEach(task -> {
            AssembleOptions assemble = izar.getAssemble();
            task.getGraph().convention(assemble.getGraph());
            task.getEnvironment().convention(assemble.getEnvironment());
            task.getOutputDirectory().convention(assemble.getOutputDirectory());
            task.getSkip().convention(assemble.getSkip());
            task.getReleases().convention(providers.provider(() -> assemble.getReleases().stream()
                    .map(ReleaseSpec::toEntry)
                    .toList()));
            task.getResolver().convention(release -> resolve(project, release));
        });
    }

    /** Resolves one release through the project's own repositories, so mirrors and credentials apply. */
    private static Path resolve(Project project, ReleaseEntry release) {
        Configuration configuration = project.getConfigurations()
                .detachedConfiguration(project.getDependencies().create(release.dependencyNotation()));
        configuration.setTransitive(false);
        return configuration.getSingleFile().toPath();
    }

    /** Adds every enabled {@link IzarAttachTask}'s manifest to every Maven publication as a classified artifact. */
    private static void attachToPublications(Project project) {
        project.getPlugins().withId("maven-publish", plugin -> project.getExtensions()
                .getByType(PublishingExtension.class)
                .getPublications()
                .withType(MavenPublication.class)
                .configureEach(publication -> {
                    if (publication.getName().equals(DEPLOY_PUBLICATION)) {
                        return;
                    }
                    project.getTasks().withType(IzarAttachTask.class).all(task -> {
                        if (task.getSkip().get()) {
                            return;
                        }
                        publication.artifact(task.getManifestFile(), artifact -> {
                            artifact.setClassifier(task.getClassifier().get());
                            artifact.setExtension(task.getExtension().get());
                            artifact.builtBy(task);
                        });
                    });
                }));
    }

    /**
     * Publishes the manifest as its own Maven publication under the configured coordinates, reachable
     * only through {@code izarDeploy}, never through {@code publish} or {@code publishToMavenLocal}.
     */
    private static void configureDeploy(Project project, IzarExtension izar) {
        DeployOptions options = izar.getDeploy();
        TaskProvider<Task> deploy = project.getTasks().named("izarDeploy");
        if (options.getSkip().get()) {
            deploy.configure(task -> task.doLast(t -> t.getLogger().lifecycle("Izar manifest deployment skipped.")));
            return;
        }
        List<String> missing = missingDeployOptions(options);
        if (!missing.isEmpty()) {
            // Deferred to execution: a build that never runs izarDeploy must not fail to configure.
            deploy.configure(task -> task.doFirst(t -> {
                throw new GradleException("izarDeploy needs " + String.join(", ", missing) + ".");
            }));
            return;
        }

        project.getPluginManager().apply("maven-publish");
        PublishingExtension publishing = project.getExtensions().getByType(PublishingExtension.class);

        MavenPublication publication = publishing.getPublications().create(DEPLOY_PUBLICATION, MavenPublication.class);
        publication.setGroupId(options.getGroupId().get());
        publication.setArtifactId(options.getArtifactId().get());
        publication.setVersion(options.getVersion().get());
        publication.artifact(options.getManifestFile(), artifact -> {
            artifact.setClassifier(options.getClassifier().get());
            artifact.setExtension(options.getExtension().get());
        });

        String repositoryUrl = options.getRepositoryUrl().get();
        MavenArtifactRepository repository = publishing.getRepositories().maven(repo -> {
            repo.setName(options.getRepositoryId().get());
            repo.setUrl(repositoryUrl);
            repo.setAllowInsecureProtocol(repositoryUrl.startsWith("http://"));
            if (repositoryUrl.startsWith("http")) {
                repo.credentials(PasswordCredentials.class);
            }
        });

        project.getTasks().withType(PublishToMavenRepository.class).configureEach(task -> {
            if (task.getPublication() == publication) {
                guard(project, task, deploy, options);
            } else if (task.getRepository() == repository) {
                // The deployment repository receives only the manifest, never the project's own publications.
                task.onlyIf("the izarDeploy repository only receives the manifest", t -> false);
            }
        });
        project.getTasks().withType(PublishToMavenLocal.class).configureEach(task -> {
            if (task.getPublication() == publication) {
                guard(project, task, deploy, options);
            }
        });
        deploy.configure(task -> task.dependsOn(project.getTasks().withType(PublishToMavenRepository.class)
                .matching(t -> t.getPublication() == publication && t.getRepository() == repository)));
    }

    private static List<String> missingDeployOptions(DeployOptions options) {
        Map<String, Property<String>> required = new LinkedHashMap<>();
        required.put("izar.deploy.repositoryUrl", options.getRepositoryUrl());
        required.put("izar.deploy.repositoryId", options.getRepositoryId());
        required.put("izar.deploy.groupId", options.getGroupId());
        required.put("izar.deploy.artifactId", options.getArtifactId());
        required.put("izar.deploy.version", options.getVersion());
        return required.entrySet().stream()
                .filter(entry -> {
                    String value = entry.getValue().getOrNull();
                    return value == null || value.isBlank();
                })
                .map(Map.Entry::getKey)
                .toList();
    }


    /**
     * Runs the deployment publication tasks only when {@code izarDeploy} is in the task graph, and
     * validates the version and manifest before anything is uploaded.
     */
    private static void guard(Project project, Task task, TaskProvider<Task> deploy, DeployOptions options) {
        task.notCompatibleWithConfigurationCache("izarDeploy checks the live task graph");
        task.onlyIf("izarDeploy was requested", t -> project.getGradle().getTaskGraph().hasTask(deploy.get()));
        task.doFirst(t -> {
            try {
                ExactVersion.require(options.getVersion().get(), "izar.deploy.version");
            } catch (IllegalArgumentException e) {
                throw new GradleException(e.getMessage(), e);
            }
            ManifestFiles.read(options.getManifestFile().get().getAsFile().toPath(), "izar.deploy.manifestFile");
        });
    }

    private static Provider<RegularFile> file(ProviderFactory providers, Directory base, String property) {
        return base.file(providers.gradleProperty(property));
    }

    /**
     * A manifest option's default: the Gradle property when set, else the generated manifest. Not
     * built with {@code orElse}, which would drop the generated file's producing task and so the
     * dependency on {@code izarGenerate}.
     */
    private static Provider<RegularFile> manifestOption(
            ProviderFactory providers, Directory base, String property, Provider<RegularFile> generated) {
        return providers.gradleProperty(property).isPresent() ? file(providers, base, property) : generated;
    }

    private static Provider<Directory> directory(ProviderFactory providers, Directory base, String property) {
        return base.dir(providers.gradleProperty(property));
    }

    private static Provider<Boolean> bool(ProviderFactory providers, String property) {
        return providers.gradleProperty(property).map(Boolean::parseBoolean).orElse(false);
    }
}
