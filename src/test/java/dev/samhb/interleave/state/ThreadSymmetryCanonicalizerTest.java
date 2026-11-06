/** Independent orbit/mapping oracles for the internal thread-symmetry value foundation. */
package dev.samhb.interleave.state;

import dev.samhb.interleave.bugs.BenchmarkProgram;
import dev.samhb.interleave.format.SourceProgramLoader;
import dev.samhb.interleave.format.dsl.DynamicState;
import dev.samhb.interleave.search.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.*;
import static org.junit.jupiter.api.Assertions.*;

class ThreadSymmetryCanonicalizerTest {
    /** Three-owner permutations are explicit, independent of the production sorting algorithm. */
    private static final List<List<Integer>> THREE_PERMUTATIONS = List.of(
        List.of(0, 1, 2), List.of(0, 2, 1), List.of(1, 0, 2), List.of(1, 2, 0), List.of(2, 0, 1), List.of(2, 1, 0));

    /** Each component of a whole frame controls ordering without dropping the other components.
     * @param axis PC, integer, boolean or second array element being varied
     */
    @ParameterizedTest @ValueSource(strings = {"pc", "integer", "boolean", "array"})
    void canonicalizesAllSmallTypedFramePermutations(String axis) {
        var state = state(3);
        List<Integer> pcs = axis.equals("pc") ? List.of(2, 0, 1) : List.of(0, 0, 0);
        if (axis.equals("integer")) {
            state.setLocalInt(0, "r", Integer.MAX_VALUE); state.setLocalInt(1, "r", Integer.MIN_VALUE);
        } else if (axis.equals("boolean")) {
            state.setLocalBool(0, "flag", true); state.setLocalBool(2, "flag", true);
        } else if (axis.equals("array")) {
            state.setLocalArrayElement(0, "values", 1, 2); state.setLocalArrayElement(1, "values", 1, -1);
            state.setLocalArrayElement(2, "values", 1, 1);
        }
        var canonicalizer = new ThreadSymmetryCanonicalizer(3, List.of(List.of(2, 0, 1)));
        var original = canonicalizer.canonicalize(state, pcs);
        assertEquals(axis.equals("boolean") ? List.of(1, 0, 2) : List.of(1, 2, 0), original.canonicalToOriginal());
        var before = state.deepCopy();
        for (var permutation : THREE_PERMUTATIONS) {
            var reordered = permute(state, permutation);
            List<Integer> reorderedPcs = permutation.stream().map(pcs::get).toList();
            var position = canonicalizer.canonicalize(reordered, reorderedPcs);
            assertEquals(original.key(), position.key());
            assertMappings(position, 3);
            var canonicalState = permute(reordered, position.canonicalToOriginal());
            List<Integer> canonicalPcs = position.canonicalToOriginal().stream().map(reorderedPcs::get).toList();
            assertEquals(position.key(), canonicalizer.canonicalize(canonicalState, canonicalPcs).key());
            assertEquals(List.of(0, 1, 2), canonicalizer.canonicalize(canonicalState, canonicalPcs).canonicalToOriginal());
        }
        assertEquals(before, state);
    }

