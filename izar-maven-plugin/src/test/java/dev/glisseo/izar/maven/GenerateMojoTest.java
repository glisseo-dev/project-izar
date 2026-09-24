package dev.glisseo.izar.maven;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.lang.reflect.Field;
import java.nio.file.Files;
import java.nio.file.Path;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.project.MavenProject;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Every {@code generate} parameter is overridden by at least one consumer pom, but none of them
 * override {@code schema}, {@code operationDirectory}, {@code outputDirectory}, {@code
 * manifestFile}, or {@code skip} (see their pom comments: "schema and operationDirectory keep the
 * plugin's defaults"). These tests drive {@link GenerateMojo} directly, the same way Maven's own
 * field injection would, to prove each override actually changes behavior rather than being
 * silently ignored.
 */
class GenerateMojoTest {

    private static final String SCHEMA =
            """
            type Query {
              book: Book
            }

            type Book {
              title: String!
            }
            """;

    private static final String OPERATION =
            """
            query GetBook {
              book {
                title
              }
            }
            """;

    @Test
    void honorsOverriddenSchemaOperationDirectoryOutputDirectoryAndManifestFile(@TempDir Path tempDir)
            throws Exception {
        Path schema = writeFile(tempDir, "custom-schema.graphqls", SCHEMA);
        Path operations = Files.createDirectory(tempDir.resolve("custom-operations"));
        writeFile(operations, "GetBook.graphql", OPERATION);
        Path output = tempDir.resolve("custom-output");
        Path manifestFile = tempDir.resolve("nested/custom-manifest.json");
        MavenProject project = new MavenProject();

        mojo(schema, operations, output, manifestFile, "generated.book", false, project).execute();

        assertThat(project.getCompileSourceRoots()).contains(output.toString());
        assertThat(output.resolve("generated/book/GetBookQuery.java")).exists();
        assertThat(manifestFile).exists();
        assertThat(Files.readString(manifestFile)).contains("GetBook");
    }

    @Test
    void generatesJavaFromAnImportedManifestWithoutRewritingIt(@TempDir Path tempDir) throws Exception {
        Path schema = writeFile(tempDir, "schema.graphqls", SCHEMA);
        Path operationDirectory = tempDir.resolve("unused-operations");
        Path output = tempDir.resolve("out");
        Path manifestOutput = tempDir.resolve("manifest-output.json");
        Path manifestInput = writeFile(
                tempDir,
                "server-manifest.json",
                """
                {"format":"apollo-persisted-query-manifest","version":1,"operations":[
                  {"id":"catalog-operation-7","body":"query GetBook { book { title } }","name":"GetBook","type":"query"}
                ]}
                """);
        MavenProject project = new MavenProject();
        GenerateMojo mojo = mojo(schema, operationDirectory, output, manifestOutput, "generated.book", false, project);
        setField(mojo, "manifestInput", manifestInput.toFile());

        mojo.execute();

        assertThat(project.getCompileSourceRoots()).contains(output.toString());
        assertThat(Files.readString(output.resolve("generated/book/GetBookQuery.java")))
                .contains("OPERATION_ID = \"catalog-operation-7\"");
        assertThat(manifestOutput).doesNotExist();
    }

    @Test
    void failsNamingTheOverriddenSchemaPathWhenItIsMissing(@TempDir Path tempDir) throws Exception {
        Path missingSchema = tempDir.resolve("does-not-exist.graphqls");
        Path operations = Files.createDirectory(tempDir.resolve("operations"));
        writeFile(operations, "GetBook.graphql", OPERATION);

        GenerateMojo mojo =
                mojo(missingSchema, operations, tempDir.resolve("out"), tempDir.resolve("manifest.json"),
                        "generated", false, new MavenProject());

        // Only possible if the overridden path is what the mojo actually reads: the default
        // path (src/main/graphql/schema.graphqls under a basedir-less MavenProject) would fail
        // differently, not name this file.
        assertThatThrownBy(mojo::execute)
                .isInstanceOf(MojoExecutionException.class)
                .hasMessageContaining(missingSchema.toString());
    }

    @Test
    void failsNamingTheOverriddenOperationDirectoryWhenItIsMissing(@TempDir Path tempDir) throws Exception {
        Path schema = writeFile(tempDir, "schema.graphqls", SCHEMA);
        Path missingOperations = tempDir.resolve("does-not-exist");

        GenerateMojo mojo =
                mojo(schema, missingOperations, tempDir.resolve("out"), tempDir.resolve("manifest.json"),
                        "generated", false, new MavenProject());

        assertThatThrownBy(mojo::execute)
                .isInstanceOf(MojoExecutionException.class)
                .hasMessageContaining(missingOperations.toString());
    }

    @Test
    void skipLeavesTheCompileSourceRootUnregisteredAndWritesNoOutput(@TempDir Path tempDir) throws Exception {
        Path schema = writeFile(tempDir, "schema.graphqls", SCHEMA);
        Path operations = Files.createDirectory(tempDir.resolve("operations"));
        writeFile(operations, "GetBook.graphql", OPERATION);
        Path output = tempDir.resolve("out");
        Path manifestFile = tempDir.resolve("manifest.json");
        MavenProject project = new MavenProject();

        mojo(schema, operations, output, manifestFile, "generated", true, project).execute();

        assertThat(project.getCompileSourceRoots()).isEmpty();
        assertThat(output).doesNotExist();
        assertThat(manifestFile).doesNotExist();
    }

    private static GenerateMojo mojo(
            Path schema,
            Path operationDirectory,
            Path outputDirectory,
            Path manifestFile,
            String basePackage,
            boolean skip,
            MavenProject project)
            throws Exception {
        GenerateMojo mojo = new GenerateMojo();
        setField(mojo, "project", project);
        setField(mojo, "schema", schema.toFile());
        setField(mojo, "operationDirectory", operationDirectory.toFile());
        setField(mojo, "outputDirectory", outputDirectory.toFile());
        setField(mojo, "manifestFile", manifestFile.toFile());
        setField(mojo, "basePackage", basePackage);
        setField(mojo, "skip", skip);
        return mojo;
    }

    private static void setField(Object target, String name, Object value) throws Exception {
        Field field = GenerateMojo.class.getDeclaredField(name);
        field.setAccessible(true);
        field.set(target, value);
    }

    private static Path writeFile(Path dir, String name, String content) throws IOException {
        Path file = dir.resolve(name);
        Files.writeString(file, content);
        return file;
    }
}
