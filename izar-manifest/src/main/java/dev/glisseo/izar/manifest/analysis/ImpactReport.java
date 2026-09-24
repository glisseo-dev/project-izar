package dev.glisseo.izar.manifest.analysis;

import java.util.List;
import org.jspecify.annotations.Nullable;

/**
 * The structural diff between two stored schema versions, cross-referenced against every
 * registered client release's stored operations at the "from" version, computed fresh from
 * the schema feature rather than kept incrementally in sync with schema uploads.
 *
 * @param fromRevision the earlier compared schema revision, or {@code null} if there is no prior
 *     version to diff against (fewer than two schema versions have ever been uploaded)
 * @param toRevision the later compared schema revision, or {@code null} if no schema has been
 *     uploaded yet
 * @param selectionRevision the release-selection revision (the exact set of currently
 *     registered client releases) the comparison's usage was cross-referenced against, or
 *     {@code null} if there was no comparison to compute one for
 * @param complete {@code true} if every registered operation's document was fully analyzed against
 *     {@code fromRevision}; {@code false} if {@code incomplete} names at least one that was not
 * @param incomplete every registered operation whose document failed to parse or whose traversal
 *     did not finish, each naming the release(s) it belongs to
 */
public record ImpactReport(
        @Nullable String fromRevision,
        @Nullable String toRevision,
        @Nullable String selectionRevision,
        boolean complete,
        List<IncompleteOperation> incomplete,
        List<SchemaChange> changes) {

    public static ImpactReport empty(@Nullable String toRevision, @Nullable String selectionRevision) {
        return new ImpactReport(null, toRevision, selectionRevision, true, List.of(), List.of());
    }

    /**
     * @param granularity how precisely {@code usedBy} is attributed to this exact change, per ADRs
     *     0019 and 0020 — see {@link Granularity}
     * @param unconfirmedNoDependency {@code true} when {@code usedByCount} is zero but the report's
     *     analysis was incomplete, so this cannot be read as an unqualified "no client depends on
     *     this" — a client with a currently unanalyzable document might have referenced it
     */
    public record SchemaChange(
            Severity severity,
            Category category,
            String description,
            String typeName,
            @Nullable String fieldName,
            @Nullable String argumentName,
            int usedByCount,
            List<OperationRef> usedBy,
            Granularity granularity,
            boolean unconfirmedNoDependency) {}

    public enum Severity {
        BREAKING,
        NON_BREAKING
    }

    /**
     * How precisely a {@link SchemaChange}'s {@code usedBy} identifies its actual dependents, per
     * ADR 0019 (coverage) and ADR 0020 (impact): an enum value or input-object field is never itself
     * selected in a document, so a change to one is attributed to every release using the
     * <em>enclosing type</em> at all ({@link #TYPE}) — the same release could be listed against a
     * change it never actually touches. An object/interface field change is attributed to every
     * release calling that <em>exact field</em> ({@link #FIELD}). An argument change (removed,
     * renamed, or retyped) is attributed only to releases that actually <em>supply that argument</em>
     * ({@link #ARGUMENT}) — finer than field-level, since a release can call a field without ever
     * passing one of its optional arguments. A newly added argument is the one exception that stays
     * at {@link #FIELD} granularity: nobody could already supply an argument that did not exist yet.
     */
    public enum Granularity {
        TYPE,
        FIELD,
        ARGUMENT
    }

    public enum Category {
        TYPE_REMOVED,
        TYPE_ADDED,
        FIELD_REMOVED,
        FIELD_ADDED,
        FIELD_RENAMED,
        FIELD_TYPE_CHANGED,
        ARGUMENT_REMOVED,
        ARGUMENT_ADDED_REQUIRED,
        ARGUMENT_ADDED_OPTIONAL,
        ARGUMENT_RENAMED,
        ARGUMENT_TYPE_CHANGED,
        ENUM_VALUE_REMOVED,
        ENUM_VALUE_ADDED,
        ENUM_VALUE_RENAMED,
        UNION_MEMBER_REMOVED,
        UNION_MEMBER_ADDED
    }
}
