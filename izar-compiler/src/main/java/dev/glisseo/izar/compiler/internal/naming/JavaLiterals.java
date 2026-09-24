package dev.glisseo.izar.compiler.internal.naming;

/** Renders Java string literals for generated source. */
public final class JavaLiterals {

    private JavaLiterals() {}

    /**
     * Quotes {@code value} as a single-line Java string literal, escaping backslashes, double
     * quotes, and newlines. A plain quoted literal, rather than a text block, keeps generated
     * output free of text-block indentation sensitivity.
     */
    public static String quote(String value) {
        StringBuilder out = new StringBuilder(value.length() + 16);
        out.append('"');
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            switch (c) {
                case '\\' -> out.append("\\\\");
                case '"' -> out.append("\\\"");
                case '\n' -> out.append("\\n");
                case '\r' -> out.append("\\n");
                case '\t' -> out.append("\\t");
                default -> out.append(c);
            }
        }
        out.append('"');
        return out.toString();
    }
}
