/** Immutable source certification and owner/action mapping shared by checked symmetry stores. */
package dev.samhb.interleave.search;

import dev.samhb.interleave.Strategy;
import dev.samhb.interleave.core.*;
import dev.samhb.interleave.format.dsl.DynamicState;
import dev.samhb.interleave.format.dsl.SourceSymmetry;
import dev.samhb.interleave.state.ThreadSymmetryCanonicalizer;
import java.util.*;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;

/** Program-sized metadata only; contains no visited positions or mutable membership. */
final class SymmetryRunContext {
    /** Exact program bound to this capability. */
    private final Program program;
    /** Exact compiler property object, or null. */
    private final Invariant invariant;
    /** Bound built-in algorithm. */
    private final Strategy strategy;
    /** Stateless whole-frame value and owner mapping. */
    private final ThreadSymmetryCanonicalizer canonicalizer;
    /** Read-only-after-construction first-occurrence indices for DPOR actions. */
    private final List<IdentityHashMap<Step, Integer>> occurrences = new ArrayList<>();

    /** Certifies source groups and retains only immutable run/mapping metadata.
     * @param program immutable trusted source program
     * @param invariant compiler property, or null
     * @param strategy built-in bound algorithm
     * @throws NullPointerException if program or strategy is null
     * @throws IllegalArgumentException if source/property or DPOR alias layout is unsupported
     */
    SymmetryRunContext(Program program, Invariant invariant, Strategy strategy) {
        Objects.requireNonNull(strategy, "strategy");
        var groups = SourceSymmetry.groups(program, invariant);
        this.program = program;
        this.invariant = invariant;
        this.strategy = strategy;
        canonicalizer = new ThreadSymmetryCanonicalizer(program.threadCount(), groups);
        if (strategy == Strategy.DPOR) {
            var partitions = new ArrayList<List<Integer>>();
            for (var thread : program.threads()) {
                var indices = new IdentityHashMap<Step, Integer>();
                var partition = new ArrayList<Integer>();
                for (int pc = 0; pc < thread.steps().size(); pc++) {
                    Step action = thread.steps().get(pc);
                    indices.putIfAbsent(action, pc);
                    partition.add(indices.get(action));
                }
                occurrences.add(indices);
                partitions.add(List.copyOf(partition));
            }
            for (var group : groups) {
                for (int owner : group) {
                    if (!partitions.get(group.getFirst()).equals(partitions.get(owner))) {
                        throw new IllegalArgumentException("Symmetry DPOR requires matching instruction-alias partitions within each group");
                    }
                }
            }
        }
    }

    /** Rejects foreign program, property or strategy identities before traversal.
     * @param program requested program object
     * @param invariant requested property object, or null
     * @param strategy requested algorithm
     * @throws IllegalArgumentException if any bound identity differs
     */
    void requireRun(Program program, Invariant invariant, Strategy strategy) {
        if (this.program != program || this.invariant != invariant || this.strategy != strategy) {
            throw new IllegalArgumentException("Symmetry store belongs to a different program, property or strategy");
        }
    }

    /** Returns the complete orbit and existing deterministic permutation witness.
     * @param config original source configuration
     * @return fresh immutable canonical position
     * @throws ClassCastException if state is not DynamicState
     */
    ThreadSymmetryCanonicalizer.CanonicalPosition position(Configuration config) {
        return canonicalizer.canonicalize((DynamicState) config.state(), config.programCounters());
    }

    /** Encodes a complete orbit using version 1, VALUE domain 1 and a big-endian UTF-8 length.
     * @param config original source configuration
     * @return fresh domain-separated bytes compatible with DataOutputStream field order
     */
    byte[] valueBytes(Configuration config) {
        byte[] orbit = position(config).key().getBytes(StandardCharsets.UTF_8);
        return ByteBuffer.allocate(6 + orbit.length).put((byte) 1).put((byte) 1)
                .putInt(orbit.length).put(orbit).array();
    }

    /** Encodes one complete CBS product; the final four bytes hold its cost.
     * @param config original source configuration
     * @param lastThreadId actual last owner, or initial -1
     * @param cost nonnegative cost validated by the owning store
     * @return fresh version/domain/UTF-8 orbit/cost bytes in big-endian order
     * @throws IllegalArgumentException if last owner is invalid
     */
    byte[] costBytes(Configuration config, int lastThreadId, int cost) {
        byte[] orbit = costKey(config,lastThreadId).getBytes(StandardCharsets.UTF_8);
        return ByteBuffer.allocate(10 + orbit.length).put((byte)1).put((byte)2)
            .putInt(orbit.length).put(orbit).putInt(cost).array();
    }

    /** Maps last owner with the same frames for existing exact CBS membership.
     * @param config original source configuration
     * @param lastThreadId actual last owner, or initial -1
     * @return complete context-aware key
     * @throws IllegalArgumentException if last owner is invalid
     */
    String costKey(Configuration config, int lastThreadId) {
        return canonicalizer.canonicalize((DynamicState) config.state(), config.programCounters(), lastThreadId).key();
    }

    /** Freezes mapped DPOR sleepers using their first original instruction occurrence.
     * @param config actual original configuration
     * @param sleeping caller's normalized owner/action entries
     * @return immutable complete value/sleep context, retaining stale entries
     * @throws IllegalArgumentException for a non-DPOR binding or foreign owner/action
     */
    SleepContext sleepContext(Configuration config, Set<Map.Entry<Integer, Step>> sleeping) {
        if (strategy != Strategy.DPOR) throw new IllegalArgumentException("Sleep contexts require symmetry DPOR");
        var position = position(config);
        var tokens = new HashSet<SleepingAction>();
        for (var entry : sleeping) {
            int owner = entry.getKey();
            if (owner < 0 || owner >= occurrences.size()) throw new IllegalArgumentException("Invalid sleeping owner");
            Integer pc = occurrences.get(owner).get(entry.getValue());
            if (pc == null) throw new IllegalArgumentException("Foreign sleeping action");
            tokens.add(new SleepingAction(position.canonicalThreadId(owner), pc));
        }
        return new SleepContext(position.key(), Set.copyOf(tokens));
    }

    /** Mapped action identity without diagnostic metadata.
     * @param owner canonical owner
     * @param pc first original occurrence of its instruction object
     */
    record SleepingAction(int owner, int pc) {}

    /** Immutable existing exact coverage product.
     * @param key full value orbit
     * @param tokens immutable mapped sleepers
     */
    record SleepContext(String key, Set<SleepingAction> tokens) {}
}
