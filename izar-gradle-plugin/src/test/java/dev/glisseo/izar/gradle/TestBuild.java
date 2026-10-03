package dev.glisseo.izar.gradle;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.gradle.testkit.runner.BuildResult;
import org.gradle.testkit.runner.GradleRunner;

/** A throwaway Gradle build in a temp directory, driven through Gradle TestKit with the plugin on its classpath. */
final class TestBuild {

    static final String SCHEMA =
            """
            type Query {
              book: Book
            }

            type Book {
              title: String!
            }
            """;

    static final String OPERATION =
            """
            query GetBook {
              book {
                title
              }
            }
            """;

    private final Path directory;

    TestBuild(Path directory) {
        this.directory = directory;
    }

    Path directory() {
        return directory;
    }

    TestBuild file(String relativePath, String content) {
        Path target = directory.resolve(relativePath);
        try {
            Files.createDirectories(target.getParent());
            Files.writeString(target, content);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        return this;
    }

    /** A project with the default schema and one operation under {@code src/main/graphql}. */
    TestBuild withGraphql() {
        return file("src/main/graphql/schema.graphqls", SCHEMA).file("src/main/graphql/GetBook.graphql", OPERATION);
    }

    TestBuild buildScript(String content) {
        return file("settings.gradle", "rootProject.name = 'consumer'\n").file("build.gradle", content);
    }

    BuildResult run(String... arguments) {
        return runner(arguments).build();
    }

    BuildResult runAndFail(String... arguments) {
        return runner(arguments).buildAndFail();
    }

    private GradleRunner runner(String... arguments) {
        return GradleRunner.create()
                .withProjectDir(directory.toFile())
                .withPluginClasspath()
                .withArguments(arguments);
    }
}
