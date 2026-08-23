package dev.samhb.modelcheck.dpor;

import dev.samhb.modelcheck.core.Step;
import java.util.*;

public final class SleepSet {
    private final Set<Step> sleepingSteps;

    public SleepSet() {
        this.sleepingSteps = new LinkedHashSet<>();
    }

    public SleepSet(Set<Step> sleepingSteps) {
        this.sleepingSteps = new LinkedHashSet<>(sleepingSteps);
    }

    public boolean contains(Step step) {
        return sleepingSteps.contains(step);
    }

    public void add(Step step) {
        sleepingSteps.add(step);
    }

    public SleepSet copy() {
        return new SleepSet(new LinkedHashSet<>(sleepingSteps));
    }

    public void remove(Step step) {
        sleepingSteps.remove(step);
    }

    public void clear() {
        sleepingSteps.clear();
    }
}
