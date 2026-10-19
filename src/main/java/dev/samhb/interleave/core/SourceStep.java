/** Immutable source instruction protocol used by the shared execution boundary. */
package dev.samhb.interleave.core;

import java.util.List;

/** A source action with forward continuation and diagnostic identity. */
public interface SourceStep extends Step {
    /**
     * Executes against an isolated copy, returning its selected continuation or terminal event.
     * @param threads immutable definitions
     * @param parent committed parent configuration
     * @param threadId selected owner
     * @param scratch mutable execution copy owned by this attempt
     * @return isolated execution result
     */
    StepTransition transition(List<ModelThread> threads, Configuration parent, int threadId, SharedState scratch);

    /**
     * Returns this occurrence's immutable source identity.
     * @param pc actual instruction position
     * @return diagnostic metadata
     */
    ExecutedStep metadata(int pc);

    /**
     * Source control flow requires the shared executor rather than legacy outcome-only execution.
     * @param state supplied state
     * @return never returns
     * @throws UnsupportedOperationException when a caller bypasses TransitionExecutor
     */
    @Override default StepOutcome execute(SharedState state) {
        throw new UnsupportedOperationException("Execute source instructions through TransitionExecutor");
    }
}