    /** Noncontiguous groups stay separate, omitted owners stay fixed and partition ordering is irrelevant. */
    @Test void respectsPartialPartitionsAndNormalizesGroupOrder() {
        var state = state(4);
        for (int owner = 0; owner < 4; owner++) state.setLocalInt(owner, "r", 4 - owner);
        var pcs = List.of(0, 0, 0, 0);
        var full = new ThreadSymmetryCanonicalizer(4, List.of(List.of(3, 1), List.of(2, 0)));
        var normalized = new ThreadSymmetryCanonicalizer(4, List.of(List.of(0, 2), List.of(1, 3)));
        assertEquals(List.of(2, 3, 0, 1), full.canonicalize(state, pcs).canonicalToOriginal());
        assertEquals(normalized.canonicalize(state, pcs).key(), full.canonicalize(state, pcs).key());
        var partial = new ThreadSymmetryCanonicalizer(4, List.of(List.of(0, 2)));
        assertEquals(List.of(2, 1, 0, 3), partial.canonicalize(state, pcs).canonicalToOriginal());
        assertNotEquals(partial.canonicalize(state, pcs).key(), partial.canonicalize(permute(state, List.of(0, 3, 2, 1)), pcs).key());
        assertNotEquals(full.canonicalize(state, pcs).key(), partial.canonicalize(state, pcs).key());
        var fixed = new ThreadSymmetryCanonicalizer(4, List.of());
        var singletons = new ThreadSymmetryCanonicalizer(4, List.of(List.of(3), List.of(1)));
        assertEquals(fixed.canonicalize(state, pcs).key(), singletons.canonicalize(state, pcs).key());
        assertEquals(List.of(0, 1, 2, 3), fixed.canonicalize(state, pcs).canonicalToOriginal());
    }

    /** Shared fields/arrays and declaration shape are fixed, and a PC cannot detach from its local row. */
    @Test void keepsSharedValuesSchemaAndFrameCorrelationDistinct() {
        var original = state(2);
        original.setLocalInt(0, "r", 1); original.setLocalInt(1, "r", 2);
        var canonicalizer = new ThreadSymmetryCanonicalizer(2, List.of(List.of(0, 1)));
        var pcs = List.of(0, 1);
        String key = canonicalizer.canonicalize(original, pcs).key();
        var crossed = permute(original, List.of(1, 0));
        assertNotEquals(key, canonicalizer.canonicalize(crossed, pcs).key());
        assertEquals(key, canonicalizer.canonicalize(crossed, List.of(1, 0)).key());
        var sharedInt = (DynamicState) original.deepCopy(); sharedInt.setInt("x", 7);
        assertNotEquals(key, canonicalizer.canonicalize(sharedInt, pcs).key());
        var sharedBool = (DynamicState) original.deepCopy(); sharedBool.setBool("ready", true);
        assertNotEquals(key, canonicalizer.canonicalize(sharedBool, pcs).key());
        var sharedArray = (DynamicState) original.deepCopy();
        sharedArray.setArrayElement("shared_values", 0, 1); sharedArray.setArrayElement("shared_values", 1, 0);
        assertNotEquals(key, canonicalizer.canonicalize(sharedArray, pcs).key());
        var differentDecl = new SourceProgramLoader().load("program other { shared int x = 0; local bool r = false; thread 0 {} thread 1 {} }");
        assertNotEquals(key, canonicalizer.canonicalize((DynamicState) differentDecl.program().initialConfiguration().state(), pcs).key());
        assertNotEquals(key, canonicalizer.canonicalize(original, List.of(0, 2)).key());
    }

    /** Keys retain each local type and partition even when other components have equal values. */
    @Test void distinguishesLocalValuesAndEqualFramePartitions() {
        var original = state(3);
        var pcs = List.of(0, 0, 0);
        var canonicalizer = new ThreadSymmetryCanonicalizer(3, List.of(List.of(0, 1, 2)));
        String key = canonicalizer.canonicalize(original, pcs).key();
        var integer = (DynamicState) original.deepCopy(); integer.setLocalInt(1, "r", 1);
        var bool = (DynamicState) original.deepCopy(); bool.setLocalBool(1, "flag", true);
        var array = (DynamicState) original.deepCopy(); array.setLocalArrayElement(1, "values", 0, 1);
        for (var changed : List.of(integer, bool, array)) {
            assertNotEquals(key, canonicalizer.canonicalize(changed, pcs).key());
        }
        assertNotEquals(key, new ThreadSymmetryCanonicalizer(3, List.of(List.of(0, 1))).canonicalize(original, pcs).key());
        assertNotEquals(key, new ThreadSymmetryCanonicalizer(3, List.of()).canonicalize(original, pcs).key());
    }

