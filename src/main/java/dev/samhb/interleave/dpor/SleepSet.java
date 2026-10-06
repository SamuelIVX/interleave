/** Run-local transitions sleeping until dependence requires them to be explored. */
package dev.samhb.interleave.dpor;

import dev.samhb.interleave.por.IndependenceRelation;
import dev.samhb.interleave.core.Step;
import java.util.*;

/** Run-local transitions sleeping until dependence requires them to be explored. */
public final class SleepSet {
    /** Sleeping steps. */
    private final Map<Integer, Step> sleepingSteps;

    /** Creates sleep set from the supplied values. */
    public SleepSet() {
        this.sleepingSteps = new LinkedHashMap<>();
    }

    /**
     * Creates sleep set from the supplied values.
     * @param sleepingSteps sleeping transitions to copy
     */
    public SleepSet(Map<Integer, Step> sleepingSteps) {
        this.sleepingSteps = new LinkedHashMap<>(sleepingSteps);
    }

    /**
     * Reports whether this exact thread and transition are sleeping.
     * @param threadId zero-based modeled thread ID
     * @param step modeled transition to inspect
     * @return true if the thread has a sleeping transition equal to the supplied step
     */
    public boolean contains(int threadId, Step step) {
        Step sleeping = sleepingSteps.get(threadId);
        return sleeping != null && sleeping.equals(step);
    }

    /**
     * Marks this thread’s transition as sleeping.
     * @param threadId zero-based modeled thread ID
     * @param step modeled transition to inspect
     */
    public void add(int threadId, Step step) {
        sleepingSteps.put(threadId, step);
    }

    /**
     * Returns copy for this sleep set.
     * @return an independent copy of the run-local state
     */
    public SleepSet copy() {
        return new SleepSet(new LinkedHashMap<>(sleepingSteps));
    }

    /**
     * Copies sleeping entries that remain independent of the current transition.
     * @param relation independence relation used to filter sleeping steps
     * @param currentStep transition against which sleeping steps are checked
     * @return independent sleep set containing entries independent of the current step
     */
    public SleepSet copyFiltering(dev.samhb.interleave.por.IndependenceRelation relation, Step currentStep) {
        SleepSet filtered = new SleepSet();
        for (Map.Entry<Integer, Step> entry : sleepingSteps.entrySet()) {
            if (relation.areIndependent(currentStep, entry.getValue())) {
                filtered.sleepingSteps.put(entry.getKey(), entry.getValue());
            }
        }
        return filtered;
    }

    /**
     * Removes the sleeping entry for the supplied thread.
     * @param threadId zero-based modeled thread ID
     */
    public void remove(int threadId) {
        sleepingSteps.remove(threadId);
    }

    /** Removes every sleeping thread entry. */
    public void clear() {
        sleepingSteps.clear();
    }

    /**
     * Returns the live mutable view of sleeping thread-to-step entries.
     * @return backing-map entries; removals and entry updates change this sleep set
     */
    public Set<Map.Entry<Integer, Step>> entries() {
        return sleepingSteps.entrySet();
    }

    /**
     * Returns the number of sleeping entries.
     * @return number of sleeping entries
     */
    public int size() {
        return sleepingSteps.size();
    }
}
