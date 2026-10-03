package dev.glisseo.izar.compiler;

/** Selects the generated response model and decoder style. */
public enum GenerationMode {
    /** Generates Izar's own strongly typed decoder and unknown-value wrappers. */
    IZAR,

    /** Generates response models that Jackson can map through Spring GraphQL's {@code toEntity}. */
    JACKSON
}
