package dev.glisseo.izar.scalars;

/** A raw wire value's runtime type, for a codec's own "got the wrong kind of value" diagnostics. */
final class ScalarValues {

    private ScalarValues() {}

    static String describe(Object value) {
        return value == null ? "null" : value.getClass().getName();
    }
}
