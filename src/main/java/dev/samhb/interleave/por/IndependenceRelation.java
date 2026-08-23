package dev.samhb.interleave.por;

import dev.samhb.interleave.core.Step;
import java.util.*;

public final class IndependenceRelation {
    public boolean areIndependent(Step a, Step b) {
        Set<dev.samhb.interleave.core.MemoryLocation> aReads = a.reads();
        Set<dev.samhb.interleave.core.MemoryLocation> aWrites = a.writes();
        Set<dev.samhb.interleave.core.MemoryLocation> bReads = b.reads();
        Set<dev.samhb.interleave.core.MemoryLocation> bWrites = b.writes();
        
        Set<dev.samhb.interleave.core.MemoryLocation> aTotal = new HashSet<>();
        aTotal.addAll(aReads);
        aTotal.addAll(aWrites);
        
        Set<dev.samhb.interleave.core.MemoryLocation> bTotal = new HashSet<>();
        bTotal.addAll(bReads);
        bTotal.addAll(bWrites);
        
        for (dev.samhb.interleave.core.MemoryLocation loc : aTotal) {
            if (bTotal.contains(loc)) {
                return false;
            }
        }
        
        return true;
    }
}
