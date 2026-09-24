/**
 * Execution of generated operations through application-configured Spring GraphQL clients.
 *
 * <p>Synchronous and reactive adapters share generated models, operation metadata, and
 * serialization behavior. Spring owns HTTP execution, connection pools, TLS, proxies,
 * authentication, filters, and transport instrumentation; this package supplies only the
 * operation-specific bridge: variable encoding, response mapping, scalar integration, and
 * persisted-operation metadata.
 *
 * <p>Results keep data, GraphQL errors, and extensions together, with transport and decoding
 * failures kept distinct from GraphQL execution errors. Izar adds no retry policy and
 * preserves the caller timeout and cancellation behavior.
 */
@NullMarked
package dev.glisseo.izar.client;

import org.jspecify.annotations.NullMarked;