    /** Last-owner identity moves with frames and cannot collapse distinct scheduling roles.
     * @param last original last owner, or the initial sentinel
     */
    @ParameterizedTest @ValueSource(ints = {-1, 0, 1, 2})
    void mapsSchedulingContextEquivariantly(int last) {
        var state = state(3);
        state.setLocalInt(0, "r", 9); state.setLocalBool(1, "flag", true);
        state.setLocalArrayElement(2, "values", 0, -7);
        var pcs = List.of(1, 0, 0);
        var canonicalizer = new ThreadSymmetryCanonicalizer(3, List.of(List.of(0, 1, 2)));
        var original = canonicalizer.canonicalize(state, pcs, last);
        for (var permutation : THREE_PERMUTATIONS) {
            int newLast = last == -1 ? -1 : permutation.indexOf(last);
            assertEquals(original.key(), canonicalizer.canonicalize(permute(state, permutation),
                permutation.stream().map(pcs::get).toList(), newLast).key());
        }
        assertNotEquals(canonicalizer.canonicalize(state, pcs).key(), canonicalizer.canonicalize(state, pcs, -1).key());
        assertNotEquals(canonicalizer.canonicalize(state, pcs, 0).key(), canonicalizer.canonicalize(state, pcs, 1).key());
    }

    /** Equal frames use the last-owner marker before their original-ID tie break. */
    @Test void equalFramesRetainAValidLastOwnerAndStableTieMapping() {
        var state = state(3);
        var pcs = List.of(0, 0, 0);
        var canonicalizer = new ThreadSymmetryCanonicalizer(3, List.of(List.of(0, 1, 2)));
        assertEquals(List.of(0, 1, 2), canonicalizer.canonicalize(state, pcs, -1).canonicalToOriginal());
        assertEquals(List.of(0, 1, 2), canonicalizer.canonicalize(state, pcs, 2).canonicalToOriginal());
        for (int last = 0; last < 3; last++) {
            assertNotEquals(canonicalizer.canonicalize(state, pcs, -1).key(), canonicalizer.canonicalize(state, pcs, last).key());
        }
        assertEquals(List.of(1, 2, 0), canonicalizer.canonicalize(state, pcs, 0).canonicalToOriginal());
        assertEquals(List.of(0, 2, 1), canonicalizer.canonicalize(state, pcs, 1).canonicalToOriginal());
        assertEquals(canonicalizer.canonicalize(state, pcs, 0).key(), canonicalizer.canonicalize(state, pcs, 1).key());
        assertEquals(List.of(0, 1, 2), canonicalizer.canonicalize(state, pcs).canonicalToOriginal());
    }

    /** Input collections, returned maps and state-array values never alias across canonicalization. */
    @Test void freezesPartitionsResultsAndOriginalState() {
        var members = new ArrayList<>(List.of(1, 0));
        var groups = new ArrayList<List<Integer>>(); groups.add(members);
        var canonicalizer = new ThreadSymmetryCanonicalizer(2, groups);
        members.clear(); groups.clear();
        var state = state(2); state.setLocalInt(0, "r", 9);
        state.setLocalArrayElement(0, "values", 1, 6);
        var pcs = new ArrayList<>(List.of(0, 0));
        var before = state.deepCopy();
        var result = canonicalizer.canonicalize(state, pcs);
        assertEquals(before, state);
        assertEquals(List.of(1, 0), result.canonicalToOriginal());
        assertThrows(UnsupportedOperationException.class, () -> result.canonicalToOriginal().set(0, 0));
        String key = result.key();
        pcs.set(0, 3); state.setLocalInt(0, "r", -1); state.setLocalArrayElement(0, "values", 1, 99);
        assertEquals(key, result.key());
        assertNotEquals(key, canonicalizer.canonicalize(state, pcs).key());
        assertMappings(result, 2);
        assertThrows(IndexOutOfBoundsException.class, () -> result.originalThreadId(-1));
        assertThrows(IndexOutOfBoundsException.class, () -> result.originalThreadId(2));
        assertThrows(IndexOutOfBoundsException.class, () -> result.canonicalThreadId(-1));
        assertThrows(IndexOutOfBoundsException.class, () -> result.canonicalThreadId(2));
    }

