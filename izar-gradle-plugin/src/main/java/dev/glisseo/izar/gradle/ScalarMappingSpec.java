package dev.glisseo.izar.gradle;

import javax.inject.Inject;
import org.gradle.api.Named;
import org.gradle.api.provider.Property;

/**
 * One entry of {@code izar { scalarMappings { ... } }}. The entry name is the GraphQL scalar name.
 */
public abstract class ScalarMappingSpec implements Named {

    private final String name;

    @Inject
    public ScalarMappingSpec(String name) {
        this.name = name;
    }

    @Override
    public String getName() {
        return name;
    }

    /** Fully qualified Java type the scalar maps to; generated code adds no import for it. */
    public abstract Property<String> getJavaTypeName();

    /** A class implementing {@code ScalarCodec<T>} with a public no-argument constructor. */
    public abstract Property<String> getCodecClassName();
}
