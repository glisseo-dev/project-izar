package dev.glisseo.izar.gradle;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import org.gradle.testkit.runner.BuildResult;
import org.gradle.testkit.runner.TaskOutcome;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Drives {@code izarGenerate} through a real Gradle build, the way a consumer's build script would. */
class GenerateTaskTest {

    @Test
    void generatesSourcesAndAManifestWithDefaultLocations(@TempDir Path dir) throws Exception {
        TestBuild build = new TestBuild(dir).withGraphql().buildScript("""
                plugins { id 'dev.glisseo.izar' }
                izar { basePackage = 'generated.book' }
                """);

        BuildResult result = build.run("izarGenerate");

        assertThat(result.task(":izarGenerate").getOutcome()).isEqualTo(TaskOutcome.SUCCESS);
        assertThat(dir.resolve("build/generated-sources/izar/generated/book/GetBookQuery.java")).exists();
        assertThat(Files.readString(dir.resolve("build/izar/manifest.json"))).contains("GetBook");
    }

    @Test
    void isUpToDateOnASecondRunAndRerunsWhenAnOperationChanges(@TempDir Path dir) {
        TestBuild build = new TestBuild(dir).withGraphql().buildScript("""
                plugins { id 'dev.glisseo.izar' }
                izar { basePackage = 'generated.book' }
                """);
        build.run("izarGenerate");

        assertThat(build.run("izarGenerate").task(":izarGenerate").getOutcome()).isEqualTo(TaskOutcome.UP_TO_DATE);

        build.file("src/main/graphql/GetBook.graphql", "query GetBook { book { title } }\n");
        assertThat(build.run("izarGenerate").task(":izarGenerate").getOutcome()).isEqualTo(TaskOutcome.SUCCESS);
    }

    @Test
    void compileJavaRunsGenerationFirst(@TempDir Path dir) {
        TestBuild build = new TestBuild(dir).withGraphql().buildScript("""
                plugins {
                    id 'java'
                    id 'dev.glisseo.izar'
                }
                izar { basePackage = 'generated.book' }
                """);

        BuildResult result = build.run("compileJava", "--dry-run");

        assertThat(result.getOutput()).contains(":izarGenerate").contains(":compileJava");
        assertThat(result.getOutput().indexOf(":izarGenerate")).isLessThan(result.getOutput().indexOf(":compileJava"));
    }

    @Test
    void honorsExplicitSchemaOperationDirectoryOutputDirectoryAndManifestFile(@TempDir Path dir) throws Exception {
        TestBuild build = new TestBuild(dir)
                .file("custom/custom-schema.graphqls", TestBuild.SCHEMA)
                .file("custom/ops/GetBook.graphql", TestBuild.OPERATION)
                .buildScript("""
                        plugins { id 'dev.glisseo.izar' }
                        izar {
                            basePackage = 'generated.book'
                            schema = file('custom/custom-schema.graphqls')
                            operationDirectory = file('custom/ops')
                            outputDirectory = file('custom/out')
                            manifestFile = file('custom/nested/manifest.json')
                        }
                        """);

        build.run("izarGenerate");

        assertThat(dir.resolve("custom/out/generated/book/GetBookQuery.java")).exists();
        assertThat(Files.readString(dir.resolve("custom/nested/manifest.json"))).contains("GetBook");
    }

    @Test
    void gradlePropertiesOverrideDefaultsLikeMavenUserProperties(@TempDir Path dir) {
        TestBuild build = new TestBuild(dir).withGraphql().buildScript("""
                plugins { id 'dev.glisseo.izar' }
                """);

        build.run("izarGenerate", "-Pizar.basePackage=from.property", "-Pizar.manifestFile=out/m.json");

        assertThat(dir.resolve("build/generated-sources/izar/from/property/GetBookQuery.java")).exists();
        assertThat(dir.resolve("out/m.json")).exists();
    }

    @Test
    void jacksonGenerationModeImplementsGraphQlRequest(@TempDir Path dir) throws Exception {
        TestBuild build = new TestBuild(dir).withGraphql().buildScript("""
                plugins { id 'dev.glisseo.izar' }
                izar {
                    basePackage = 'generated.book'
                    generationMode = 'JACKSON'
                }
                """);

        build.run("izarGenerate");

        String generated =
                Files.readString(dir.resolve("build/generated-sources/izar/generated/book/GetBookQuery.java"));
        assertThat(generated).contains("GraphQlRequest");
    }

    @Test
    void anUnknownGenerationModeFailsNamingTheValidOnes(@TempDir Path dir) {
        TestBuild build = new TestBuild(dir).withGraphql().buildScript("""
                plugins { id 'dev.glisseo.izar' }
                izar {
                    basePackage = 'generated.book'
                    generationMode = 'NOPE'
                }
                """);

        assertThat(build.runAndFail("izarGenerate").getOutput()).contains("NOPE").contains("JACKSON");
    }

