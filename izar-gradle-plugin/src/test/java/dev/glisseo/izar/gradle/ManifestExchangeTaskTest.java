package dev.glisseo.izar.gradle;

import static org.assertj.core.api.Assertions.assertThat;

import dev.glisseo.izar.manifest.ManifestOperation;
import dev.glisseo.izar.manifest.OperationManifest;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.gradle.testkit.runner.BuildResult;
import org.gradle.testkit.runner.TaskOutcome;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Drives {@code izarAttach}, {@code izarDeploy}, and {@code izarAssemble} against a real, local
 * {@code file://} Maven repository: manifests are deployed into it first, then resolved and
 * assembled, the same round trip a client build and a deployment build make.
 */
class ManifestExchangeTaskTest {

    private static final String CATALOG =
            OperationManifest.of(List.of(ManifestOperation.of("GetBook", "query", "query GetBook { book { title } }")))
                    .toJson();
    private static final String CHECKOUT =
            OperationManifest.of(List.of(ManifestOperation.of("GetCart", "query", "query GetCart { cart { total } }")))
                    .toJson();

    @Test
    void deploysAManifestUnderIndependentCoordinates(@TempDir Path dir) {
        Path repo = dir.resolve("remote-repo");

        deploy(new TestBuild(dir.resolve("client")), repo, "catalog", "2026.09", CATALOG);

        Path artifactDirectory = repo.resolve("com/example/manifests/catalog/2026.09");
        assertThat(artifactDirectory.resolve("catalog-2026.09-izar-manifest.json")).exists();
        assertThat(artifactDirectory.resolve("catalog-2026.09.pom")).exists();
    }

    @Test
    void deployRejectsARangeVersionBeforeUploadingAnything(@TempDir Path dir) {
        Path repo = dir.resolve("remote-repo");
        TestBuild build = deployBuild(new TestBuild(dir.resolve("client")), repo, "catalog", "[1.0,2.0)", CATALOG);

        BuildResult result = build.runAndFail("izarDeploy");

        assertThat(result.getOutput()).contains("exact");
        assertThat(repo).doesNotExist();
    }

    @Test
    void publishAndPublishToMavenLocalNeverDeployTheManifest(@TempDir Path dir) {
        Path repo = dir.resolve("remote-repo");
        TestBuild build = deployBuild(new TestBuild(dir.resolve("client")), repo, "catalog", "2026.09", CATALOG);

        BuildResult result = build.run("publish");

        assertThat(result.task(":publishIzarManifestPublicationToReleasesRepository").getOutcome())
                .isEqualTo(TaskOutcome.SKIPPED);
        assertThat(repo).doesNotExist();
    }


    @Test
    void anIncompleteDeployConfigurationOnlyFailsWhenIzarDeployRuns(@TempDir Path dir) {
        TestBuild build = new TestBuild(dir).buildScript("""
                plugins { id 'dev.glisseo.izar' }
                izar { deploy { repositoryUrl = 'https://repo.example.internal/releases' } }
                """);

        build.run("tasks");

        assertThat(build.runAndFail("izarDeploy").getOutput())
                .contains("izar.deploy.repositoryId")
                .contains("izar.deploy.groupId")
                .contains("izar.deploy.version");
    }

    @Test
    void publishKeepsTheManifestOutOfEveryRepositoryAndOtherPublicationsOutOfTheDeployRepository(@TempDir Path dir) {
        Path deployRepo = dir.resolve("deploy-repo");
        Path otherRepo = dir.resolve("other-repo");
        TestBuild build = new TestBuild(dir.resolve("client")).file("manifest.json", CATALOG).file("x.txt", "x")
                .buildScript("""
                        plugins {
                            id 'maven-publish'
                            id 'dev.glisseo.izar'
                        }
                        izar {
                            deploy {
                                manifestFile = file('manifest.json')
                                groupId = 'com.example.manifests'
                                artifactId = 'catalog'
                                version = '2026.09'
                                repositoryId = 'releases'
                                repositoryUrl = '%s'
                            }
                        }
                        publishing {
                            publications { extra(MavenPublication) { groupId = 'g'; artifactId = 'extra'; version = '1'; artifact(file('x.txt')) } }
                            repositories { maven { name = 'other'; url = '%s' } }
                        }
                        """.formatted(deployRepo.toUri(), otherRepo.toUri()));

        build.run("publish");

        assertThat(deployRepo).doesNotExist();
        assertThat(otherRepo.resolve("g/extra/1")).exists();
        assertThat(otherRepo.resolve("com/example/manifests")).doesNotExist();
    }
    @Test
    void deployWithoutARepositoryFailsNamingTheMissingOptions(@TempDir Path dir) {
        TestBuild build = new TestBuild(dir).buildScript("plugins { id 'dev.glisseo.izar' }\n");

        assertThat(build.runAndFail("izarDeploy").getOutput()).contains("izar.deploy.repositoryUrl");
    }

    @Test
    void attachesTheManifestToEveryMavenPublicationUnderTheClassifier(@TempDir Path dir) throws Exception {
        Path repo = dir.resolve("repo");
        TestBuild build = new TestBuild(dir.resolve("client")).withGraphql().buildScript("""
                plugins {
                    id 'java'
                    id 'maven-publish'
                    id 'dev.glisseo.izar'
                }
                group = 'com.example'
                version = '1.2.3'
                repositories { mavenLocal(); mavenCentral() }
                dependencies { implementation 'dev.glisseo.izar:izar-operation:0.1.0-SNAPSHOT' }
                izar {
                    basePackage = 'generated.book'
                    attach {
                        enabled = true
                        classifier = 'catalog-manifest'
                    }
                }
                publishing {
                    publications { maven(MavenPublication) { from components.java } }
                    repositories { maven { name = 'test'; url = '%s' } }
                }
                """.formatted(repo.toUri()));

        BuildResult result = build.run("publishMavenPublicationToTestRepository");

        Path artifactDirectory = repo.resolve("com/example/consumer/1.2.3");
        assertThat(artifactDirectory.resolve("consumer-1.2.3-catalog-manifest.json")).exists();
        assertThat(Files.readString(artifactDirectory.resolve("consumer-1.2.3-catalog-manifest.json")))
                .contains("GetBook");
        assertThat(result.getOutput()).contains("Attached manifest artifact com.example:consumer:json:catalog-manifest:1.2.3");
    }

    @Test
    void assemblesEveryResolvedReleaseIntoOneUnionManifest(@TempDir Path dir) throws Exception {
        Path repo = dir.resolve("remote-repo");
        deploy(new TestBuild(dir.resolve("catalog-client")), repo, "catalog", "2026.09", CATALOG);
        deploy(new TestBuild(dir.resolve("checkout-client")), repo, "checkout", "2026.10", CHECKOUT);
        Path assembled = dir.resolve("assembled");

        BuildResult result = assembleBuild(new TestBuild(dir.resolve("deployment")), repo, assembled, "2026.09", "2026.10")
                .run("izarAssemble");

        assertThat(Files.readString(assembled.resolve("manifest.json"))).contains("GetBook").contains("GetCart");
        assertThat(Files.readString(assembled.resolve("lock.json")))
                .contains("storefront")
                .contains("production")
                .contains("catalog")
                .contains("checkout");
        assertThat(Files.readString(assembled.resolve("provenance.json"))).contains("2026.09").contains("2026.10");
        assertThat(result.getOutput()).contains("Assembled 2 operation(s) from 2 release(s)");
    }

    @Test
    void assembleReportsEveryUnresolvableReleaseInOneRun(@TempDir Path dir) {
        Path repo = Path.of(dir.toString(), "empty-repo");
        TestBuild build = assembleBuild(new TestBuild(dir.resolve("deployment")), repo, dir.resolve("assembled"), "2026.09", "2026.10");

        BuildResult result = build.runAndFail("izarAssemble");

        assertThat(result.getOutput())
                .contains("catalog@2026.09")
                .contains("checkout@2026.10")
                .doesNotContain("Assembled");
        assertThat(dir.resolve("assembled/manifest.json")).doesNotExist();
    }

    @Test
    void assembleRejectsRangesAndDynamicVersions(@TempDir Path dir) {
        TestBuild build = assembleBuild(new TestBuild(dir.resolve("deployment")), dir.resolve("repo"), dir.resolve("assembled"),
                "1.+", "2026.10");

        assertThat(build.runAndFail("izarAssemble").getOutput()).contains("exact").contains("1.+");
    }

    private static void deploy(TestBuild build, Path repo, String artifactId, String version, String manifestJson) {
        deployBuild(build, repo, artifactId, version, manifestJson).run("izarDeploy");
    }

    private static TestBuild deployBuild(
            TestBuild build, Path repo, String artifactId, String version, String manifestJson) {
        return build.file("manifest.json", manifestJson).buildScript("""
                plugins { id 'dev.glisseo.izar' }
                izar {
                    deploy {
                        manifestFile = file('manifest.json')
                        groupId = 'com.example.manifests'
                        artifactId = '%s'
                        version = '%s'
                        repositoryId = 'releases'
                        repositoryUrl = '%s'
                    }
                }
                """.formatted(artifactId, version, repo.toUri()));
    }

    private static TestBuild assembleBuild(
            TestBuild build, Path repo, Path outputDirectory, String catalogVersion, String checkoutVersion) {
        return build.buildScript("""
                plugins { id 'dev.glisseo.izar' }
                repositories { maven { url = '%s' } }
                izar {
                    assemble {
                        graph = 'storefront'
                        environment = 'production'
                        outputDirectory = file('%s')
                        releases {
                            catalog {
                                manifestVersion = '2026.09'
                                groupId = 'com.example.manifests'
                                artifactId = 'catalog'
                                version = '%s'
                            }
                            checkout {
                                manifestVersion = '2026.10'
                                groupId = 'com.example.manifests'
                                artifactId = 'checkout'
                                version = '%s'
                            }
                        }
                    }
                }
                """.formatted(repo.toUri(), outputDirectory.toString().replace("\\", "/"), catalogVersion,
                checkoutVersion));
    }
}
