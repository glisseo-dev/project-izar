package dev.glisseo.izar.manifest.analysis;

import graphql.schema.GraphQLSchema;

/**
 * A schema version becoming available for analysis: its name and parsed content.
 *
 * <p>This is a narrow, dependency-free seam, not a full event type of its own. The host
 * application's {@code schema} feature owns the actual {@code SchemaPublished} event and publishes
 * it on every successful upload; that event implements this interface so a module reacting to
 * publication does not import the host application's event type directly. Both sides already
 * depend on izar-manifest, so hosting the contract here keeps the module graph acyclic.
 *
 * <p>A Spring {@code @EventListener} typed to this interface still fires for any concrete event
 * implementing it, regardless of which module published it: Spring resolves listeners by the
 * published object's actual runtime type, not by the static type used to publish it.
 */
public interface SchemaPublication {
    String schemaName();

    GraphQLSchema schema();
}
