package dev.glisseo.izar.integrationtests.bookstoreconsumer;

import dev.glisseo.izar.generated.books.CreateBookMutation;
import dev.glisseo.izar.generated.books.GetBookQuery;
import dev.glisseo.izar.generated.books.WatchBookSubscription;
import dev.glisseo.izar.manifest.ManifestOperation;
import graphql.ExecutionInput;
import graphql.GraphqlErrorBuilder;
import graphql.execution.preparsed.PreparsedDocumentEntry;
import graphql.execution.preparsed.PreparsedDocumentProvider;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;
import org.springframework.boot.graphql.autoconfigure.GraphQlSourceBuilderCustomizer;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.graphql.execution.ErrorType;

/**
 * Turns the in-process Spring GraphQL server into a persisted-ID server using only GraphQL Java's
 * {@link PreparsedDocumentProvider}: a hash-only request resolves to the generated operation
 * document registered under that ID, and any other ID is rejected. It stands in for any server a
 * team might run, so these tests prove the client's persisted transports without depending on an
 * Izar server module.
 */
@TestConfiguration
class PersistedDocumentsConfiguration {

    @Bean
    RecordingProvider persistedDocumentProvider() {
        Map<String, String> documentsById = new HashMap<>();
        for (String document : List.of(
                new GetBookQuery().document(),
                CreateBookMutation.builder()
                        .input(CreateBookMutation.BookInput.builder().title("x").build())
                        .build()
                        .document(),
                new WatchBookSubscription().document())) {
            documentsById.put(ManifestOperation.idFor(document), document);
        }
        return new RecordingProvider(Map.copyOf(documentsById));
    }

    @Bean
    GraphQlSourceBuilderCustomizer persistedDocumentCustomizer(RecordingProvider provider) {
        return builder -> builder.configureGraphQl(graphQl -> graphQl.preparsedDocumentProvider(provider));
    }

    /** Resolves persisted IDs and remembers the query text of every request it saw. */
    static final class RecordingProvider implements PreparsedDocumentProvider {

        private final Map<String, String> documentsById;
        private final List<String> receivedQueries = new CopyOnWriteArrayList<>();

        RecordingProvider(Map<String, String> documentsById) {
            this.documentsById = documentsById;
        }

        /** The {@code query} of every request, as GraphQL Java saw it before resolution. */
        List<String> receivedQueries() {
            return List.copyOf(receivedQueries);
        }

        @Override
        public CompletableFuture<PreparsedDocumentEntry> getDocumentAsync(
                ExecutionInput executionInput,
                java.util.function.Function<ExecutionInput, PreparsedDocumentEntry> parseAndValidate) {
            receivedQueries.add(executionInput.getQuery());
            if (!ExecutionInput.PERSISTED_QUERY_MARKER.equals(executionInput.getQuery())) {
                return CompletableFuture.completedFuture(parseAndValidate.apply(executionInput));
            }
            String id = persistedId(executionInput.getExtensions());
            String document = id == null ? null : documentsById.get(id);
            if (document == null) {
                return CompletableFuture.completedFuture(new PreparsedDocumentEntry(GraphqlErrorBuilder.newError()
                        .errorType(id == null ? ErrorType.BAD_REQUEST : ErrorType.NOT_FOUND)
                        .message(id == null
                                ? "The persistedQuery extension is invalid."
                                : "No operation matches this persisted ID.")
                        .build()));
            }
            return CompletableFuture.completedFuture(
                    parseAndValidate.apply(executionInput.transform(builder -> builder.query(document))));
        }

        private static String persistedId(Map<String, Object> extensions) {
            return extensions.get("persistedQuery") instanceof Map<?, ?> persisted
                            && persisted.get("sha256Hash") instanceof String hash
                            && !hash.isBlank()
                    ? hash
                    : null;
        }
    }
}
