/* Checked exact source-orbit exploration with original-identity execution and witnesses. */
package dev.samhb.interleave.search;

import dev.samhb.interleave.core.*;
import dev.samhb.interleave.format.dsl.DynamicState;
import dev.samhb.interleave.format.dsl.SourceSymmetry;
import dev.samhb.interleave.state.ThreadSymmetryCanonicalizer;
import java.util.*;

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
        var groups = SourceSymmetry.groups(program, invariant);
        var canonicalizer = new ThreadSymmetryCanonicalizer(program.threadCount(), groups);
        return new DfsExplorer().explore(program, invariant, new OrbitStore(canonicalizer), visitor, maxStates);
    }

    /** Private per-run membership; cannot be reused with an uncertified program/property pair. */
    private static final class OrbitStore implements StateStore {
        /** Immutable partition/owner-value canonicalization for this checked run. */
        private final ThreadSymmetryCanonicalizer canonicalizer;
        /** Full opaque keys, without probabilistic equality or diagnostic text. */
        private final Set<String> visited = new HashSet<>();

        /** Binds this empty store to the already checked run's owner partition.
         * @param canonicalizer immutable owner-value canonicalizer
         */
        private OrbitStore(ThreadSymmetryCanonicalizer canonicalizer) { this.canonicalizer = canonicalizer; }

        /** Tests the actual configuration's orbit without retaining mutable values.
         * @param config original source configuration, read only
         * @return whether this complete value orbit was recorded
         */
        @Override public boolean isVisited(Configuration config) { return visited.contains(key(config)); }

        /** Records an immutable orbit key while execution retains original IDs.
         * @param config original source configuration, read only
         */
        @Override public void markVisited(Configuration config) { visited.add(key(config)); }

        /** Clears this run's orbit membership; no program definitions are modified. */
        @Override public void clear() { visited.clear(); }

        /** Encodes complete PC/local frames and fixed shared values.
         * @param config original configuration from the validated source program
         * @return opaque exact orbit-value key
         */
        private String key(Configuration config) {
            return canonicalizer.canonicalize((DynamicState) config.state(), config.programCounters()).key();
        }
    }
}
