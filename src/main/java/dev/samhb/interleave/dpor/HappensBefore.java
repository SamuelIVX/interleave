package dev.samhb.interleave.dpor;

import dev.samhb.interleave.core.*;
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
        Set<Integer> visited = new LinkedHashSet<>();
        Deque<Integer> stack = new ArrayDeque<>();
        stack.push(sourceThreadId);
        while (!stack.isEmpty()) {
            int current = stack.pop();
            if (current == targetThreadId) {
                return true;
            }
            if (!visited.add(current)) {
                continue;
            }
            Set<Integer> next = edges.get(current);
            if (next != null) {
                for (int nextId : next) {
                    if (!visited.contains(nextId)) {
                        stack.push(nextId);
                    }
                }
            }
        }
        return false;
    }

    public Set<Integer> getThreadsThatHappenBefore(int threadId) {
        Set<Integer> result = new LinkedHashSet<>();
        for (Map.Entry<Integer, Set<Integer>> entry : edges.entrySet()) {
            if (entry.getValue().contains(threadId) && happensBefore(entry.getKey(), threadId)) {
                result.add(entry.getKey());
            }
        }
        return result;
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
