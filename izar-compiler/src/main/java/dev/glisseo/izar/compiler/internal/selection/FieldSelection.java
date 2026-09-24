package dev.glisseo.izar.compiler.internal.selection;

/**
 * One selected field, after merging every occurrence (direct, or reached through a fragment)
 * that contributes to the same response key.
 *
 * @param responseName the field's response key: its alias if aliased, otherwise its schema name
 * @param shape what the field decodes to
 */
public record FieldSelection(String responseName, FieldShape shape) {}
