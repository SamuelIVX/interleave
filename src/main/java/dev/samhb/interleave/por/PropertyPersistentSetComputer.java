/** Selects invisible components closed over all remaining thread accesses for state-only properties. */
package dev.samhb.interleave.por;

import dev.samhb.interleave.core.Configuration;
import dev.samhb.interleave.core.MemoryLocation;
import dev.samhb.interleave.core.ModelThread;
import dev.samhb.interleave.core.Step;
import java.util.*;

/** Run-local conservative persistence analysis; execution/progress is checked by the explorer. */
final class PropertyPersistentSetComputer {
    private final IndependenceRelation relation;
    private final Set<MemoryLocation> observations;
    private final List<ThreadAccesses> accesses;

    /**
     * Snapshots stable footprints and their suffix unions once for this exploration.
     * @param threads finite linear model threads
     * @param observations complete state-only property observations
     * @param relation alias-aware footprint comparison
     */
    PropertyPersistentSetComputer(List<ModelThread> threads, Set<MemoryLocation> observations,
                                  IndependenceRelation relation) {
        this.relation = relation;
        this.observations = Set.copyOf(observations);
        List<ThreadAccesses> snapshots = new ArrayList<>();
        for (ModelThread thread : threads) {
            List<Footprint> steps = new ArrayList<>();
            for (Step step : thread.steps()) {
                steps.add(new Footprint(Set.copyOf(step.reads()), Set.copyOf(step.writes())));
            }
            List<Footprint> suffixes = new ArrayList<>(Collections.nCopies(steps.size() + 1,
                new Footprint(Set.of(), Set.of())));
            for (int pc = steps.size() - 1; pc >= 0; pc--) {
                Set<MemoryLocation> reads = new HashSet<>(suffixes.get(pc + 1).reads());
                Set<MemoryLocation> writes = new HashSet<>(suffixes.get(pc + 1).writes());
                reads.addAll(steps.get(pc).reads());
                writes.addAll(steps.get(pc).writes());
                suffixes.set(pc, new Footprint(Set.copyOf(reads), Set.copyOf(writes)));
            }
            snapshots.add(new ThreadAccesses(List.copyOf(steps), List.copyOf(suffixes)));
        }
        this.accesses = List.copyOf(snapshots);
    }

    /**
     * Returns the smallest enabled, invisible component, or every enabled thread.
     *
     * <p>Disabled and future accesses participate in the graph. A component's current actions
     * cannot interfere with any outside continuation, establishing path-level persistence.
     * Equal-size candidates are ordered by their sorted thread IDs. Progress and assertion
     * outcomes are not inferred from footprints; the explorer validates actual executions.
     *
     * @param config current modeled position
     * @return sorted selected IDs, possibly the entire enabled set or empty at deadlock
     */
    List<Integer> compute(Configuration config) {
        List<Integer> enabled = config.enabledThreadIds().stream().sorted().toList();
        if (enabled.size() <= 1) return enabled;
        Set<Integer> enabledIds = new HashSet<>(enabled);
        boolean[] seen = new boolean[accesses.size()];
        List<Integer> best = enabled;
        for (int seed = 0; seed < accesses.size(); seed++) {
            if (seen[seed] || !live(config, seed)) continue;
            List<Integer> component = component(config, seed, seen);
            if (component.size() >= best.size() || !enabledIds.containsAll(component)) continue;
            boolean invisible = true;
            for (int id : component) {
                Footprint next = accesses.get(id).steps().get(config.programCounters().get(id));
                if (relation.writesOverlap(next.writes(), observations)) {
                    invisible = false;
                    break;
                }
            }
            if (invisible) best = component;
        }
        return List.copyOf(best);
    }

    /**
     * Finds one connected component using suffix footprints, including disabled live threads.
     * @param config current counters
     * @param seed first component member
     * @param seen members already assigned to a component; updated in place
     * @return component sorted by thread ID
     */
    private List<Integer> component(Configuration config, int seed, boolean[] seen) {
        List<Integer> component = new ArrayList<>();
        ArrayDeque<Integer> pending = new ArrayDeque<>();
        seen[seed] = true;
        pending.add(seed);
        while (!pending.isEmpty()) {
            int id = pending.removeFirst();
            component.add(id);
            Footprint a = accesses.get(id).suffixes().get(config.programCounters().get(id));
            for (int other = 0; other < accesses.size(); other++) {
                if (seen[other] || !live(config, other)) continue;
                Footprint b = accesses.get(other).suffixes().get(config.programCounters().get(other));
                if (!relation.areIndependent(a.reads(), a.writes(), b.reads(), b.writes())) {
                    seen[other] = true;
                    pending.add(other);
                }
            }
        }
        component.sort(Integer::compareTo);
        return component;
    }

    /** Checks whether this counter still points into a finite step list. */
    private boolean live(Configuration config, int id) {
        return config.programCounters().get(id) < accesses.get(id).steps().size();
    }

    private record Footprint(Set<MemoryLocation> reads, Set<MemoryLocation> writes) {}
    private record ThreadAccesses(List<Footprint> steps, List<Footprint> suffixes) {}
}