    @Test
    void customScalarWithoutAMappingFailsAndWithOneGenerates(@TempDir Path dir) {
        TestBuild build = new TestBuild(dir)
                .file("src/main/graphql/schema.graphqls", """
                        scalar DateTime
                        type Query { now: DateTime! }
                        """)
                .file("src/main/graphql/Now.graphql", "query Now { now }\n")
                .buildScript("""
                        plugins { id 'dev.glisseo.izar' }
                        izar { basePackage = 'generated.now' }
                        """);

        assertThat(build.runAndFail("izarGenerate").getOutput()).contains("DateTime");

        build.buildScript("""
                plugins { id 'dev.glisseo.izar' }
                izar {
                    basePackage = 'generated.now'
                    scalarMappings {
                        DateTime {
                            javaTypeName = 'java.time.Instant'
                            codecClassName = 'dev.glisseo.izar.scalars.InstantScalarCodec'
                        }
                    }
                }
                """);
        build.run("izarGenerate");
        assertThat(dir.resolve("build/generated-sources/izar/generated/now/NowQuery.java")).exists();
    }

    @Test
    void generatesFromAnImportedManifestWithoutWritingTheManifestFile(@TempDir Path dir) {
        TestBuild build = new TestBuild(dir)
                .file("src/main/graphql/schema.graphqls", TestBuild.SCHEMA)
                .file("server-manifest.json", """
                        {"format":"apollo-persisted-query-manifest","version":1,"operations":[
                          {"id":"catalog-operation-7","body":"query GetBook { book { title } }","name":"GetBook","type":"query"}
                        ]}
                        """)
                .buildScript("""
                        plugins { id 'dev.glisseo.izar' }
                        izar {
                            basePackage = 'generated.book'
                            manifestInput = file('server-manifest.json')
                        }
                        """);

        build.run("izarGenerate");

        assertThat(dir.resolve("build/generated-sources/izar/generated/book/GetBookQuery.java")).exists();
        assertThat(dir.resolve("build/izar/manifest.json")).doesNotExist();
    }

    @Test
    void skipLeavesEverythingUntouched(@TempDir Path dir) {
        TestBuild build = new TestBuild(dir).withGraphql().buildScript("""
                plugins { id 'dev.glisseo.izar' }
                izar {
                    basePackage = 'generated.book'
                    skip = true
                }
                """);

        BuildResult result = build.run("izarGenerate");

        assertThat(result.task(":izarGenerate").getOutcome()).isEqualTo(TaskOutcome.SKIPPED);
        assertThat(dir.resolve("build/izar")).doesNotExist();
    }

    @Test
    void aSecondTaskInstanceGeneratesForASecondServer(@TempDir Path dir) {
        TestBuild build = new TestBuild(dir)
                .file("src/main/graphql/server-a/schema.graphqls", TestBuild.SCHEMA)
                .file("src/main/graphql/server-a/GetBook.graphql", TestBuild.OPERATION)
                .file("src/main/graphql/server-b/schema.graphqls", TestBuild.SCHEMA)
                .file("src/main/graphql/server-b/GetBook.graphql", TestBuild.OPERATION)
                .buildScript("""
                        plugins { id 'dev.glisseo.izar' }
                        tasks.named('izarGenerate') { enabled = false }
                        tasks.register('izarGenerateA', dev.glisseo.izar.gradle.IzarGenerateTask) {
                            schema = file('src/main/graphql/server-a/schema.graphqls')
                            operationDirectory = file('src/main/graphql/server-a')
                            basePackage = 'generated.servera'
                            outputDirectory = layout.buildDirectory.dir('generated-sources/server-a')
                            manifestFile = layout.buildDirectory.file('izar/server-a/manifest.json')
                        }
                        tasks.register('izarGenerateB', dev.glisseo.izar.gradle.IzarGenerateTask) {
                            schema = file('src/main/graphql/server-b/schema.graphqls')
                            operationDirectory = file('src/main/graphql/server-b')
                            basePackage = 'generated.serverb'
                            outputDirectory = layout.buildDirectory.dir('generated-sources/server-b')
                            manifestFile = layout.buildDirectory.file('izar/server-b/manifest.json')
                        }
                        """);

        BuildResult result = build.run("izarGenerate", "izarGenerateA", "izarGenerateB");

        assertThat(result.task(":izarGenerate").getOutcome()).isEqualTo(TaskOutcome.SKIPPED);

        assertThat(dir.resolve("build/generated-sources/server-a/generated/servera/GetBookQuery.java")).exists();
        assertThat(dir.resolve("build/generated-sources/server-b/generated/serverb/GetBookQuery.java")).exists();
        assertThat(dir.resolve("build/izar/server-a/manifest.json")).exists();
        assertThat(dir.resolve("build/izar/server-b/manifest.json")).exists();
    }
}
