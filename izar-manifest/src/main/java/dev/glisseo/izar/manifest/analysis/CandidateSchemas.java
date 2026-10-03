package dev.glisseo.izar.manifest.analysis;

import graphql.schema.GraphQLSchema;

/**
 * A baseline and candidate client-facing API schema for {@link CandidateChecker}, each carrying a
 * caller-supplied identity string used only to label results.
 *
 * <p>Neither identity is a revision Izar tracks itself: a candidate check runs entirely outside
 * any stored schema history, so {@code baselineIdentity} and {@code
 * candidateIdentity} are opaque labels a caller chooses, typically a file path.
 */
public record CandidateSchemas(GraphQLSchema baseline, String baselineIdentity, GraphQLSchema candidate, String candidateIdentity) {}
