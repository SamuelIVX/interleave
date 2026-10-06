/** Compares modeled access footprints, including array aliases and enabledness interference. */
package dev.samhb.interleave.por;

import dev.samhb.interleave.core.*;
import java.util.*;

/** Alias-aware access comparison; soundness requires complete stable modeled footprints. */
public final class IndependenceRelation {
    /** Creates independence relation with its default configuration. */
    public IndependenceRelation() {}

    /**
     * Checks whether complete step accesses have no cross-thread write conflict.
     * @param a first action, including guard and outcome dependencies
     * @param b second action
     * @return whether declared accesses are independent under array alias rules
     */
    public boolean areIndependent(Step a, Step b) {
        Set<dev.samhb.interleave.core.MemoryLocation> aReads = a.reads();
        Set<dev.samhb.interleave.core.MemoryLocation> aWrites = a.writes();
        Set<dev.samhb.interleave.core.MemoryLocation> bReads = b.reads();
        Set<dev.samhb.interleave.core.MemoryLocation> bWrites = b.writes();

        return areIndependent(aReads, aWrites, bReads, bWrites);
    }

    /**
     * Compares immutable snapshots of step or remaining-thread footprints.
     * @param aReads first footprint's complete reads, including guards/outcomes
     * @param aWrites first footprint's complete writes
     * @param bReads second footprint's complete reads
     * @param bWrites second footprint's complete writes
     * @return whether no write overlaps the other footprint's reads or writes
     */
    boolean areIndependent(Set<MemoryLocation> aReads, Set<MemoryLocation> aWrites,
                           Set<MemoryLocation> bReads, Set<MemoryLocation> bWrites) {

        // Two steps are independent if neither writes to a location the other reads or writes.
        // Array handling: a dynamic-index access is reported as bare "arr" and conflicts with
        // any constant-index "arr[k]" of the same array (base-vs-element overlap), because
        // the dynamic index could alias the constant one. arr[0] vs arr[1] stays independent.
        for (dev.samhb.interleave.core.MemoryLocation loc : aWrites) {
            if (conflictsWithAny(loc, bReads) || conflictsWithAny(loc, bWrites)) {
                return false;
            }
        }
        for (dev.samhb.interleave.core.MemoryLocation loc : bWrites) {
            if (conflictsWithAny(loc, aReads) || conflictsWithAny(loc, aWrites)) {
                return false;
            }
        }

        return true;
    }

    /**
     * Tests visibility using the same alias convention as dependency checking.
     * @param writes modeled writes
     * @param observations property reads
     * @return whether a write may change an observed location
     */
    boolean writesOverlap(Set<MemoryLocation> writes, Set<MemoryLocation> observations) {
        for (MemoryLocation location : writes) {
            if (conflictsWithAny(location, observations)) return true;
        }
        return false;
    }

    /**
     * Tests one location against an over-approximated access set, including array-base aliases.
     * @param loc modeled location to check for aliases
     * @param set conservative access locations to compare against
     * @return true if the location aliases any member of the access set
     */
    private static boolean conflictsWithAny(dev.samhb.interleave.core.MemoryLocation loc, Set<dev.samhb.interleave.core.MemoryLocation> set) {
        for (dev.samhb.interleave.core.MemoryLocation other : set) {
            if (conflicts(loc, other)) return true;
        }
        return false;
    }

    /**
     * An array base may alias any element, while distinct literal elements remain disjoint.
     * @param a first modeled location
     * @param b second modeled location
     * @return true for equal names or an array-base/element alias
     */
    private static boolean conflicts(dev.samhb.interleave.core.MemoryLocation a, dev.samhb.interleave.core.MemoryLocation b) {
        String an = a.toString();
        String bn = b.toString();
        if (an.equals(bn)) return true;
        if (an.startsWith(bn + "[")) return true;
        return bn.startsWith(an + "[");
    }

    /**
     * Checks whether one currently enabled action changes another action's enabledness.
     * @param config current configuration; not mutated
     * @param threadA action to execute on an isolated state copy
     * @param threadB action whose enabledness is compared
     * @param threads program threads indexed by ID
     * @return whether the observed enabledness changes
     */
    public boolean hasEnableDisableInterference(Configuration config, int threadA, int threadB, List<ModelThread> threads) {
        if (threadA == threadB) return false;

        ModelThread threadAType = threads.get(threadA);
        ModelThread threadBType = threads.get(threadB);

        int pcA = config.programCounters().get(threadA);
        int pcB = config.programCounters().get(threadB);

        if (pcA >= threadAType.steps().size() || pcB >= threadBType.steps().size()) {
            return false;
        }

        Step stepA = threadAType.steps().get(pcA);
        Step stepB = threadBType.steps().get(pcB);

        if (!stepA.enabled(config.state())) {
            return false;
        }

        boolean bEnabledBefore = stepB.enabled(config.state());

        SharedState nextState = config.state().deepCopy();
        stepA.execute(nextState);

        boolean bEnabledAfter = stepB.enabled(nextState);

        return bEnabledBefore != bEnabledAfter;
    }
}
