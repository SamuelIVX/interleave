/** Callbacks for newly visited configurations and recorded traces. */
package dev.samhb.interleave.search;

import dev.samhb.interleave.core.Configuration;

/** Callbacks for newly visited configurations and recorded traces. */
@FunctionalInterface
public interface StateVisitor {
    /**
     * Observes a newly visited search configuration.
     * @param config current search configuration
     */
    void onStateVisited(Configuration config);

    /**
     * Preemption-aware callback for context-bounded search.
     *
     * <p>Defaults to the single-argument form. That delegation is load-bearing rather than
     * incidental: limit-enforcing visitors override only {@link #onStateVisited(Configuration)},
     * so a bounded search keeps honouring {@code maxStates} / {@code maxTime} without every such
     * visitor having to learn about preemptions.
     *
     * @param config the configuration that was visited
     * @param lastThreadId the most recently scheduled thread
     * @param preemptions the preemption budget at which it was visited
     */
    default void onStateVisited(Configuration config, int lastThreadId, int preemptions) {
        onStateVisited(config);
    }

    /**
     * Observes a source execution prefix when a state-visit callback throws a limit interruption.
     * Called synchronously before traversal unwinds. After successful notification the original
     * exception is rethrown; exceptions from this notification propagate to the caller.
     * The INCOMPLETE carrier has no omitted cutoff and does not authenticate a resource cause;
     * the runner attaches its own observed cause separately. No property is evaluated to capture it.
     * Implementations must not mutate or retain the supplied live configuration.
     * @param config actual last committed configuration, borrowed for this callback only
     * @param prefix immutable source choices/outcomes/identities, excluding any omitted action
     */
    default void onInterruptedPrefix(Configuration config, Trace prefix) {
        // Observers without resource diagnostics retain their existing behavior.
    }

    /**
     * Observes a trace when exploration records it.
     * @param trace recorded execution to inspect or replay
     */
    default void onTraceCreated(Trace trace) {
        // Default no-op - implementations can override to capture traces
    }
}
