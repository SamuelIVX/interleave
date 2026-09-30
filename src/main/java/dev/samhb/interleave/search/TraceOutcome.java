package dev.samhb.interleave.search;

public enum TraceOutcome {
    VIOLATION,
    COMPLETED,
    DEADLOCK,
    /**
     * A context-bounded search exhausted its preemption budget without finding a violation.
     *
     * <p>This is a statement about completeness, not correctness, and must never be folded into
     * a failing outcome. It is also a property of the <em>search</em> rather than of a schedule:
     * replaying a partial path cannot reproduce it, which is why the trace minimizer rejects it.
     */
    INCOMPLETE
}
