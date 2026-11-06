/* Independent quotient counts, fail-closed eligibility, and concrete witness replay. */
package dev.samhb.interleave.search;

import dev.samhb.interleave.bugs.BenchmarkProgram;
import dev.samhb.interleave.core.*;
import dev.samhb.interleave.format.SourceProgramLoader;
import dev.samhb.interleave.format.dsl.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.*;
import static org.junit.jupiter.api.Assertions.*;

class SymmetryDfsExplorerTest {
    /** Actual quotient search must realize the independently counted mask layers.
     * @param owners number of identical source owners
     * @param concrete literal binary-mask count
     * @param quotient literal completed-owner-layer count
     */
    @ParameterizedTest @CsvSource({"4,16,5", "8,256,9"})
    void exploresCounterOrbitsWithOriginalWitnesses(int owners, int concrete, int quotient) {
        String threads = "";
        for (int owner = 0; owner < owners; owner++) threads += "thread " + owner + " { atomic { x = x + 1; } } ";
        var model = load("shared int x = 0; " + threads + " invariant final (x == " + owners + ");");
        Invariant invariant = model.invariant().orElseThrow();
        var ordinary = new DfsExplorer().explore(model.program(), invariant);
        var reduced = new SymmetryDfsExplorer().explore(model.program(), invariant);
        assertEquals(concrete, ordinary.statesExplored());
        assertEquals(quotient, reduced.statesExplored());
        assertEquals(Set.of(TraceOutcome.COMPLETED), outcomes(reduced));
        for (Trace trace : reduced.traces()) {
            var end = new TraceReplayer().replay(model.program(), trace, invariant);
            assertEquals(owners, ((DynamicState) end.state()).getInt("x"));
            assertEquals(owners, trace.length());
            assertEquals(owners, new HashSet<>(trace.threadIds()).size());
        }
        assertEquals(quotient, new SymmetryDfsExplorer().explore(model.program(), invariant).statesExplored());
    }

    /** Independent split-increment BFS gives 13 concrete states, eight orbits, final values {1,2}. */
    @Test void keepsLostUpdateOutcomesAndHelperSourceIdentity() {
        var model = load("shared int x = 0; local int r = 0; helper inc { atomic { local.r = x; } atomic { x = local.r + 1; } }"
            + " thread 0 { call inc; } thread 1 { call inc; }");
        assertEquals(13, new DfsExplorer().explore(model.program()).statesExplored());
        var reduced = new SymmetryDfsExplorer().explore(model.program(), null);
        assertEquals(8, reduced.statesExplored());
        Set<Integer> finalValues = new HashSet<>();
        for (Trace trace : reduced.traces()) {
            finalValues.add(((DynamicState) new TraceReplayer().replay(model.program(), trace, (s, c) -> true).state()).getInt("x"));
            assertEquals(4, trace.length());
            assertTrue(trace.evidence().executedSteps().stream().allMatch(step -> step.label().startsWith("call inc at ")));
        }
        assertEquals(Set.of(1, 2), finalValues);
    }

    /** Separate groups have 3*3 count layers; a fixed owner adds a binary factor to one group. */
    @Test void infersMixedGroupsAndRetainsFixedTidOwners() {
        var twoGroups = load("shared int x = 0; local bool flag = false; local int[] a = [0,0];"
            + "thread 0 { atomic { local.flag = true; local.a[1] = 1; x = x + 1; } }"
            + "thread 1 { atomic { x = x + 2; } }"
            + "thread 2 { atomic { local.flag = true; local.a[1] = 1; x = x + 1; } }"
            + "thread 3 { atomic { x = x + 2; } }");
        assertEquals(List.of(List.of(0, 2), List.of(1, 3)), SourceSymmetry.groups(twoGroups.program(), null));
        assertEquals(16, new DfsExplorer().explore(twoGroups.program()).statesExplored());
        assertEquals(9, new SymmetryDfsExplorer().explore(twoGroups.program(), null).statesExplored());
        var fixed = load("shared int x = 0; thread 0 { atomic { x = x + 2; } }"
            + "thread 1 { atomic { x = x + tid; } } thread 2 { atomic { x = x + 2; } }");
        assertEquals(List.of(List.of(0, 2)), SourceSymmetry.groups(fixed.program(), null));
        assertEquals(8, new DfsExplorer().explore(fixed.program()).statesExplored());
        var reduced = new SymmetryDfsExplorer().explore(fixed.program(), null);
        assertEquals(6, reduced.statesExplored());
        for (Trace trace : reduced.traces()) {
            assertEquals(5, ((DynamicState) new TraceReplayer().replay(fixed.program(), trace, (s,c) -> true).state()).getInt("x"));
        }
        assertThrows(UnsupportedOperationException.class, () -> SourceSymmetry.groups(fixed.program(), null).getFirst().add(1));
    }

