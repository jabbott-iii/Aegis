package io.github.jabbottiii.aegis.core.model

/**
 * The outcome of scanning one target (FR-SCAN-07). The rows of the precedence table in
 * docs/features/malware-detection.md ("Verdict aggregation") decide which verdict applies, and
 * only `VerdictAggregator` maps detector signals to a verdict.
 */
public enum class Verdict {
    CLEAN,
    MALICIOUS,
    STALKERWARE,

    /** A stalkerware heuristic or a weak stalkerware IOC fired. */
    STALKERWARE_POSSIBLE,
    PUA,
    SUSPICIOUS,

    /** Static parsing hit a limit. Informational on its own. */
    UNSCANNABLE,
    ;

    /**
     * Stalkerware verdicts go through the safety flow and are never removed in one tap
     * (docs/features/device-security-audit.md, "Stalkerware safety flow").
     */
    public val requiresSafetyFlow: Boolean
        get() =
            when (this) {
                STALKERWARE, STALKERWARE_POSSIBLE -> true
                CLEAN, MALICIOUS, PUA, SUSPICIOUS, UNSCANNABLE -> false
            }
}
