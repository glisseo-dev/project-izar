package dev.glisseo.izar.examples.nightsky.server;

import graphql.ExecutionInput;
import graphql.GraphqlErrorBuilder;
import graphql.execution.preparsed.PreparsedDocumentEntry;
import graphql.execution.preparsed.PreparsedDocumentProvider;
import java.io.IOException;
import java.io.InputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import org.springframework.boot.graphql.autoconfigure.GraphQlSourceBuilderCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.ClassPathResource;
import org.springframework.graphql.execution.ErrorType;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

@Configuration
class PersistedQueryConfiguration {

    private static final String MANIFEST_PATH = "izar/manifest.json";
    private static final String EXTENSION_NAME = "persistedQuery";

    @Bean
    GraphQlSourceBuilderCustomizer persistedQueryDocumentProvider(ObjectMapper objectMapper) {
        PreparsedDocumentProvider provider = loadManifest(objectMapper);
        return builder -> builder.configureGraphQl(graphQl -> graphQl.preparsedDocumentProvider(provider));
    }

    private static PreparsedDocumentProvider loadManifest(ObjectMapper objectMapper) {
        Map<String, String> documents = new HashMap<>();
        try (InputStream input = new ClassPathResource(MANIFEST_PATH).getInputStream()) {
            JsonNode manifest = objectMapper.readTree(input);
            if (!"apollo-persisted-query-manifest".equals(manifest.path("format").asText())
                    || manifest.path("version").asInt() != 1
                    || !manifest.path("operations").isArray()) {
                throw new IllegalStateException("Unsupported persisted-query manifest: " + MANIFEST_PATH);
            }

            for (JsonNode operation : manifest.path("operations")) {
                String id = operation.path("id").asText();
                String body = operation.path("body").asText();
                if (id.isBlank() || body.isBlank() || !id.equals(sha256(body))) {
                    throw new IllegalStateException("Invalid operation entry in " + MANIFEST_PATH + ": " + id);
                }
                if (documents.putIfAbsent(id, body) != null) {
                    throw new IllegalStateException("Duplicate operation ID in " + MANIFEST_PATH + ": " + id);
                }
            }
        } catch (IOException e) {
            throw new IllegalStateException("Could not read persisted-query manifest " + MANIFEST_PATH, e);
        }

        Map<String, String> registeredDocuments = Map.copyOf(documents);
        return (executionInput, parseAndValidate) -> {
            if (!ExecutionInput.PERSISTED_QUERY_MARKER.equals(executionInput.getQuery())) {
                return CompletableFuture.completedFuture(parseAndValidate.apply(executionInput));
            }

            String id = persistedId(executionInput.getExtensions());
            String document = registeredDocuments.get(id);
            if (document == null) {
                return CompletableFuture.completedFuture(new PreparsedDocumentEntry(GraphqlErrorBuilder.newError()
                        .errorType(id == null ? ErrorType.BAD_REQUEST : ErrorType.NOT_FOUND)
                        .message(id == null
                                ? "The persistedQuery extension is invalid."
                                : "No operation matches this persisted ID.")
                        .build()));
            }

            ExecutionInput resolved = executionInput.transform(builder -> builder.query(document));
            return CompletableFuture.completedFuture(parseAndValidate.apply(resolved));
        };
    }

    private static String persistedId(Map<String, Object> extensions) {
        Object value = extensions.get(EXTENSION_NAME);
        if (!(value instanceof Map<?, ?> persistedQuery)) {
            return null;
        }
        Object version = persistedQuery.get("version");
        Object hash = persistedQuery.get("sha256Hash");
        if ((version != null && (!(version instanceof Number number) || number.intValue() != 1))
                || !(hash instanceof String id)
                || id.isBlank()) {
            return null;
        }
        return id;
    }

    private static String sha256(String document) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(document.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is unavailable.", e);
        }
    }
}
