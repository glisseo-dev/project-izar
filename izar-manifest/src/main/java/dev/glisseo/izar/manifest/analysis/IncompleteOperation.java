package dev.glisseo.izar.manifest.analysis;

import java.util.List;

/**
 * One registered operation whose document could not be fully analyzed against a schema: it failed
 * to parse, could not be prepared for traversal, or traversal stopped partway through because the
 * document no longer matches the schema (a stale client's operation against a newer or older
 * revision). {@code releases} names every contributing release, so the affected operation can still
 * be traced to an exact release even though its usage is incomplete.
 *
 * <p>A report listing any {@code IncompleteOperation} cannot support an unqualified no-dependency
 * or safe-removal claim: the affected operation might have referenced a field or type whose visit
 * never completed. {@link ImpactReport.SchemaChange#unconfirmedNoDependency()} marks exactly the
 * findings this leaves in doubt.
 */
public record IncompleteOperation(String operationId, List<OperationRef> releases, String reason) {}