    /** Actual DFS remains concrete; its offline keys have independently counted 5/9 orbits.
     * @param owners four or eight identical one-action owners
     * @param concrete literal Cartesian-mask count
     * @param orbitCount literal count of possible completed-owner totals
     */
    @ParameterizedTest @CsvSource({"4,16,5", "8,256,9"})
    void countsOfflineCounterOrbitsWithoutChangingExploration(int owners, int concrete, int orbitCount) {
        String threads = "";
        for (int owner = 0; owner < owners; owner++) threads += "thread " + owner + " { atomic { counter = counter + 1; } } ";
        var model = new SourceProgramLoader().load("program counter_orbits { shared int counter = 0; " + threads + " }");
        var group = new ArrayList<Integer>(); for (int owner = 0; owner < owners; owner++) group.add(owner);
        var canonicalizer = new ThreadSymmetryCanonicalizer(owners, List.of(group));
        Set<String> keys = new HashSet<>();
        var result = new DfsExplorer().explore(model.program(), null, null,
            config -> keys.add(canonicalizer.canonicalize((DynamicState) config.state(), config.programCounters()).key()));
        assertEquals(concrete, result.statesExplored());
        assertEquals(orbitCount, keys.size());
        assertFalse(result.traces().isEmpty());
        for (Trace trace : result.traces()) {
            var end = new TraceReplayer().replay(model.program(), trace, (state, config) -> true);
            assertEquals(owners, ((DynamicState) end.state()).getInt("counter"));
            assertTrue(trace.evidence().executedSteps().stream().allMatch(step -> step.instructionIndex() == 0));
        }
    }

    /** Independent split load/store enumeration has 13 concrete states, eight orbits and values {1,2}. */
    @Test void preservesSplitIncrementLocalObservationsAndOriginalWitnesses() {
        var model = new SourceProgramLoader().load("program split_orbits { shared int counter = 0; local int r = 0;"
            + " helper increment { atomic { local.r = counter; } atomic { counter = local.r + 1; } }"
            + " thread 0 { call increment; } thread 1 { call increment; } }");
        var canonicalizer = new ThreadSymmetryCanonicalizer(2, List.of(List.of(0, 1)));
        Set<String> keys = new HashSet<>();
        var result = new DfsExplorer().explore(model.program(), null, null,
            config -> keys.add(canonicalizer.canonicalize((DynamicState) config.state(), config.programCounters()).key()));
        assertEquals(13, result.statesExplored());
        assertEquals(8, keys.size());
        Set<Integer> finalValues = new HashSet<>();
        for (Trace trace : result.traces()) {
            finalValues.add(((DynamicState) new TraceReplayer().replay(model.program(), trace, (s, c) -> true).state()).getInt("counter"));
            assertEquals(4, trace.length());
            assertTrue(trace.evidence().executedSteps().stream().allMatch(step -> step.label().startsWith("call increment at ")));
        }
        assertEquals(Set.of(1, 2), finalValues);
    }

    /** Owner capacity boundaries remain explicit and do not raise current source limits.
     * @param owners unsupported zero, negative or above-cap capacity
     */
    @ParameterizedTest @ValueSource(ints = {-1, 0, 9})
    void rejectsInvalidCapacity(int owners) {
        assertThrows(IllegalArgumentException.class, () -> new ThreadSymmetryCanonicalizer(owners, List.of()));
    }

    /** Null/empty/overlapping/out-of-domain groups reject before any key can be made. */
    @Test void rejectsMalformedPartitions() {
        assertThrows(NullPointerException.class, () -> new ThreadSymmetryCanonicalizer(2, null));
        assertThrows(NullPointerException.class, () -> new ThreadSymmetryCanonicalizer(2, Arrays.asList((List<Integer>) null)));
        assertThrows(NullPointerException.class, () -> new ThreadSymmetryCanonicalizer(2, List.of(Arrays.asList((Integer) null))));
        for (var groups : List.of(List.<List<Integer>>of(List.of()), List.of(List.of(0, 0)),
                List.of(List.of(0), List.of(0, 1)), List.of(List.of(-1, 0)), List.of(List.of(0, 2)))) {
            assertThrows(IllegalArgumentException.class, () -> new ThreadSymmetryCanonicalizer(2, groups));
        }
    }

