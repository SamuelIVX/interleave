/* Conservative structural eligibility for source-thread permutation groups. */
package dev.samhb.interleave.format.dsl;

import dev.samhb.interleave.core.*;
import dev.samhb.interleave.search.Invariant;
import java.util.*;

/** Discovers interchangeable source owners without trusting arbitrary step/property callbacks. */
public final class SourceSymmetry {
    /** Prevents instances of this stateless eligibility utility. */
    private SourceSymmetry() {}

    /**
     * Derives disjoint groups with identical owner-independent instructions and initial locals.
     * Only compiler-built source instructions and absent/compiler-built shared properties qualify.
     * Non-grouped owners remain fixed, including owners whose instructions observe their own tid.
     * The returned partition certifies the supplied immutable program/property pair only.
     * @param program source program to validate; callbacks/definitions must remain unchanged
     * @param invariant absent or compiler-built source property actually used for the run
     * @return immutable groups of at least two owners, ordered by first owner
     * @throws NullPointerException for a null program
     * @throws IllegalArgumentException for unsupported semantics, invalid bindings or no valid group
     */
    public static List<List<Integer>> groups(Program program, Invariant invariant) {
        Objects.requireNonNull(program, "program");
        if (!program.isSource() || (invariant != null && !(invariant instanceof SourceProperty))) {
            throw new IllegalArgumentException("Symmetry requires compiled source and its shared-state property; custom callbacks are unsupported");
        }
        if (program.threadCount() > 8) {
            throw new IllegalArgumentException("Symmetry requires matching source owner counts from 1 through 8");
        }
        // Check all callbacks and owner bindings before initial enabledness can invoke any step.
        List<List<Object>> shapes = new ArrayList<>();
        for (int owner = 0; owner < program.threadCount(); owner++) {
            ModelThread thread = program.threads().get(owner);
            if (thread.id() != owner) throw new IllegalArgumentException("Symmetry requires dense source owner IDs");
            List<Object> instructions = new ArrayList<>();
            boolean eligible = true;
            for (int pc = 0; pc < thread.steps().size(); pc++) {
                if (!(thread.steps().get(pc) instanceof SourceInstruction instruction)) {
                    throw new IllegalArgumentException("Symmetry requires compiler-built source instructions in every owner");
                }
                Object shape = instruction.symmetryShape(owner, pc, thread.steps().size());
                eligible &= shape != null;
                instructions.add(shape);
            }
            shapes.add(eligible ? List.copyOf(instructions) : null);
        }
        if (!(program.initialConfiguration().state() instanceof DynamicState state)
                || state.threadCount() != program.threadCount()) {
            throw new IllegalArgumentException("Symmetry requires matching dynamic source-state owner counts");
        }
        Map<ThreadShape, List<Integer>> candidates = new LinkedHashMap<>();
        for (int owner = 0; owner < program.threadCount(); owner++) {
            if (shapes.get(owner) != null) {
                ThreadShape shape = new ThreadShape(shapes.get(owner), initialLocals(state, owner));
                candidates.computeIfAbsent(shape, ignored -> new ArrayList<>()).add(owner);
            }
        }
        List<List<Integer>> groups = candidates.values().stream().filter(group -> group.size() > 1).map(List::copyOf).toList();
        if (groups.isEmpty()) throw new IllegalArgumentException("Symmetry found no interchangeable source-thread group");
        return groups;
    }

    /** Snapshots typed initial local values; arrays use element equality rather than object identity.
     * @param state independent initial dynamic state
     * @param owner original owner whose initial frame is read
     * @return immutable value tuple in declaration order
     */
    private static List<Object> initialLocals(DynamicState state, int owner) {
        List<Object> values = new ArrayList<>();
        for (LocalDecl local : state.decl().locals()) {
            values.add(switch (local.type()) {
                case INT -> state.getLocalInt(owner, local.name());
                case BOOL -> state.getLocalBool(owner, local.name());
                case INT_ARRAY -> Arrays.stream(state.getLocalArray(owner, local.name())).boxed().toList();
            });
        }
        return List.copyOf(values);
    }

    /** Structural execution shape plus invariant initial local values.
     * @param instructions immutable metadata-free instruction values
     * @param locals immutable typed initial local values
     */
    private record ThreadShape(List<Object> instructions, List<Object> locals) {}
}
