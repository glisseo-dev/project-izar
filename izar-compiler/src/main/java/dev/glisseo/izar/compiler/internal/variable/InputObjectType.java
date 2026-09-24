package dev.glisseo.izar.compiler.internal.variable;

import java.util.List;

/**
 * A GraphQL input object type, generated once per operation as one immutable Java class with a
 * builder, however many variables or nested fields reach it.
 *
 * <p>Not a record: {@link VariableAnalyzer} creates an instance before its fields are resolved and
 * registers it under its GraphQL name first, so a field whose own type reaches back to this type
 * (directly, or through another input type in a mutual cycle) can hold a reference to this same
 * instance instead of recursing forever. {@link #setFields} is called exactly once, after every
 * field is resolved; every reader runs after the whole operation has been analyzed, so none ever
 * observes a partially-built instance.
 */
public final class InputObjectType {

    private final String javaTypeName;
    private List<InputFieldDefinition> fields = List.of();

    InputObjectType(String javaTypeName) {
        this.javaTypeName = javaTypeName;
    }

    /** @return the generated class's simple name, unique within its enclosing operation */
    public String javaTypeName() {
        return javaTypeName;
    }

    /** @return the input type's fields, in schema-declared order */
    public List<InputFieldDefinition> fields() {
        return fields;
    }

    void setFields(List<InputFieldDefinition> fields) {
        this.fields = List.copyOf(fields);
    }
}
