package dev.glisseo.izar.operation;

/**
 * How a generated {@link GraphQlOperation#decode} handles an output enum value or polymorphic
 * concrete type the schema used at generation time did not know about.
 *
 * <p>{@link #LENIENT} preserves the unfamiliar value or type name in the generated {@code
 * Unrecognized} representation. {@link #STRICT} fails decoding instead, with a message naming the
 * enum or polymorphic type and the unrecognized value. This is not baked into generated code: a
 * generated {@code decode} method receives it as a parameter, so a caller such as {@code
 * SynchronousGraphQlOperations} can fix its own choice once, at construction, and pass it on every
 * call, without affecting any other client executing the same generated operation.
 */
public enum DecodingPolicy {
    LENIENT,
    STRICT
}
