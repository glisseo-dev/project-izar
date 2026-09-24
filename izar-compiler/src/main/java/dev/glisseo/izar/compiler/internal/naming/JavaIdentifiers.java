package dev.glisseo.izar.compiler.internal.naming;

import java.util.Set;

/** Turns GraphQL names into Java identifiers, and disambiguates them within one namespace. */
public final class JavaIdentifiers {

    private JavaIdentifiers() {}

    private static final Set<String> RESERVED_WORDS =
            Set.of(
                    "abstract", "assert", "boolean", "break", "byte", "case", "catch", "char",
                    "class", "const", "continue", "default", "do", "double", "else", "enum",
                    "extends", "final", "finally", "float", "for", "goto", "if", "implements",
                    "import", "instanceof", "int", "interface", "long", "native", "new",
                    "package", "private", "protected", "public", "record", "return", "short",
                    "static", "strictfp", "super", "switch", "synchronized", "this", "throw",
                    "throws", "transient", "try", "var", "void", "volatile", "while", "yield",
                    "true", "false", "null");

    /** A GraphQL field's response name is already a valid Java identifier except for keywords. */
    public static String fieldIdentifier(String graphqlName) {
        return RESERVED_WORDS.contains(graphqlName) ? graphqlName + "_" : graphqlName;
    }

    /** Capitalizes a field's response name into a candidate nested-record type name. */
    public static String capitalize(String graphqlName) {
        return Character.toUpperCase(graphqlName.charAt(0)) + graphqlName.substring(1);
    }

    /**
     * Renders {@code javaTypeName} as a type reference, with a {@code @Nullable} type-use
     * annotation when {@code nullable} is {@code true}. A qualified name with no generic argument
     * (one with a package, e.g. {@code "java.time.Instant"}, as an application-configured scalar
     * mapping's Java type is likely to be) is annotated immediately before its rightmost segment,
     * since {@code "@Nullable java.time.Instant"} is not valid Java: a type-use annotation on a
     * qualified type goes on the type name itself, not the package qualifier. A generic type (e.g.
     * {@code "List<...>"}) is always annotated up front instead: its own name is never qualified,
     * whatever a nested type argument's own name looks like.
     */
    public static String nullableTypeReference(String javaTypeName, boolean nullable) {
        if (!nullable) {
            return javaTypeName;
        }
        int lastDot = javaTypeName.indexOf('<') < 0 ? javaTypeName.lastIndexOf('.') : -1;
        if (lastDot < 0) {
            return "@Nullable " + javaTypeName;
        }
        return javaTypeName.substring(0, lastDot + 1) + "@Nullable " + javaTypeName.substring(lastDot + 1);
    }

    /**
     * Renders a GraphQL scalar name as a {@code SCREAMING_SNAKE_CASE} Java constant identifier
     * segment, e.g. {@code "DateTime"} to {@code "DATE_TIME"}: an underscore is inserted before
     * every uppercase letter that is not itself the first character.
     */
    public static String constantName(String graphqlName) {
        StringBuilder out = new StringBuilder(graphqlName.length() + 4);
        for (int i = 0; i < graphqlName.length(); i++) {
            char c = graphqlName.charAt(i);
            if (i > 0 && Character.isUpperCase(c)) {
                out.append('_');
            }
            out.append(Character.toUpperCase(c));
        }
        return out.toString();
    }

    /**
     * Claims {@code candidate} in {@code usedTypeNames}, or a numbered variant if it is already
     * taken. Shared by {@code SelectionAnalyzer} and {@code VariableAnalyzer}: every generated
     * type for an operation is a sibling in one enclosing class, so output records, input
     * objects, and enums all draw from the same namespace.
     */
    public static String uniqueTypeName(Set<String> usedTypeNames, String candidate) {
        if (usedTypeNames.add(candidate)) {
            return candidate;
        }
        int suffix = 2;
        String name;
        do {
            name = candidate + suffix;
            suffix++;
        } while (!usedTypeNames.add(name));
        return name;
    }
}
