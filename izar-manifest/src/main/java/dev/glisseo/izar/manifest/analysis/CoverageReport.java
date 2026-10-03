package dev.glisseo.izar.manifest.analysis;

import java.util.List;
import org.jspecify.annotations.Nullable;

/**
 * How the current schema's declared types and fields are used by every registered client release's
 * stored operations, computed fresh from the schema feature rather than kept
 * incrementally in sync with publication.
 *
 * @param schemaRevision the uploaded schema this report was computed against, or {@code null} if no
 *     schema has been uploaded yet
 * @param selectionRevision the release-selection revision (the exact set of currently
 *     registered client releases) this report was computed against, or {@code null} if no schema
 *     has been uploaded yet
 * @param scope which registered releases the report counted
 * @param complete {@code true} if every registered operation's document was fully analyzed;
 *     {@code false} if {@code incomplete} names at least one that was not, in which case a
 *     zero-{@code usedByCount} finding elsewhere in this report is not a reliable no-dependency claim
 * @param incomplete every registered operation whose document failed to parse or whose traversal
 *     did not finish, each naming the release(s) it belongs to
 */
public record CoverageReport(
        @Nullable String schemaRevision,
        @Nullable String selectionRevision,
        ReleaseScope scope,
        int totalTypes,
        int usedTypes,
        int totalFields,
        int usedFields,
        boolean complete,
        List<IncompleteOperation> incomplete,
        List<TypeCoverage> types) {

    public static CoverageReport empty(ReleaseScope scope) {
        return new CoverageReport(null, null, scope, 0, 0, 0, 0, true, List.of(), List.of());
    }

    public record TypeCoverage(String name, Kind kind, int usedByCount, List<OperationRef> usedBy, List<FieldCoverage> fields) {}

    public record FieldCoverage(String name, int usedByCount, List<OperationRef> usedBy) {}

    public enum Kind {
        OBJECT,
        INTERFACE,
        UNION,
        ENUM,
        INPUT_OBJECT,
        SCALAR
    }
}
