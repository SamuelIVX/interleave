package dev.samhb.interleave.core;

import java.util.*;

public final class Program {
    private final SharedState initialState;
    private final List<ModelThread> threads;

    public Program(SharedState initialState, List<ModelThread> threads) {
        if (initialState == null) throw new IllegalArgumentException("initialState must not be null");
        if (threads == null || threads.isEmpty()) throw new IllegalArgumentException("threads must not be empty");
        this.initialState = initialState;
        this.threads = List.copyOf(threads);
    }

    public Configuration initialConfiguration() {
        return Configuration.initial(initialState, threads);
    }

    public int threadCount() {
        return threads.size();
    }

    public List<ModelThread> threads() {
        return threads;
    }
}