    /** Identity observations reject even when hidden in nonexecuted branches or indices.
     * @param body identical but owner-sensitive instruction body
     */
    @ParameterizedTest @ValueSource(strings = {
        "atomic { x = tid; }", "atomic when (tid == 0) {}", "assert(tid == 0);",
        "atomic { if (false) { x = tid; } }", "atomic { x = items[tid]; }",
        "atomic { items[tid] = 1; }", "atomic { local.a[tid] = 1; }",
        "atomic { if (true) {} else { x = tid; } }", "atomic { x = -tid; }", "assert(true || tid == 0);", "if (tid == 0) { atomic {} }",
        "atomic { x = local.a[tid]; }", "assert(local.a[tid] == 0);"
    })
    void rejectsNestedIdentityObservations(String body) {
        var model = pair(body);
        assertThrows(IllegalArgumentException.class, () -> new SymmetryDfsExplorer().explore(model.program(), null));
    }

    /** Different control/effect values never qualify simply because their footprints agree.
     * @param other second owner's semantically different body
     */
    @ParameterizedTest @ValueSource(strings = {"atomic { x = 2; }", "atomic when (false) { x = 1; }",
        "assert(false);", "atomic { x = 1; } atomic {}", "if (true) { atomic { x = 1; } }"})
    void rejectsDifferentSemantics(String other) {
        var model = load("shared int x = 0; thread 0 { atomic { x = 1; } } thread 1 { " + other + " }");
        assertThrows(IllegalArgumentException.class, () -> SourceSymmetry.groups(model.program(), null));
    }

    /** A group must have identical initial whole local rows, including boolean and array values. */
    @Test void separatesInitialFramesAndRejectsUnknownCallbacksAndLegacy() {
        var model = pair("atomic { x = x + 1; }");
        for (String type : List.of("integer", "boolean", "array")) {
            var state = (DynamicState) model.program().initialConfiguration().state();
            if (type.equals("integer")) state.setLocalInt(1, "r", 1);
            if (type.equals("boolean")) state.setLocalBool(1, "flag", true);
            if (type.equals("array")) state.setLocalArrayElement(1, "a", 1, 1);
            var changed = new Program(state, model.program().threads(), model.program().sourceDigest());
            assertThrows(IllegalArgumentException.class, () -> SourceSymmetry.groups(changed, null));
        }
        assertThrows(IllegalArgumentException.class, () -> SourceSymmetry.groups(model.program(), (s,c) -> true));
        var legacy = new Program(model.program().initialConfiguration().state(), model.program().threads());
        assertThrows(IllegalArgumentException.class, () -> SourceSymmetry.groups(legacy, null));
        assertThrows(NullPointerException.class, () -> SourceSymmetry.groups(null, null));
    }

