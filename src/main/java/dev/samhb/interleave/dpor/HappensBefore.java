/** Recorded thread-ordering edges and source counters used by dynamic POR. */
package dev.samhb.interleave.dpor;

import dev.samhb.interleave.core.*;
import java.util.*;

/** Recorded thread-ordering edges and source counters used by dynamic POR. */
public final class HappensBefore {
    /** Edges. */
    private final Map<Integer, Map<Integer, Step>> edges;
    /** Edge pcs. */
    private final Map<Integer, Map<Integer, Integer>> edgePcs;
    /** Thread local counters. */
    private final Map<Integer, Integer> threadLocalCounters;

    /** Creates happens before from the supplied values. */
    public HappensBefore() {
        this.edges = new LinkedHashMap<>();
        this.edgePcs = new LinkedHashMap<>();
        this.threadLocalCounters = new LinkedHashMap<>();
    }

    /**
     * Records an ordering edge and the source transition that established it.
     * @param sourceThreadId thread at the source of the ordering edge
     * @param targetThreadId thread at the target of the ordering edge
     * @param sourceStep step that established the ordering edge
     * @param sourcePc source program counter when the edge was recorded
     */
    public void record(int sourceThreadId, int targetThreadId, Step sourceStep, int sourcePc) {
        // Only record the first (earliest) PC for each edge pair
        edges.computeIfAbsent(sourceThreadId, k -> new LinkedHashMap<>())
             .putIfAbsent(targetThreadId, sourceStep);
        edgePcs.computeIfAbsent(sourceThreadId, k -> new LinkedHashMap<>())
               .putIfAbsent(targetThreadId, sourcePc);
        threadLocalCounters.merge(sourceThreadId, 1, Integer::sum);
    }

    /**
     * Reports whether the ordering graph connects the source thread to the target.
     * @param sourceThreadId thread at the source of the ordering edge
     * @param targetThreadId thread at the target of the ordering edge
     * @return true for the same thread or a path of recorded ordering edges
     */
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

    /**
     * Returns source threads with a directly recorded edge to the supplied thread.
     * @param threadId zero-based modeled thread ID
     * @return source IDs of directly recorded edges ending at the supplied thread
     */
    public Set<Integer> getThreadsThatHappenBefore(int threadId) {
        Set<Integer> result = new LinkedHashSet<>();
        for (Map.Entry<Integer, Map<Integer, Step>> entry : edges.entrySet()) {
            if (entry.getValue().containsKey(threadId) && happensBefore(entry.getKey(), threadId)) {
                result.add(entry.getKey());
            }
        }
        return result;
    }

    /**
     * Returns the step recorded for this ordering edge, or null when absent.
     * @param sourceThreadId thread at the source of the ordering edge
     * @param targetThreadId thread at the target of the ordering edge
     * @return recorded source step, or null for an absent edge
     */
    public Step getStep(int sourceThreadId, int targetThreadId) {
        Map<Integer, Step> targets = edges.get(sourceThreadId);
        if (targets == null) {
            return null;
        }
        return targets.get(targetThreadId);
    }

    /**
     * Returns the source counter recorded on this edge, or the absent-edge sentinel.
     * @param sourceThreadId thread at the source of the ordering edge
     * @param targetThreadId thread at the target of the ordering edge
     * @return earliest source counter on this edge, or -1 for an absent edge
     */
    public int getPcAtRecord(int sourceThreadId, int targetThreadId) {
        Map<Integer, Integer> targets = edgePcs.get(sourceThreadId);
        if (targets == null) {
            return -1;
        }
        Integer pc = targets.get(targetThreadId);
        return pc == null ? -1 : pc;
    }

    /**
     * Returns the number of ordering events recorded for the supplied thread.
     * @param threadId zero-based modeled thread ID
     * @return recorded event count for this thread, or zero before any event
     */
    public int getThreadLocalCounter(int threadId) {
        return threadLocalCounters.getOrDefault(threadId, 0);
    }

    /**
     * Returns copy for this happens before.
     * @return an independent copy of the run-local state
     */
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
