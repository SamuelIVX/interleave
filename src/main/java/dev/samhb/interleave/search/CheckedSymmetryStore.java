/** Closed capability for source-certified symmetry membership bound to one run. */
package dev.samhb.interleave.search;

import dev.samhb.interleave.Strategy;
import dev.samhb.interleave.core.*;
import java.util.Map;
import java.util.Set;

/** Only built-in stores can provide source certification and canonical owner mapping. */
public sealed interface CheckedSymmetryStore extends StateStore
        permits SymmetryStateStore, BitstateSymmetryStateStore {
    /** Rejects a different run before traversal invokes callbacks.
     * @param program exact bound source program
     * @param invariant exact bound compiler property, or null
     * @param strategy bound algorithm
     * @throws IllegalArgumentException if any identity differs
     */
    void requireRun(Program program, Invariant invariant, Strategy strategy);
    /** Tests and records canonical DPOR sleep coverage when supported.
     * @param config original source configuration
     * @param sleeping normalized original owner/action entries
     * @return whether a previously recorded context subsumes this one
     * @throws UnsupportedOperationException if this store does not support DPOR
     * @throws IllegalArgumentException for an invalid binding, owner or action
     */
    boolean sleepCovered(Configuration config, Set<Map.Entry<Integer, Step>> sleeping);
}