    /** Rebound owner instructions or truncated forward control cannot borrow source eligibility. */
    @Test void rejectsForgedOwnerBindingsTargetsAndCounts() {
        var model = pair("atomic {} atomic {}");
        var state = model.program().initialConfiguration().state();
        var wrongOwner = new Program(state, List.of(model.program().threads().getFirst(),
            new ModelThread(1, model.program().threads().getFirst().steps())), model.program().sourceDigest());
        assertThrows(IllegalArgumentException.class, () -> SourceSymmetry.groups(wrongOwner, null));
        var badTarget = new Program(state, List.of(new ModelThread(0, List.of(model.program().threads().get(0).steps().get(1))),
            new ModelThread(1, List.of(model.program().threads().get(1).steps().get(1)))), model.program().sourceDigest());
        assertThrows(IllegalArgumentException.class, () -> SourceSymmetry.groups(badTarget, null));
        var wrongIds = new Program(state, List.of(new ModelThread(1, model.program().threads().get(0).steps()),
            model.program().threads().get(1)), model.program().sourceDigest());
        assertThrows(IllegalArgumentException.class, () -> SourceSymmetry.groups(wrongIds, null));
        var mismatched = new Program(new DynamicState(((DynamicState) state).decl(), 3), model.program().threads(), model.program().sourceDigest());
        assertThrows(IllegalArgumentException.class, () -> SourceSymmetry.groups(mismatched, null));
    }

    /** Empty decisions and exhausted loops differ by kind; equal-position limits retain their bound. */
    @Test void rejectsControlKindAndLoopLimitDifferences() {
        var kinds = load("shared int x = 0; thread 0 { if (true) {} } thread 1 { while (true) limit 0 {} }");
        assertThrows(IllegalArgumentException.class, () -> SourceSymmetry.groups(kinds.program(), null));
        var limits = load("shared int x = 0; thread 0 { atomic {} while (true) limit 0 {} } thread 1 { while (true) limit 1 {} }");
        var atomic = pair("atomic {}");
        // Public Program composition can retain valid bindings/targets while selecting different LIMIT nodes.
        var recomposed = new Program(limits.program().initialConfiguration().state(), List.of(limits.program().threads().get(0),
            new ModelThread(1, List.of(atomic.program().threads().get(1).steps().get(0),
                limits.program().threads().get(1).steps().get(1)))), limits.program().sourceDigest());
        assertThrows(IllegalArgumentException.class, () -> SourceSymmetry.groups(recomposed, null));
    }

    /** Repositioned decisions must reject true and false self-targets independently. */
    @Test void rejectsBothSelfTargetBoundariesAndExcessOwners() {
        for (String body : List.of("if (true) { atomic {} } atomic {}", "if (true) {} else { atomic {} } atomic {}")) {
            var model = pair(body);
            List<ModelThread> threads = new ArrayList<>();
            for (int owner = 0; owner < 2; owner++) {
                var steps = model.program().threads().get(owner).steps();
                threads.add(new ModelThread(owner, List.of(steps.get(1), steps.get(0), steps.get(2))));
            }
            var selfTarget = new Program(model.program().initialConfiguration().state(), threads, model.program().sourceDigest());
            assertThrows(IllegalArgumentException.class, () -> SourceSymmetry.groups(selfTarget, null));
        }
        var model = pair("atomic {}");
        var state = (DynamicState) model.program().initialConfiguration().state();
        List<ModelThread> tooMany = java.util.stream.IntStream.range(0, 9).mapToObj(owner -> new ModelThread(owner, List.of())).toList();
        var excessive = new Program(new DynamicState(state.decl(), 9), tooMany, model.program().sourceDigest());
        assertThrows(IllegalArgumentException.class, () -> SourceSymmetry.groups(excessive, null));
    }

    /** Unknown source callbacks reject before initial enabledness can execute them. */
    @Test void rejectsCustomSourceStepsBeforeCallbacks() {
        var model = pair("atomic {}");
        SourceStep unknown = new SourceStep() {
            /** @return no modeled reads; execution remains deliberately untrusted */
            @Override public Set<MemoryLocation> reads() { return Set.of(); }
            /** @return no modeled writes; execution remains deliberately untrusted */
            @Override public Set<MemoryLocation> writes() { return Set.of(); }
            /** @param state unused state; this callback must never be reached
             * @return never returns
             */
            @Override public boolean enabled(SharedState state) { throw new AssertionError("Untrusted enabled callback must not run"); }
            /** @param threads unused definitions
             * @param parent unused parent
             * @param threadId unused original owner
             * @param scratch unused state
             * @return never returns
             */
            @Override public StepTransition transition(List<ModelThread> threads, Configuration parent, int threadId, SharedState scratch) {
                throw new AssertionError("Untrusted transition must not run");
            }
            /** @param pc unused position
             * @return never returns
             */
            @Override public ExecutedStep metadata(int pc) { throw new AssertionError("Untrusted metadata must not run"); }
        };
        var program = new Program(model.program().initialConfiguration().state(),
            List.of(model.program().threads().get(0), new ModelThread(1, List.of(unknown))), model.program().sourceDigest());
        assertThrows(IllegalArgumentException.class, () -> SourceSymmetry.groups(program, null));
    }

