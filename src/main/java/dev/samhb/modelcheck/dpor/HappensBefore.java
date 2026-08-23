package dev.samhb.modelcheck.dpor;

import dev.samhb.modelcheck.core.*;
import java.util.*;

public final class HappensBefore {
    private final Map<Integer, Set<Integer>> edges;
    private final Map<Integer, Integer> threadLocalCounters;

    public HappensBefore() {
        this.edges = new LinkedHashMap<>();
        this.threadLocalCounters = new LinkedHashMap<>();
    }

    public void record(int sourceThreadId, int targetThreadId) {
        edges.computeIfAbsent(sourceThreadId, k -> new LinkedHashSet<>()).add(targetThreadId);
        threadLocalCounters.merge(sourceThreadId, 1, Integer::sum);
    }

    public boolean happensBefore(int sourceThreadId, int targetThreadId) {
        if (sourceThreadId == targetThreadId) {
            return true;
        }
        Set<Integer> direct = edges.get(sourceThreadId);
        return direct != null && direct.contains(targetThreadId);
    }

    public int getThreadLocalCounter(int threadId) {
        return threadLocalCounters.getOrDefault(threadId, 0);
    }

    public HappensBefore copy() {
        HappensBefore copy = new HappensBefore();
        for (Map.Entry<Integer, Set<Integer>> entry : edges.entrySet()) {
            copy.edges.put(entry.getKey(), new LinkedHashSet<>(entry.getValue()));
        }
        for (Map.Entry<Integer, Integer> entry : threadLocalCounters.entrySet()) {
            copy.threadLocalCounters.put(entry.getKey(), entry.getValue());
        }
        return copy;
    }
}
