/** Single execution/copy boundary shared by search, fixed schedules and replay. */
package dev.samhb.interleave.core;

import java.util.List;

/** Executes one chosen step without modifying its parent configuration. */
public final class TransitionExecutor {
    /** Prevents construction of the stateless executor. */
    private TransitionExecutor() {}

    /**
     * Runs an enabled choice once on scratch state. Legacy blocked identity successors are retained.
     * @param program immutable model
     * @param parent unmodified committed position
     * @param threadId selected thread
     * @return isolated successor or terminal evidence
     */
    public static StepTransition execute(Program program, Configuration parent, int threadId) {
        return execute(program.threads(), parent, threadId);
    }

    /**
     * Executes using thread definitions when an independence probe has no Program wrapper.
     * @param threads immutable model definitions
     * @param parent committed position
     * @param threadId chosen owner
     * @return isolated result
     */
    public static StepTransition execute(List<ModelThread> threads, Configuration parent, int threadId) {
        int pc = parent.programCounters().get(threadId);
        Step step = threads.get(threadId).steps().get(pc);
        SharedState scratch = parent.state().deepCopy();
        if (step instanceof SourceStep source) return source.transition(threads, parent, threadId, scratch);
        StepOutcome outcome = step.execute(scratch);
        Configuration next = outcome == StepOutcome.ASSERTION_FAILED ? null
            : parent.successor(threadId, outcome, threads, scratch);
        return new StepTransition(outcome, next, null, null);
    }
}
