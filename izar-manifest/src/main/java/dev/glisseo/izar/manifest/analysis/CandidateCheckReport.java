package dev.glisseo.izar.manifest.analysis;

import java.util.List;

/**
 * The result of one {@link CandidateChecker#check} call: every stored operation document GraphQL
 * Java's validator rejects against the candidate schema, every structural change between baseline
 * and candidate cross-referenced against baseline usage, and whether that usage analysis was
 * complete.
 *
 * <p>Carries no pass/fail verdict of its own. Per decision 22 of the phase 2 specification, a
 * caller applies a {@link CompatibilityPolicy} to this report to get one; the same report supports
 * more than one policy without being recomputed.
 *
 * @param baselineIdentity the baseline schema's caller-supplied label
 * @param candidateIdentity the candidate schema's caller-supplied label
 * @param selectionIdentity the checked release selection's caller-supplied label, for example its
 *     graph ID and environment
 * @param complete {@code true} if every checked operation's document was fully analyzed against
 *     the baseline schema; {@code false} if {@code incomplete} names at least one that was not, in
 *     which case a zero-usage finding in {@code changes} is not a reliable no-dependency claim
 * @param incomplete every checked operation whose document failed to parse or whose traversal
 *     against the baseline schema did not finish, each naming the release(s) it belongs to
 * @param validationFailures every checked operation whose document GraphQL Java rejects against
 *     the candidate schema
 * @param changes the structural diff between baseline and candidate, cross-referenced against
 *     which releases' stored operations use each changed type, field, or argument
 */
public record CandidateCheckReport(
        String baselineIdentity,
        String candidateIdentity,
        String selectionIdentity,
        boolean complete,
        List<IncompleteOperation> incomplete,
        List<OperationValidationFailure> validationFailures,
        List<ImpactReport.SchemaChange> changes) {

    public CandidateCheckReport {
        incomplete = List.copyOf(incomplete);
        validationFailures = List.copyOf(validationFailures);
        changes = List.copyOf(changes);
    }

    /** Whether {@code changes} contains at least one {@link ImpactReport.Severity#BREAKING} entry. */
    public boolean hasBreakingChanges() {
        return breakingChangeCount() > 0;
    }

    /** How many entries in {@code changes} are {@link ImpactReport.Severity#BREAKING}. */
    public long breakingChangeCount() {
        return changes.stream().filter(change -> change.severity() == ImpactReport.Severity.BREAKING).count();
    }
}
