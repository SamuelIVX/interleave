/* Internal orbit-value keys and reversible owner mappings for explicitly supplied thread groups. */
package dev.samhb.interleave.state;

import dev.samhb.interleave.format.dsl.DynamicState;
import dev.samhb.interleave.format.dsl.LocalDecl;
import java.util.*;
import java.util.stream.IntStream;

/**
 * Canonicalizes whole (PC, local-frame, scheduling-context) tuples within disjoint owner groups.
 * This value primitive does not certify behavioral symmetry or enable search pruning. Before a
 * store uses it, its caller must establish program/property equivariance for the supplied groups.
 * Keys are opaque and scoped to one program and normalized partition, not persistent wire IDs.
 */
final class ThreadSymmetryCanonicalizer {
    /** Current bounded-source owner capacity; this utility does not raise model limits. */
    private static final int MAX_THREADS = 8;
    /** Number of owner slots in every accepted position. */
    private final int threadCount;
    /** Sorted, disjoint nontrivial groups; omitted/singleton owners remain fixed. */
    private final List<List<Integer>> groups;

    /**
     * Freezes a mathematical owner partition; it makes no claim about program interchangeability.
     * @param threadCount number of owners, from one through eight
     * @param groups nonempty disjoint groups; omitted/singleton owners are fixed points
     * @throws NullPointerException for a null collection, group or owner
     * @throws IllegalArgumentException for invalid capacity, empty groups or duplicate/invalid owners
     */
    ThreadSymmetryCanonicalizer(int threadCount, List<List<Integer>> groups) {
        if (threadCount < 1 || threadCount > MAX_THREADS) throw new IllegalArgumentException("Expected 1 through 8 owners");
        Objects.requireNonNull(groups, "groups");
        this.threadCount = threadCount;
        boolean[] seen = new boolean[threadCount];
        List<List<Integer>> normalized = new ArrayList<>();
        for (List<Integer> group : groups) {
            List<Integer> owners = new ArrayList<>(Objects.requireNonNull(group, "group"));
            if (owners.isEmpty()) throw new IllegalArgumentException("Owner groups must not be empty");
            for (Integer owner : owners) {
                Objects.requireNonNull(owner, "owner");
                if (owner < 0 || owner >= threadCount || seen[owner]) throw new IllegalArgumentException("Invalid or duplicate owner");
                seen[owner] = true;
            }
            owners.sort(Integer::compare);
            if (owners.size() > 1) normalized.add(List.copyOf(owners));
        }
        normalized.sort(Comparator.comparingInt(List::getFirst));
        this.groups = List.copyOf(normalized);
    }

    /**
     * Computes a plain orbit-value key without mutating modeled state or caller collections.
     * Inputs must remain unchanged during this call. Each PC stays paired with its full local row.
     * @param state validated declarative state with this partition's owner count
     * @param programCounters matching nonnegative positions
     * @return immutable key and original/canonical mapping witness
     * @throws NullPointerException for null state, counter collection or counter element
     * @throws IllegalArgumentException for mismatched owner counts or negative positions
     */
    CanonicalPosition canonicalize(DynamicState state, List<Integer> programCounters) {
        return canonicalizePosition(state, programCounters, null);
    }

    /**
     * Computes a scheduling-context orbit key, mapping the last owner with the complete frames.
     * A last owner of -1 is the initial sentinel and differs from the plain-key context.
     * @param state validated declarative state, not concurrently mutated
     * @param programCounters matching nonnegative positions
     * @param lastThreadId original last owner, or -1 before any choice
     * @return immutable key and original/canonical mapping witness
     * @throws NullPointerException for null state, counter collection or counter element
     * @throws IllegalArgumentException for mismatched counts, negative PCs or invalid last owner
     */
    CanonicalPosition canonicalize(DynamicState state, List<Integer> programCounters, int lastThreadId) {
        return canonicalizePosition(state, programCounters, lastThreadId);
    }

