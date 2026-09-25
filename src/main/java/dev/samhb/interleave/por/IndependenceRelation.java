package dev.samhb.interleave.por;

import dev.samhb.interleave.core.*;
import java.util.*;

public final class IndependenceRelation {
    public boolean areIndependent(Step a, Step b) {
        Set<dev.samhb.interleave.core.MemoryLocation> aReads = a.reads();
        Set<dev.samhb.interleave.core.MemoryLocation> aWrites = a.writes();
        Set<dev.samhb.interleave.core.MemoryLocation> bReads = b.reads();
        Set<dev.samhb.interleave.core.MemoryLocation> bWrites = b.writes();

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

    private static boolean conflictsWithAny(dev.samhb.interleave.core.MemoryLocation loc, Set<dev.samhb.interleave.core.MemoryLocation> set) {
        for (dev.samhb.interleave.core.MemoryLocation other : set) {
            if (conflicts(loc, other)) return true;
        }
        return false;
    }

    private static boolean conflicts(dev.samhb.interleave.core.MemoryLocation a, dev.samhb.interleave.core.MemoryLocation b) {
        String an = a.toString();
        String bn = b.toString();
        if (an.equals(bn)) return true;
        if (an.startsWith(bn + "[")) return true;
        return bn.startsWith(an + "[");
    }

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
