package dev.samhb.interleave.dpor;

import dev.samhb.interleave.core.Step;
import java.util.*;

public final class SleepSet {
    private final Map<Integer, Step> sleepingSteps;

    public SleepSet() {
        this.sleepingSteps = new LinkedHashMap<>();
    }

    public SleepSet(Map<Integer, Step> sleepingSteps) {
        this.sleepingSteps = new LinkedHashMap<>(sleepingSteps);
    }

    public boolean contains(int threadId, Step step) {
        Step sleeping = sleepingSteps.get(threadId);
        return sleeping != null && sleeping.equals(step);
    }

    public void add(int threadId, Step step) {
        sleepingSteps.put(threadId, step);
    }

    public SleepSet copy() {
        return new SleepSet(new LinkedHashMap<>(sleepingSteps));
    }

    public void remove(int threadId) {
        sleepingSteps.remove(threadId);
    }

    public void clear() {
        sleepingSteps.clear();
    }

    public Set<Map.Entry<Integer, Step>> entries() {
        return sleepingSteps.entrySet();
    }

    public int size() {
        return sleepingSteps.size();
    }
}