    /** Validates and canonically orders complete frames, preserving fixed shared values.
     * @param state caller-owned modeled values, read only
     * @param programCounters ordered original PCs
     * @param lastThreadId null for plain keys, -1 for initial scheduling context, otherwise an owner
     * @return key whose mapping identifies each original frame at its canonical position
     */
    private CanonicalPosition canonicalizePosition(DynamicState state, List<Integer> programCounters, Integer lastThreadId) {
        Objects.requireNonNull(state, "state");
        List<Integer> pcs = List.copyOf(Objects.requireNonNull(programCounters, "programCounters"));
        if (state.threadCount() != threadCount || pcs.size() != threadCount || pcs.stream().anyMatch(pc -> pc < 0)) {
            throw new IllegalArgumentException("State and nonnegative PCs must match the owner partition");
        }
        if (lastThreadId != null && (lastThreadId < -1 || lastThreadId >= threadCount)) {
            throw new IllegalArgumentException("Invalid last owner");
        }
        List<Integer> toOriginal = new ArrayList<>(IntStream.range(0, threadCount).boxed().toList());
        for (List<Integer> group : groups) {
            List<Integer> ordered = new ArrayList<>(group);
            ordered.sort((left, right) -> compareFrames(state, pcs, lastThreadId, left, right));
            for (int i = 0; i < group.size(); i++) toOriginal.set(group.get(i), ordered.get(i));
        }
        DynamicState canonical = (DynamicState) state.deepCopy();
        List<Integer> orderedPcs = new ArrayList<>();
        for (int owner = 0; owner < threadCount; owner++) {
            int original = toOriginal.get(owner);
            orderedPcs.add(pcs.get(original));
            copyLocals(state, original, canonical, owner);
        }
        String context = lastThreadId == null ? "unbounded"
            : Integer.toString(lastThreadId == -1 ? -1 : toOriginal.indexOf(lastThreadId));
        // Integer-list text, Base64 and fixed/numeric context cannot contain the | delimiter.
        String key = "thread-orbit-v1|" + groups + "|"
            + Base64.getEncoder().encodeToString(new CanonicalEncoder().encode(canonical)) + "|" + orderedPcs + "|" + context;
        return new CanonicalPosition(key, toOriginal);
    }

    /** Compares whole frames under a total value order; original IDs break equal-frame ties only.
     * @param state immutable-during-call values
     * @param pcs original PCs
     * @param last null/plain, -1/initial or an original last owner
     * @param left first original owner
     * @param right second original owner
     * @return negative, zero or positive according to the complete frame order
     */
    private static int compareFrames(DynamicState state, List<Integer> pcs, Integer last, int left, int right) {
        int order = Integer.compare(pcs.get(left), pcs.get(right));
        if (order != 0) return order;
        for (LocalDecl local : state.decl().locals()) {
            order = switch (local.type()) {
                case INT -> Integer.compare(state.getLocalInt(left, local.name()), state.getLocalInt(right, local.name()));
                case BOOL -> Boolean.compare(state.getLocalBool(left, local.name()), state.getLocalBool(right, local.name()));
                case INT_ARRAY -> Arrays.compare(state.getLocalArray(left, local.name()), state.getLocalArray(right, local.name()));
            };
            if (order != 0) return order;
        }
        order = Boolean.compare(last != null && left == last, last != null && right == last);
        return order != 0 ? order : Integer.compare(left, right);
    }

    /** Copies one complete local row from original values into an independent state copy.
     * @param original read-only original state, never the destination
     * @param from original owner
     * @param canonical isolated state retaining all shared values/declarations
     * @param to canonical owner position
     */
    private static void copyLocals(DynamicState original, int from, DynamicState canonical, int to) {
        for (LocalDecl local : original.decl().locals()) {
            switch (local.type()) {
                case INT -> canonical.setLocalInt(to, local.name(), original.getLocalInt(from, local.name()));
                case BOOL -> canonical.setLocalBool(to, local.name(), original.getLocalBool(from, local.name()));
                case INT_ARRAY -> {
                    int[] values = original.getLocalArray(from, local.name());
                    for (int i = 0; i < values.length; i++) canonical.setLocalArrayElement(to, local.name(), i, values[i]);
                }
            }
        }
    }

    /** Immutable canonical key and checked bijection back to the original modeled owners. */
    static final class CanonicalPosition {
        /** Opaque orbit-value key; no original input object is retained. */
        private final String key;
        /** Original owner occupying each canonical position; a full permutation. */
        private final List<Integer> toOriginal;

        /** Freezes a key and the permutation assembled by the canonicalizer.
         * @param key canonical value/context key
         * @param toOriginal complete validated owner permutation
         */
        private CanonicalPosition(String key, List<Integer> toOriginal) {
            this.key = key;
            this.toOriginal = List.copyOf(toOriginal);
        }

        /** Returns the opaque key for this position within one program/partition.
         * @return immutable canonical value/context key
         */
        String key() { return key; }

        /** Returns the original owner at every canonical position.
         * @return immutable complete owner permutation
         */
        List<Integer> canonicalToOriginal() { return toOriginal; }

        /** Maps a canonical position back to its original modeled owner.
         * @param canonicalThreadId canonical position in the partition's owner domain
         * @return original modeled owner
         * @throws IndexOutOfBoundsException for an invalid canonical position
         */
        int originalThreadId(int canonicalThreadId) { return toOriginal.get(canonicalThreadId); }

        /** Maps an original modeled owner to its canonical position.
         * @param originalThreadId original owner in the partition's domain
         * @return inverse canonical owner position
         * @throws IndexOutOfBoundsException for an invalid original owner
         */
        int canonicalThreadId(int originalThreadId) {
            Objects.checkIndex(originalThreadId, toOriginal.size());
            return toOriginal.indexOf(originalThreadId);
        }
    }
}
