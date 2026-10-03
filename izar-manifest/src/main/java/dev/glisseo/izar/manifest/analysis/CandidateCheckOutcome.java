package dev.glisseo.izar.manifest.analysis;

/**
 * The outcomes returned when a {@link CompatibilityPolicy} evaluates a
 * {@link CandidateCheckReport}.
 */
public enum CandidateCheckOutcome {
    /** No candidate validation failure, and no policy-relevant structural change. */
    SUCCESS,

    /** The candidate check ran to completion and found something the chosen policy treats as a failure. */
    POLICY_FAILURE,

    /**
     * The report's usage analysis was incomplete, the chosen policy requires completeness, and the
     * report has no other finding already confirmed as a {@link #POLICY_FAILURE}. Distinct from
     * {@link #POLICY_FAILURE}: this is not itself a confirmed compatibility violation, it is an
     * otherwise-clean analysis this policy refuses to call passing. "Incomplete analysis cannot pass
     * a strict check."
     */
    INCOMPLETE_ANALYSIS
}
