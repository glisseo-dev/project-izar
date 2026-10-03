package dev.glisseo.izar.manifest.analysis;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.jspecify.annotations.Nullable;

/**
 * What a report counts as one operation under a {@link ReleaseScope}: a client's operation ID, with
 * the release added only under {@link ReleaseScope#ALL}, where the same operation published in
 * several releases is several operations. Operation name is not part of the unit, so two names for
 * one document within a client count once.
 */
public record OperationUnit(String clientName, String operationId, @Nullable String manifestVersion) {

    public static OperationUnit of(OperationRef ref, ReleaseScope scope) {
        return new OperationUnit(
                ref.clientName(), ref.operationId(), scope == ReleaseScope.ALL ? ref.manifestVersion() : null);
    }

    /**
     * One ref per counting unit, the newest release's ref when several share a unit, in
     * {@link OperationRef} order.
     */
    public static List<OperationRef> representatives(Collection<OperationRef> refs, ReleaseScope scope) {
        Map<OperationUnit, OperationRef> byUnit = new LinkedHashMap<>();
        for (OperationRef ref : refs) {
            byUnit.merge(of(ref, scope), ref, (kept, candidate) -> candidate.compareTo(kept) > 0 ? candidate : kept);
        }
        return byUnit.values().stream().sorted().toList();
    }
}
