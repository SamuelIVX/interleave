/* Checked exact source-orbit exploration with original-identity execution and witnesses. */
package dev.samhb.interleave.search;

import dev.samhb.interleave.core.*;
import dev.samhb.interleave.Strategy;

/** Owns eligibility and an isolated exact orbit store; never executes canonicalized configurations. */
public final class SymmetryDfsExplorer {
    /** Creates a stateless checked explorer; every call owns fresh DFS/store state. */
    public SymmetryDfsExplorer() {}

    /**
     * Explores the source quotient with no state budget or visitor.
     * For example, four identical one-increment owners visit five representatives instead of 16 states.
     * @param program immutable compiled source model
     * @param invariant null or its compiler-built shared-state property
     * @return concrete representatives and replayable original-identity traces
     * @throws IllegalArgumentException if source symmetry eligibility fails
     * @throws NullPointerException for a null program
     */
    public DfsResult explore(Program program, Invariant invariant) {
        return explore(program, invariant, null, Long.MAX_VALUE);
    }

    /**
     * Validates eligibility and runs exact DFS, deduplicating only interchangeable value orbits.
     * Visitors observe original configurations and must not mutate modeled values. Counts are
     * representatives, not all concrete states or schedules. Diagnostic witnesses are representative;
     * source locations, original owners, failure and cutoff evidence are never relabeled.
     * @param program immutable compiler-built source model
     * @param invariant null or its compiler-built pure shared-state property
     * @param visitor read-only observer, or null; exceptions propagate as in ordinary DFS
     * @param maxStates representative-state budget; Long.MAX_VALUE means unlimited
     * @return actual concrete representatives and original witnesses
     * @throws IllegalArgumentException for unsupported source/property semantics or no eligible group
     * @throws NullPointerException for a null program
     */
    public DfsResult explore(Program program, Invariant invariant, StateVisitor visitor, long maxStates) {
        return new DfsExplorer().explore(program, invariant,
            SymmetryStateStore.forRun(program, invariant, Strategy.DFS), visitor, maxStates);
    }

}
