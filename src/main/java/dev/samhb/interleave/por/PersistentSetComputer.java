package dev.samhb.interleave.por;

import dev.samhb.interleave.core.*;
import java.util.*;

/**
 * Computes persistent sets for static partial order reduction.
 *
 * <p>A persistent set for a configuration is a subset of enabled threads such that
 * executing any thread outside the set can be deferred without missing reachable
 * states. A thread is included if it conflicts with another enabled thread (via
 * read/write dependencies) or if another thread's execution could disable it
 * (enable-disable interference).</p>
 */
public final class PersistentSetComputer {
    private final IndependenceRelation relation;

    /**
     * Creates a persistent set computer with the given independence relation.
     *
     * @param relation the relation used to classify step pairs as independent or dependent
     */
    public PersistentSetComputer(IndependenceRelation relation) {
        this.relation = relation;
    }

    /**
     * Computes the persistent set for the given configuration.
     *
     * <p>For each enabled thread, checks whether it is dependent on any other
     * enabled thread via data conflicts ({@link IndependenceRelation#areIndependent})
     * or enable-disable interference ({@link IndependenceRelation#hasEnableDisableInterference}).
     * All dependent threads are included in the persistent set. If no threads are
     * dependent, a single arbitrary thread is returned (the amply set property).</p>
     *
     * @param config the current configuration
     * @param threads the list of model threads
     * @return the persistent set (non-empty subset of enabled thread IDs)
     */
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
                
                if (relation.hasEnableDisableInterference(config, otherId, threadId, threads)) {
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
