/** Declares atomic modeled actions with complete, stable footprints for reduction and replay. */
package dev.samhb.interleave.core;

import java.util.Set;

/**
 * One atomic action a model thread can take.
 *
 * <p>A step is the unit an interleaving scheduler chooses between. It declares which memory
 * locations it touches so the partial-order reduction strategies can decide whether two steps
 * commute, and it must be deterministic given a state: the same step on the same state always
 * yields the same outcome and written values. Internal or external mutable state must not influence
 * execution; all behavior belongs in the supplied modeled state.
 */
public interface Step {

    /**
     * Returns the memory locations this step reads.
     *
     * <p>Part of the independence relation's input. Underestimating this makes two steps look
     * independent when they are not, which silently prunes real interleavings.
     *
     * <p>The stable over-approximation includes enabledness, guards, assertions, evaluation errors
     * and values used by execution. Names must agree with property observations.
     *
     * @return the complete read set, empty if the step reads nothing
     */
    Set<MemoryLocation> reads();

    /**
     * Returns the memory locations this step writes.
     *
     * <p>Part of the independence relation's input, and the same hazard as {@link #reads()}: an
     * incomplete write set causes unsound pruning.
     *
     * <p>Include every changed modeled field, including per-thread registers and observation fields.
     * Conservative extra locations are permitted; omission can produce false passes.
     *
     * @return the complete stable write set, empty if the step writes nothing
     */
    Set<MemoryLocation> writes();

    /**
     * Reports whether this step could execute in the given state, without executing it.
     *
     * <p>Used to build the set of enabled steps a scheduler chooses from. Must have no side effects
     * -- a scheduler calls this constantly while building configurations.
     *
     * @param state the shared state to test against
     * @return true if the step is currently enabled
     */
    boolean enabled(SharedState state);

    /**
     * Executes this step against the given state, which is mutated in place.
     *
     * <p>A {@link StepOutcome#BLOCKED} result must leave {@code state} untouched, since the
     * scheduler treats a blocked step as one that did not happen.
     *
     * @param state the shared state to mutate
     * @return what happened
     */
    StepOutcome execute(SharedState state);
}
