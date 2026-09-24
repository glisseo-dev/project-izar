package dev.glisseo.izar.compiler.internal.selection;

/**
 * One concrete type's branch of a {@link PolymorphicSelection}: the type-conditioned fields a
 * fragment selected for {@code graphqlTypeName}, merged with the selection's shared fields.
 *
 * @param graphqlTypeName the concrete object type's schema name, matched against the response's
 *     {@code __typename} at decode time
 * @param selection the merged fields, rendered as an ordinary record implementing the enclosing
 *     {@link PolymorphicSelection}'s sealed interface
 */
public record PolymorphicBranch(String graphqlTypeName, ObjectSelection selection) {}
