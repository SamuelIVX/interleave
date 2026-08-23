package dev.samhb.modelcheck.core;

import java.util.Collections;
import java.util.List;

public final class ModelThread {
    private final int id;
    private final List<Step> steps;
    private int pc;

    public ModelThread(int id, List<Step> steps) {
        if (steps == null) throw new IllegalArgumentException("steps must not be null");
        this.id = id;
        this.steps = List.copyOf(steps);
        this.pc = 0;
    }

    public int id() {
        return id;
    }

    public int pc() {
        return pc;
    }

    List<Step> steps() {
        return steps;
    }

    public boolean terminated() {
        return pc >= steps.size();
    }

    public Step nextStep() {
        if (terminated()) return null;
        return steps.get(pc);
    }

    public boolean enabled(SharedState state) {
        if (terminated()) return false;
        return nextStep().enabled(state);
    }

    ModelThread advance() {
        ModelThread next = new ModelThread(this.id, this.steps);
        next.pc = this.pc + 1;
        return next;
    }
}
