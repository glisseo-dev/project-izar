package dev.glisseo.izar.gradle;

import java.io.Serializable;

/** The resolved value of a {@link ScalarMappingSpec}, safe to use as a task input. */
public record ScalarMappingEntry(String graphqlScalarName, String javaTypeName, String codecClassName)
        implements Serializable {}
