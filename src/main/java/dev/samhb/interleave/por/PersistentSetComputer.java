package dev.samhb.interleave.por;

import dev.samhb.interleave.core.*;
import java.util.*;

public final class PersistentSetComputer {
    private final IndependenceRelation relation;

    public PersistentSetComputer(IndependenceRelation relation) {
        this.relation = relation;
    }

    public List<Integer> computePersistentSet(Configuration config, List<ModelThread> threads) {
        List<Integer> enabled = config.enabledThreadIds();
        if (enabled.size() <= 1) {
            return enabled;
        }
        
        List<Integer> persistent = new ArrayList<>();
        
        for (int threadId : enabled) {
            boolean dependent = false;
            ModelThread thread = threads.get(threadId);
            int pc = config.programCounters().get(threadId);
            Step step = thread.steps().get(pc);
            if (step == null) continue;
            
            for (int otherId : enabled) {
                if (otherId == threadId) continue;
                ModelThread other = threads.get(otherId);
                int otherPc = config.programCounters().get(otherId);
                Step otherStep = other.steps().get(otherPc);
                if (otherStep == null) continue;
                
                if (!relation.areIndependent(step, otherStep)) {
                    dependent = true;
                    break;
                }
            }
            
            if (dependent) {
                persistent.add(threadId);
            }
        }
        
        if (persistent.isEmpty()) {
            return List.of(enabled.get(0));
        }
        
        return persistent;
    }
}