    /** Keys reject malformed state/PC/context domains rather than silently truncating owners. */
    @Test void rejectsMalformedPositions() {
        var canonicalizer = new ThreadSymmetryCanonicalizer(2, List.of(List.of(0, 1)));
        var state = state(2);
        assertThrows(NullPointerException.class, () -> canonicalizer.canonicalize(null, List.of(0, 0)));
        assertThrows(NullPointerException.class, () -> canonicalizer.canonicalize(state, null));
        assertThrows(NullPointerException.class, () -> canonicalizer.canonicalize(state, Arrays.asList(0, null)));
        assertThrows(IllegalArgumentException.class, () -> canonicalizer.canonicalize(state(1), List.of(0, 0)));
        assertThrows(IllegalArgumentException.class, () -> canonicalizer.canonicalize(state, List.of(0)));
        assertThrows(IllegalArgumentException.class, () -> canonicalizer.canonicalize(state, List.of(0, -1)));
        assertThrows(IllegalArgumentException.class, () -> canonicalizer.canonicalize(state, List.of(0, 0), -2));
        assertThrows(IllegalArgumentException.class, () -> canonicalizer.canonicalize(state, List.of(0, 0), 2));
        var single = new ThreadSymmetryCanonicalizer(1, List.of());
        assertEquals(List.of(0), single.canonicalize(state(1), List.of(0)).canonicalToOriginal());
    }

    /** A result is a checked full bijection, independent of how many groups were explicit.
     * @param result canonical key/mapping result
     * @param owners domain size
     */
    private static void assertMappings(ThreadSymmetryCanonicalizer.CanonicalPosition result, int owners) {
        Set<Integer> seen = new HashSet<>();
        for (int canonical = 0; canonical < owners; canonical++) {
            int original = result.originalThreadId(canonical);
            assertTrue(seen.add(original));
            assertEquals(canonical, result.canonicalThreadId(original));
        }
        assertEquals(owners, seen.size());
    }

    /** Reorders the known test schema directly, without calling the production frame-copy helper.
     * @param source original values
     * @param toOriginal original owner assigned to each new slot
     * @return independent modeled state with identical shared fields and permuted whole local rows
     */
    private static DynamicState permute(DynamicState source, List<Integer> toOriginal) {
        DynamicState state = (DynamicState) source.deepCopy();
        for (int owner = 0; owner < toOriginal.size(); owner++) {
            int old = toOriginal.get(owner);
            state.setLocalInt(owner, "r", source.getLocalInt(old, "r"));
            state.setLocalBool(owner, "flag", source.getLocalBool(old, "flag"));
            int[] values = source.getLocalArray(old, "values");
            state.setLocalArrayElement(owner, "values", 0, values[0]);
            state.setLocalArrayElement(owner, "values", 1, values[1]);
        }
        return state;
    }

    /** Loads the full typed test schema through the bounded public source loader.
     * @param owners valid source owner count
     * @return initial modeled values with integer/boolean/array local frames
     */
    private static DynamicState state(int owners) {
        String threads = "";
        for (int owner = 0; owner < owners; owner++) threads += "thread " + owner + " { atomic {} } ";
        BenchmarkProgram model = new SourceProgramLoader().load("program frame_orbits { shared int x = 0; shared bool ready = false;"
            + " shared int[] shared_values = [0, 1]; local int r = 0; local bool flag = false;"
            + " local int[] values = [0, 0]; local int[] empty = []; " + threads + " }");
        return (DynamicState) model.program().initialConfiguration().state();
    }
}
