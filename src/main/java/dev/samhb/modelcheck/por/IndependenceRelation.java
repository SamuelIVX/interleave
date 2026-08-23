package dev.samhb.modelcheck.por;

import dev.samhb.modelcheck.core.Step;
import java.util.*;

public final class IndependenceRelation {
    public boolean areIndependent(Step a, Step b) {
        Set<dev.samhb.modelcheck.core.MemoryLocation> aReads = a.reads();
        Set<dev.samhb.modelcheck.core.MemoryLocation> aWrites = a.writes();
        Set<dev.samhb.modelcheck.core.MemoryLocation> bReads = b.reads();
        Set<dev.samhb.modelcheck.core.MemoryLocation> bWrites = b.writes();
        
        Set<dev.samhb.modelcheck.core.MemoryLocation> aTotal = new HashSet<>();
        aTotal.addAll(aReads);
        aTotal.addAll(aWrites);
        
        Set<dev.samhb.modelcheck.core.MemoryLocation> bTotal = new HashSet<>();
        bTotal.addAll(bReads);
        bTotal.addAll(bWrites);
        
        for (dev.samhb.modelcheck.core.MemoryLocation loc : aTotal) {
            if (bTotal.contains(loc)) {
                return false;
            }
        }
        
        return true;
    }
}
