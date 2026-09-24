package dev.glisseo.izar.compiler.internal.schema;

import dev.glisseo.izar.compiler.OperationGenerationException;
import graphql.schema.GraphQLSchema;
import graphql.schema.idl.SchemaParser;
import graphql.schema.idl.TypeDefinitionRegistry;
import graphql.schema.idl.UnExecutableSchemaGenerator;
import graphql.schema.idl.errors.SchemaProblem;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/**
 * Loads a checked-in or pinned schema into a {@link GraphQLSchema}. Never fetched by live
 * introspection.
 *
 * <p>Builds an "un-executable" schema: one with no {@code DataFetcher}s wired, which is all a
 * client-side tool needs to validate operations and resolve field types.
 */
public final class SchemaLoader {

    private SchemaLoader() {}

    public static GraphQLSchema load(List<Path> schemaFiles) {
        SchemaParser parser = new SchemaParser();
        TypeDefinitionRegistry registry = new TypeDefinitionRegistry();
        for (Path schemaFile : schemaFiles) {
            String content;
            try {
                content = Files.readString(schemaFile);
            } catch (IOException e) {
                throw new UncheckedIOException("Could not read schema file " + schemaFile, e);
            }
            try {
                registry = registry.merge(parser.parse(content));
            } catch (SchemaProblem e) {
                throw new OperationGenerationException(
                        "Invalid schema in " + schemaFile + ": " + e.getMessage());
            }
        }
        try {
            return UnExecutableSchemaGenerator.makeUnExecutableSchema(registry);
        } catch (RuntimeException e) {
            throw new OperationGenerationException("Invalid schema: " + e.getMessage());
        }
    }
}
