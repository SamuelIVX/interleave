/** Detailed fixed-schedule result that cannot confuse a bounded stop with successful execution. */
package dev.samhb.interleave.core;

import dev.samhb.interleave.search.Trace;
import java.util.List;

/**
 * Immutable recorded evidence and final committed position.
 * @param configuration last committed state
 * @param trace recorded attempts and metadata; null for a nonterminal prefix
 * @param kind execution classification
 * @param threadIds actual executed prefix
 * @param outcomes actual outcomes
 * @param states committed snapshots, plus unchanged snapshot for a failed instruction
 */
public record ScheduleExecution(Configuration configuration, Trace trace, Kind kind,
                                 List<Integer> threadIds, List<StepOutcome> outcomes, List<StateSnapshot> states) {
    /** Freezes the captured prefix and snapshots. */
    public ScheduleExecution {
        threadIds = List.copyOf(threadIds);
        outcomes = List.copyOf(outcomes);
        states = List.copyOf(states);
    }
    /** Distinguishes a consumed nonterminal prefix from actual terminal evidence. */
    public enum Kind { /** Schedule consumed before a terminal event. */ PREFIX,
        /** All threads ended. */ COMPLETED, /** Real modeled failure. */ VIOLATION,
        /** No live thread enabled. */ DEADLOCK, /** Bound omitted behavior. */ INCOMPLETE }
}
