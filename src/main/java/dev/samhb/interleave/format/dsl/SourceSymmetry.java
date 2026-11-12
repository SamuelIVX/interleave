/** Conservative structural eligibility for trusted source and declarative permutation groups. */
package dev.samhb.interleave.format.dsl;

import dev.samhb.interleave.core.*;
import dev.samhb.interleave.search.Invariant;
import java.util.*;

/** Discovers interchangeable validated owners without trusting arbitrary step/property callbacks. */
public final class SourceSymmetry {
    /** Prevents instances of this stateless eligibility utility. */
    private SourceSymmetry() {}

    /**
     * Derives disjoint groups with identical owner-independent instructions and initial locals.
     * Only compiler-built source or loader-certified declarative semantics and shared properties qualify.
     * Non-grouped owners remain fixed, including owners whose instructions observe their own tid.
     * The returned partition certifies the supplied immutable program/property pair only.
     * @param program trusted program to validate; callbacks/definitions must remain unchanged
     * @param invariant absent or matching trusted shared property actually used for the run
     * @return immutable groups of at least two owners, ordered by first owner
     * @throws NullPointerException for a null program
     * @throws IllegalArgumentException for unsupported semantics, invalid bindings or no valid group
     */
    public static List<List<Integer>> groups(Program program, Invariant invariant) {
        Objects.requireNonNull(program, "program");
        if (program.initialStateType() != DynamicState.class) {
            throw new IllegalArgumentException("Symmetry requires a trusted DynamicState initial snapshot");
        }
        boolean source = program.isSource();
        if (invariant != null && (source ? !(invariant instanceof SourceProperty) : !(invariant instanceof DslInvariant))) {
            throw new IllegalArgumentException("Symmetry requires a trusted shared-state property; custom callbacks are unsupported");
        }
        if (program.threadCount() > 8) {
            throw new IllegalArgumentException("Symmetry requires matching owner counts from 1 through 8");
        }
        // Check all callbacks and owner bindings before initial enabledness can invoke any step.
        List<List<Object>> shapes = new ArrayList<>();
        StateDecl declaration = null;
        int nodes = 0;
        for (int owner = 0; owner < program.threadCount(); owner++) {
            ModelThread thread = program.threads().get(owner);
            if (thread.id() != owner) throw new IllegalArgumentException("Symmetry requires dense owner IDs");
            if (!source && (thread.steps().isEmpty() || thread.steps().size() > 64)) {
                throw new IllegalArgumentException("Declarative symmetry requires 1 through 64 steps per owner");
            }
            List<Object> instructions = new ArrayList<>();
            boolean eligible = true;
            for (int pc = 0; pc < thread.steps().size(); pc++) {
                Step action = thread.steps().get(pc);
                Object shape;
                if (source && action instanceof SourceInstruction instruction) {
                    shape = instruction.symmetryShape(owner, pc, thread.steps().size());
                } else if (!source && action instanceof DynamicStep instruction) {
                    if (declaration == null) declaration = instruction.declaration();
                    shape = instruction.symmetryShape(owner, declaration);
                    nodes += instruction.symmetryNodes();
                    if (nodes > 5000) throw new IllegalArgumentException("Declarative symmetry exceeds 5000 AST nodes");
                } else {
                    throw new IllegalArgumentException("Symmetry requires a single trusted instruction family in every owner");
                }
                eligible &= shape != null;
                instructions.add(shape);
            }
            shapes.add(eligible ? List.copyOf(instructions) : null);
        }
        if (!source && invariant instanceof DslInvariant property && nodes + property.symmetryNodes(declaration) > 5000) {
            throw new IllegalArgumentException("Declarative symmetry exceeds 5000 AST nodes including its property");
        }
        DynamicState state = (DynamicState) program.initialConfiguration().state();
        if (state.threadCount() != program.threadCount() || (!source && state.decl() != declaration)) {
            throw new IllegalArgumentException("Symmetry requires matching dynamic-state owner/declaration bindings");
        }
        Map<ThreadShape, List<Integer>> candidates = new LinkedHashMap<>();
        for (int owner = 0; owner < program.threadCount(); owner++) {
            if (shapes.get(owner) != null) {
                ThreadShape shape = new ThreadShape(shapes.get(owner), initialLocals(state, owner));
                candidates.computeIfAbsent(shape, ignored -> new ArrayList<>()).add(owner);
            }
        }
        List<List<Integer>> groups = candidates.values().stream().filter(group -> group.size() > 1).map(List::copyOf).toList();
        if (groups.isEmpty()) throw new IllegalArgumentException("Symmetry found no interchangeable thread group");
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
