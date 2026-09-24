/**
 * Assembles and rewrites the final executable document text sent to a server.
 *
 * <p>Combines an operation with the fragments it references, and adds the {@code __typename}
 * discriminator fields polymorphic decoding needs, without touching the authored {@code .graphql}
 * files.
 */
@NullMarked
package dev.glisseo.izar.compiler.internal.document;

import org.jspecify.annotations.NullMarked;
