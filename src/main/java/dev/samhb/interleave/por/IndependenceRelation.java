package dev.samhb.interleave.por;

import dev.samhb.interleave.core.*;
import java.util.*;

public final class IndependenceRelation {
    public boolean areIndependent(Step a, Step b) {
        Set<dev.samhb.interleave.core.MemoryLocation> aReads = a.reads();
        Set<dev.samhb.interleave.core.MemoryLocation> aWrites = a.writes();
        Set<dev.samhb.interleave.core.MemoryLocation> bReads = b.reads();
        Set<dev.samhb.interleave.core.MemoryLocation> bWrites = b.writes();
        
        // Two steps are independent if:
        // 1. Neither writes to a location the other reads or writes
        // (Read-read is independent; write-read and write-write are conflicts)
        for (dev.samhb.interleave.core.MemoryLocation loc : aWrites) {
            if (bReads.contains(loc) || bWrites.contains(loc)) {
                return false;
            }
        }
        for (dev.samhb.interleave.core.MemoryLocation loc : bWrites) {
            if (aReads.contains(loc) || aWrites.contains(loc)) {
                return false;
            }
        }
        
        return true;
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
