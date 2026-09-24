package dev.glisseo.izar.manifest.analysis;

import graphql.schema.GraphQLSchema;
import graphql.schema.idl.SchemaParser;
import graphql.schema.idl.TypeDefinitionRegistry;
import graphql.schema.idl.UnExecutableSchemaGenerator;
import graphql.schema.idl.errors.SchemaProblem;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Loads a locally composed schema SDL document into a {@link GraphQLSchema}, never by live
 * introspection or a federation composition step: {@link CandidateChecker}'s baseline and
 * candidate inputs are ordinary, already-composed SDL text, per decision 18 of the phase 2
 * specification.
 *
 * <p>Builds an "un-executable" schema, the same way {@code izar-compiler}'s own schema loader and
 * the controller's {@code SchemaRegistry} do: no {@code DataFetcher} is wired, since checking and
 * diffing only need type information, never resolver behavior.
 */
public final class SchemaSdlLoader {

    private SchemaSdlLoader() {}

    /** @throws InvalidSchemaException if {@code schemaFile} cannot be read or does not parse */
    public static GraphQLSchema load(Path schemaFile) {
        String sdl;
        try {
            sdl = Files.readString(schemaFile);
        } catch (IOException e) {
            throw new InvalidSchemaException("Could not read schema file '" + schemaFile + "': " + e.getMessage(), e);
        }
        return parse(sdl);
    }

    /** @throws InvalidSchemaException if {@code sdl} does not parse or does not describe a well-formed schema */
    public static GraphQLSchema parse(String sdl) {
        TypeDefinitionRegistry registry;
        try {
            registry = new SchemaParser().parse(sdl);
        } catch (SchemaProblem e) {
            throw new InvalidSchemaException("Invalid schema: " + e.getMessage(), e);
        }
        try {
            return UnExecutableSchemaGenerator.makeUnExecutableSchema(registry);
        } catch (RuntimeException e) {
            throw new InvalidSchemaException("Invalid schema: " + e.getMessage(), e);
        }
    }
}