    /** Quotient representatives preserve errors/assertions, property timing, deadlocks and cutoffs.
     * @param declarations shared declarations
     * @param body identical owner body
     * @param property optional source property text
     * @param outcome independently expected terminal category
     */
    @ParameterizedTest @CsvSource(delimiter = '|', value = {
        "shared int x = 0;|assert(false);||VIOLATION",
        "shared int x = 0;|atomic { x = 1 % 0; }||VIOLATION",
        "shared int x = 0;|atomic when (false) {}||DEADLOCK",
        "shared int x = 0;|while (true) limit 0 {}||INCOMPLETE",
        "shared int x = 0;|atomic { x = x + 1; }|invariant always (x < 2);|VIOLATION",
        "shared int x = 0;|atomic { x = x + 1; }|invariant final (x == 2);|COMPLETED",
        "shared int x = 0;|atomic {}|invariant final (1 % 0 == 0);|VIOLATION"
    })
    void preservesVerdictsAndAuthenticatedEvidence(String declarations, String body, String property, String outcome) {
        var model = load(declarations + " thread 0 { " + body + " } thread 1 { " + body + " } " + (property == null ? "" : property));
        Invariant invariant = model.invariant().orElse(null);
        var concrete = new DfsExplorer().explore(model.program(), invariant);
        var reduced = new SymmetryDfsExplorer().explore(model.program(), invariant);
        assertEquals(Set.of(TraceOutcome.valueOf(outcome)), outcomes(concrete));
        assertEquals(outcomes(concrete), outcomes(reduced));
        for (Trace trace : reduced.traces()) {
            new TraceReplayer().replay(model.program(), trace, invariant == null ? (s,c) -> true : invariant);
            assertEquals(model.program().sourceDigest(), trace.evidence().sourceDigest());
        }
    }

    /** A state budget counts representatives and visitor callbacks remain concrete/read-only. */
    @Test void preservesVisitorAndStateBudget() {
        var model = pair("atomic { x = x + 1; }");
        List<Configuration> seen = new ArrayList<>();
        var result = new SymmetryDfsExplorer().explore(model.program(), null, seen::add, 2);
        assertEquals(2, result.statesExplored());
        assertEquals(2, seen.size());
        assertEquals(List.of(1, 0), seen.get(1).programCounters());
        assertTrue(result.traces().isEmpty());
    }

    /** Loads a complete bounded model from its declarations/threads/property body.
     * @param body program interior
     * @return immutable compiled source model
     */
    private static BenchmarkProgram load(String body) { return new SourceProgramLoader().load("program symmetry { " + body + " }"); }

    /** Creates two identical owners with the full typed local schema.
     * @param body identical source body in both owners
     * @return compiled two-owner model
     */
    private static BenchmarkProgram pair(String body) {
        return load("shared int x = 0; shared int[] items = [0,0]; local int r = 0; local bool flag = false; local int[] a = [0,0];"
            + "thread 0 { " + body + " } thread 1 { " + body + " }");
    }

    /** Extracts actual terminal categories without reproducing search classification.
     * @param result actual explorer result
     * @return distinct trace outcomes
     */
    private static Set<TraceOutcome> outcomes(DfsResult result) {
        Set<TraceOutcome> outcomes = new HashSet<>(); result.traces().forEach(trace -> outcomes.add(trace.outcome())); return outcomes;
    }
}
