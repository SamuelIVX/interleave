package dev.samhb.interleave.dpor;

import dev.samhb.interleave.core.*;
import java.util.*;

public final class HappensBefore {
    private final Map<Integer, Map<Integer, Step>> edges;
    private final Map<Integer, Map<Integer, Integer>> edgePcs;
    private final Map<Integer, Integer> threadLocalCounters;

    public HappensBefore() {
        this.edges = new LinkedHashMap<>();
        this.edgePcs = new LinkedHashMap<>();
        this.threadLocalCounters = new LinkedHashMap<>();
    }

    public void record(int sourceThreadId, int targetThreadId, Step sourceStep, int sourcePc) {
        // Only record the first (earliest) PC for each edge pair
        edges.computeIfAbsent(sourceThreadId, k -> new LinkedHashMap<>())
             .putIfAbsent(targetThreadId, sourceStep);
        edgePcs.computeIfAbsent(sourceThreadId, k -> new LinkedHashMap<>())
               .putIfAbsent(targetThreadId, sourcePc);
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
            Map<Integer, Step> next = edges.get(current);
            if (next != null) {
                for (int nextId : next.keySet()) {
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
        for (Map.Entry<Integer, Map<Integer, Step>> entry : edges.entrySet()) {
            if (entry.getValue().containsKey(threadId) && happensBefore(entry.getKey(), threadId)) {
                result.add(entry.getKey());
            }
        }
        return result;
    }

    public Step getStep(int sourceThreadId, int targetThreadId) {
        Map<Integer, Step> targets = edges.get(sourceThreadId);
        if (targets == null) {
            return null;
        }
        return targets.get(targetThreadId);
    }

    public int getPcAtRecord(int sourceThreadId, int targetThreadId) {
        Map<Integer, Integer> targets = edgePcs.get(sourceThreadId);
        if (targets == null) {
            return -1;
        }
        Integer pc = targets.get(targetThreadId);
        return pc == null ? -1 : pc;
    }

    public int getThreadLocalCounter(int threadId) {
        return threadLocalCounters.getOrDefault(threadId, 0);
    }

    public HappensBefore copy() {
        HappensBefore copy = new HappensBefore();
        for (Map.Entry<Integer, Map<Integer, Step>> entry : edges.entrySet()) {
            copy.edges.put(entry.getKey(), new LinkedHashMap<>(entry.getValue()));
        }
        for (Map.Entry<Integer, Map<Integer, Integer>> entry : edgePcs.entrySet()) {
            copy.edgePcs.put(entry.getKey(), new LinkedHashMap<>(entry.getValue()));
        }
        for (Map.Entry<Integer, Integer> entry : threadLocalCounters.entrySet()) {
            copy.threadLocalCounters.put(entry.getKey(), entry.getValue());
        }
        return copy;
    }
}