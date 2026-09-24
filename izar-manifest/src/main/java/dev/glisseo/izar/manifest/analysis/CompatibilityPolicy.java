package dev.glisseo.izar.manifest.analysis;

/**
 * How a caller wants a {@link CandidateCheckReport} judged, per decision 22 of the phase 2
 * specification: "the caller chooses whether advisory compatibility findings fail CI."
 *
 * <p>A candidate validation failure is never advisory under either policy: GraphQL Java's validator
 * has already confirmed the operation would not execute against the candidate, so both policies
 * treat that as {@link CandidateCheckOutcome#POLICY_FAILURE}. What the two policies disagree on is
 * a structural change flagged {@link ImpactReport.Severity#BREAKING} without a confirmed validation
 * failure to back it, and whether incomplete usage analysis blocks success.
 */
public enum CompatibilityPolicy {

    /** Breaking structural changes fail the check, and incomplete usage analysis cannot pass. */
    STRICT,

    /**
     * Breaking structural changes and incomplete usage analysis are reported but never fail the
     * check on their own; only a confirmed candidate validation failure does.
     */
    ADVISORY;

    /**
     * Judges {@code report} against this policy. A confirmed finding, a validation failure always,
     * a breaking structural change under {@link #STRICT}, is never masked by incomplete analysis
     * elsewhere in the report: {@link CandidateCheckOutcome#POLICY_FAILURE} takes precedence over
     * {@link CandidateCheckOutcome#INCOMPLETE_ANALYSIS}, since incompleteness casts doubt on an
     * unconfirmed absence of a problem, not on a problem this report already confirmed. Incomplete
     * analysis under {@link #STRICT} only turns an otherwise-clean report into
     * {@link CandidateCheckOutcome#INCOMPLETE_ANALYSIS} instead of {@link CandidateCheckOutcome#SUCCESS}.
     */
    public CandidateCheckOutcome evaluate(CandidateCheckReport report) {
        boolean violatesPolicy = !report.validationFailures().isEmpty()
                || (this == STRICT && report.hasBreakingChanges());
        if (violatesPolicy) {
            return CandidateCheckOutcome.POLICY_FAILURE;
        }
        if (this == STRICT && !report.complete()) {
            return CandidateCheckOutcome.INCOMPLETE_ANALYSIS;
        }
        return CandidateCheckOutcome.SUCCESS;
    }
}
