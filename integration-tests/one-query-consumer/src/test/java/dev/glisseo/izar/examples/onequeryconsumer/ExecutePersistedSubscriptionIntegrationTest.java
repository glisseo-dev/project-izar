package dev.glisseo.izar.examples.onequeryconsumer;

import static org.assertj.core.api.Assertions.assertThat;

import dev.glisseo.izar.client.ReactiveGraphQlOperations;
import dev.glisseo.izar.generated.books.WatchBookSubscription;
import dev.glisseo.izar.manifest.ManifestOperation;
import dev.glisseo.izar.manifest.OperationManifest;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.test.StepVerifier;

/** Issue 135's end-to-end proof for generated hash-only subscriptions over HTTP SSE. */
@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = "spring.graphql.schema.locations=file:src/main/graphql/")
class ExecutePersistedSubscriptionIntegrationTest {

    @TempDir
    static Path tempDir;

    @LocalServerPort
    private int port;

    @DynamicPropertySource
    static void manifestFile(DynamicPropertyRegistry registry) throws IOException {
        WatchBookSubscription operation = new WatchBookSubscription();
        Path manifestFile = tempDir.resolve("manifest.json");
        Files.writeString(
                manifestFile,
                OperationManifest.of(List.of(ManifestOperation.of(
                                operation.operationName(), "subscription", operation.document())))
                        .toJson());
        registry.add("izar.graphql.server.manifest-file", manifestFile::toString);
    }

    @Test
    void receivesAndDecodesMultipleGeneratedPersistedSubscriptionEvents() {
        ReactiveGraphQlOperations operations = ReactiveGraphQlOperations.persistedQuery(
                WebClient.builder().baseUrl("http://localhost:" + port + "/graphql").build());

        StepVerifier.create(operations.executeSubscription(new WatchBookSubscription()))
                .assertNext(result -> assertThat(result.assertNoErrors().bookChanged())
                        .isEqualTo(new WatchBookSubscription.BookChanged("Dune", 412)))
                .assertNext(result -> assertThat(result.assertNoErrors().bookChanged())
                        .isEqualTo(new WatchBookSubscription.BookChanged("Children of Dune", 408)))
                .verifyComplete();
    }
}
