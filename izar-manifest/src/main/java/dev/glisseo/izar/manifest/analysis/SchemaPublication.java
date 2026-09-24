package dev.glisseo.izar.manifest.analysis;

import graphql.schema.GraphQLSchema;

/**
 * A schema version becoming available for analysis: its name and parsed content.
 *
 * <p>This is a narrow, dependency-free seam, not a full event type of its own. izar-controller's
 * {@code schema} feature owns the actual {@code SchemaPublished} event and publishes it on every
 * successful upload; that event implements this interface instead of a module reacting to
 * publication (such as izar-classification) importing izar-controller's event type directly.
 * izar-manifest is a dependency both already share one-way (izar-controller depends on it for
 * schema analysis; izar-classification depends on it for the same graphql-java schema types this
 * package already works with). izar-controller depends on izar-classification, and
 * izar-classification depends on izar-manifest, so hosting the contract here keeps the module graph
 * acyclic. See ADR 0042 and ADR 0043.
 *
 * <p>A Spring {@code @EventListener} typed to this interface still fires for any concrete event
 * implementing it, regardless of which module published it: Spring resolves listeners by the
 * published object's actual runtime type, not by the static type used to publish it.
 */
public interface SchemaPublication {
    String schemaName();

    GraphQLSchema schema();
}
