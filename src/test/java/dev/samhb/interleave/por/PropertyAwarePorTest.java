/** Exercises property-aware static reduction through explorer results and independent state observations. */
package dev.samhb.interleave.por;

import dev.samhb.interleave.core.*;
import dev.samhb.interleave.bugs.BugCorpus;
import dev.samhb.interleave.bugs.BenchmarkProgram;
import dev.samhb.interleave.search.*;
import org.junit.jupiter.api.Test;
import java.util.*;
import java.io.DataOutput;
import java.io.IOException;
import java.util.function.Predicate;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import static org.junit.jupiter.api.Assertions.*;

/** Compares property-aware static POR with exhaustive exploration and replay. */
class PropertyAwarePorTest {
    /** Verifies that observed property reduces independent invisible work. */
    @Test
    void observedPropertyReducesIndependentInvisibleWork() {
        Program program = new Program(PetersonState.of(false, false, 0), List.of(
            new ModelThread(0, List.of(new WriteFlagStep(1, true))),
            new ModelThread(1, List.of(new WriteTurnStep(1)))
        ));
        Invariant property = Invariant.observing(Set.of(MemoryLocation.of("flag[0]")),
            state -> !((PetersonState) state).flag(0));
        DfsResult dfs = new DfsExplorer().explore(program, property);
        DfsResult por = new StaticPorExplorer().explore(program, property);

        assertEquals(4, dfs.statesExplored(), "initial, either single write, both writes");
        assertEquals(3, por.statesExplored(), "one invisible independent component goes first");
        assertEquals(List.of(0, 1), por.traces().getFirst().threadIds(), "thread ID breaks equal-size ties");
        assertTrue(por.traces().stream().allMatch(trace -> trace.outcome() == TraceOutcome.COMPLETED));
        PetersonState fresh = (PetersonState) program.initialConfiguration().state();
        assertFalse(fresh.flag(1));
        assertEquals(0, fresh.turn(), "preparation must not mutate the program's starting state");
    }

    /** Verifies that visible independent writes cannot hide intermediate violations. */
    @Test
    void visibleIndependentWritesCannotHideIntermediateViolations() {
        Program program = flags(new WriteFlagStep(0, true), new WriteFlagStep(1, true));
        Invariant property = Invariant.observing(locations("flag[0]", "flag[1]"), state -> {
            PetersonState flags = (PetersonState) state;
            return flags.flag(0) || !flags.flag(1);
        });
        assertTrue(outcomes(new StaticPorExplorer().explore(program, property)).contains(TraceOutcome.VIOLATION));
        assertEquals(outcomes(new DfsExplorer().explore(program, property)),
            outcomes(new StaticPorExplorer().explore(program, property)));
    }

    /** Verifies that future and transitive dependencies retain every initial branch. */
    @Test
    void futureAndTransitiveDependenciesRetainEveryInitialBranch() {
        Program future = new Program(PetersonState.of(false, false, 0), List.of(
            new ModelThread(0, List.of(new WriteFlagStep(0, true))),
            new ModelThread(1, List.of(new WriteFlagStep(1, true), new ReadFlagStep(1, 0)))
        ));
        assertEquals(Set.of(0, 1), initialBranches(future, Invariant.observing(Set.of(), state -> true)));
        Program transitive = new Program(PetersonState.of(false, false, 0), List.of(
            new ModelThread(0, List.of(new WriteFlagStep(0, true))),
            new ModelThread(1, List.of(new ReadFlagStep(1, 0), new WriteFlagStep(1, true))),
            new ModelThread(2, List.of(new ReadFlagStep(2, 1)))
        ));
        assertEquals(Set.of(0, 1, 2), initialBranches(transitive, Invariant.observing(Set.of(), state -> true)));
    }

    /** Verifies that a component containing adisabled thread cannot be selected. */
    @Test
    void aComponentContainingADisabledThreadCannotBeSelected() {
        Step disabled = new Step() {
            /**
             * Returns reads for this .
             * @return stable over-approximated modeled reads
             */
            public Set<MemoryLocation> reads() { return locations("flag[0]"); }
            /**
             * Returns writes for this .
             * @return stable over-approximated modeled writes
             */
            public Set<MemoryLocation> writes() { return Set.of(); }
            /**
             * Reports whether this fixture transition is enabled in the supplied state.
             * @param state shared state to inspect or mutate according to this operation
             * @return whether this transition can execute against the supplied state
             */
            public boolean enabled(SharedState state) { return false; }
            /**
             * Applies this fixture transition and returns its modeled outcome.
             * @param state shared state to inspect or mutate according to this operation
             * @return modeled execution outcome after applying the transition
             */
            public StepOutcome execute(SharedState state) { throw new AssertionError("disabled step executed"); }
        };
        Program program = new Program(PetersonState.of(false, false, 0), List.of(
            new ModelThread(0, List.of(new WriteFlagStep(0, true))),
            new ModelThread(1, List.of(disabled)),
            new ModelThread(2, List.of(new WriteTurnStep(1))),
            new ModelThread(3, List.of(new WriteTurnStep(2)))
        ));
        assertEquals(Set.of(0, 2, 3), initialBranches(program,
            Invariant.observing(locations("turn"), state -> ((PetersonState) state).turn() >= 0)));
    }

    /**
     * Verifies that non progress or assertion expands branches without executing prepared steps twice.
     * @param outcome execution outcome being recorded
     */
    @ParameterizedTest
    @EnumSource(value = StepOutcome.class, names = {"BLOCKED", "ASSERTION_FAILED"})
    void nonProgressOrAssertionExpandsBranchesWithoutExecutingPreparedStepsTwice(StepOutcome outcome) {
        Map<PetersonState, Integer> calls = new HashMap<>();
        Step candidate = new Step() {
            /**
             * Returns reads for this .
             * @return stable over-approximated modeled reads
             */
            public Set<MemoryLocation> reads() { return Set.of(); }
            /**
             * Returns writes for this .
             * @return stable over-approximated modeled writes
             */
            public Set<MemoryLocation> writes() { return Set.of(); }
            /**
             * Reports whether this fixture transition is enabled in the supplied state.
             * @param state shared state to inspect or mutate according to this operation
             * @return whether this transition can execute against the supplied state
             */
            public boolean enabled(SharedState state) { return true; }
            /**
             * Applies this fixture transition and returns its modeled outcome.
             * @param state shared state to inspect or mutate according to this operation
             * @return modeled execution outcome after applying the transition
             */
            public StepOutcome execute(SharedState state) {
                calls.merge((PetersonState) state.deepCopy(), 1, Integer::sum);
                return outcome;
            }
        };
        Program program = flags(candidate, new WriteFlagStep(1, true));
        DfsResult result = new StaticPorExplorer().explore(program,
            Invariant.observing(locations("flag[1]"), state -> true));
        assertTrue(result.states().values().stream().anyMatch(c -> c.programCounters().equals(List.of(0, 1))),
            "fallback must visit the other initial branch");
        assertEquals(1, calls.get(PetersonState.of(false, false, 0)), "reuse the prepared root transition");
        assertEquals(1, calls.get(PetersonState.of(false, true, 0)));
        if (outcome == StepOutcome.ASSERTION_FAILED) assertTrue(outcomes(result).contains(TraceOutcome.VIOLATION));
    }

    /** Verifies that terminated outcome advances and allows reduction. */
    @Test
    void terminatedOutcomeAdvancesAndAllowsReduction() {
        Step terminating = new Step() {
            /**
             * Returns reads for this .
             * @return stable over-approximated modeled reads
             */
            public Set<MemoryLocation> reads() { return Set.of(); }
            /**
             * Returns writes for this .
             * @return stable over-approximated modeled writes
             */
            public Set<MemoryLocation> writes() { return Set.of(); }
            /**
             * Reports whether this fixture transition is enabled in the supplied state.
             * @param state shared state to inspect or mutate according to this operation
             * @return whether this transition can execute against the supplied state
             */
            public boolean enabled(SharedState state) { return true; }
            /**
             * Applies this fixture transition and returns its modeled outcome.
             * @param state shared state to inspect or mutate according to this operation
             * @return modeled execution outcome after applying the transition
             */
            public StepOutcome execute(SharedState state) { return StepOutcome.TERMINATED; }
        };
        Program program = flags(terminating, new WriteFlagStep(1, true));
        DfsResult result = new StaticPorExplorer().explore(program, Invariant.observing(Set.of(), state -> true));
        assertEquals(3, result.statesExplored());
        assertEquals(Set.of(TraceOutcome.COMPLETED), outcomes(result));
        assertEquals(StepOutcome.TERMINATED, result.traces().getFirst().outcomes().getFirst());
    }

    /** Verifies that smallest invisible component wins before lower thread ids. */
    @Test
    void smallestInvisibleComponentWinsBeforeLowerThreadIds() {
        Program program = new Program(PetersonState.of(false, false, 0), List.of(
            new ModelThread(0, List.of(new WriteFlagStep(0, true))),
            new ModelThread(1, List.of(new WriteFlagStep(0, false))),
            new ModelThread(2, List.of(new WriteFlagStep(1, true)))
        ));
        assertEquals(Set.of(2), initialBranches(program, Invariant.observing(Set.of(), state -> true)));
    }

    /** Verifies that captured observations and footprints are read once per exploration. */
    @Test
    void capturedObservationsAndFootprintsAreReadOncePerExploration() {
        int[] observations = {0};
        int[] reads = {0};
        int[] writes = {0};
        Step step = new Step() {
            /**
             * Returns reads for this .
             * @return stable over-approximated modeled reads
             */
            public Set<MemoryLocation> reads() { reads[0]++; return Set.of(); }
            /**
             * Returns writes for this .
             * @return stable over-approximated modeled writes
             */
            public Set<MemoryLocation> writes() { writes[0]++; return Set.of(); }
            /**
             * Reports whether this fixture transition is enabled in the supplied state.
             * @param state shared state to inspect or mutate according to this operation
             * @return whether this transition can execute against the supplied state
             */
            public boolean enabled(SharedState state) { return true; }
            /**
             * Applies this fixture transition and returns its modeled outcome.
             * @param state shared state to inspect or mutate according to this operation
             * @return modeled execution outcome after applying the transition
             */
            public StepOutcome execute(SharedState state) { return StepOutcome.ADVANCED; }
        };
        Invariant property = new Invariant() {
            /**
             * Evaluates the state property using the inherited observation contract.
             * @param state shared state to inspect or mutate according to this operation
             * @param config current search configuration
             * @return whether holds
             */
            public boolean holds(SharedState state, Configuration config) { return true; }
            /**
             * Returns the stable locations used by this state-only property.
             * @return known state-observation locations, or an empty Optional when unknown
             */
            public Optional<Set<MemoryLocation>> observedLocations() { observations[0]++; return Optional.of(Set.of()); }
        };
        new StaticPorExplorer().explore(flags(step, new WriteFlagStep(1, true)), property);
        assertEquals(1, observations[0]);
        assertEquals(1, reads[0]);
        assertEquals(1, writes[0]);
    }

    /** Verifies that generated programs preserve violations and terminal outcomes and replay their traces. */
    @Test
    void generatedProgramsPreserveViolationsAndTerminalOutcomesAndReplayTheirTraces() {
        List<CellStep> choices = List.of(
            new CellStep(-1, 0, 1, -1, false), // x = 1
            new CellStep(-1, 1, 1, -1, false), // y = 1
            new CellStep(1, 0, 0, -1, false),  // x = y
            new CellStep(-1, 1, 1, 0, false),  // when x == 1, y = 1
            new CellStep(1, -1, 0, -1, true)   // assert y == 0
        );
        List<Predicate<SharedState>> predicates = List.of(
            state -> ((Cells) state).values[0] == 0,
            state -> ((Cells) state).values[1] == 0,
            state -> ((Cells) state).values[0] == 1 || ((Cells) state).values[1] == 0
        );
        boolean reduced = false;
        int cases = 0;
        for (CellStep a : choices) for (CellStep b : choices)
        for (CellStep c : choices) for (CellStep d : choices) {
            Program program = new Program(new Cells(0, 0, 0), List.of(
                new ModelThread(0, List.of(a, b)), new ModelThread(1, List.of(c, d)),
                new ModelThread(2, List.of(new CellStep(-1, 2, 1, -1, false)))
            ));
            for (int i = 0; i < predicates.size(); i++) {
                Invariant property = Invariant.observing(i == 0 ? locations("cell[0]")
                    : i == 1 ? locations("cell[1]") : locations("cell[0]", "cell[1]"), predicates.get(i));
                DfsResult dfs = new DfsExplorer().explore(program, property);
                DfsResult por = new StaticPorExplorer().explore(program, property);
                assertEquals(outcomes(dfs), outcomes(por), "generated case " + cases);
                for (Trace trace : por.traces()) replay(program, property, trace);
                reduced |= por.statesExplored() < dfs.statesExplored();
                cases++;
            }
        }
        assertEquals(1875, cases, "5^4 programs times three properties");
        assertTrue(reduced, "the differential comparison must exercise real reduction");
    }

    /** Verifies that annotated state only corpus properties preserve real violations with independent work. */
    @Test
    void annotatedStateOnlyCorpusPropertiesPreserveRealViolationsWithIndependentWork() {
        for (BenchmarkProgram model : List.of(BugCorpus.doubleCheckedLocking(), BugCorpus.tornCounter())) {
            Invariant property = model.name().equals("double-checked-locking")
                ? Invariant.observing(locations("observedInstance", "initialized"), state -> {
                    DclState dcl = (DclState) state;
                    return dcl.observedInstance() == null || dcl.initialized();
                })
                : Invariant.observing(locations("hasObservation", "observedHigh", "observedLow"), state -> {
                    PairState pair = (PairState) state;
                    return !pair.hasObservation() || pair.observedHigh() != 2 || pair.observedLow() == 2;
                });
            for (boolean extra : List.of(false, true)) {
                List<ModelThread> threads = new ArrayList<>(model.program().threads());
                if (extra) threads.add(new ModelThread(threads.size(), List.of(new Step() {
                    /**
                     * Returns reads for this .
                     * @return stable over-approximated modeled reads
                     */
                    public Set<MemoryLocation> reads() { return Set.of(); }
                    /**
                     * Returns writes for this .
                     * @return stable over-approximated modeled writes
                     */
                    public Set<MemoryLocation> writes() { return Set.of(); }
                    /**
                     * Reports whether this fixture transition is enabled in the supplied state.
                     * @param state shared state to inspect or mutate according to this operation
                     * @return whether this transition can execute against the supplied state
                     */
                    public boolean enabled(SharedState state) { return true; }
                    /**
                     * Applies this fixture transition and returns its modeled outcome.
                     * @param state shared state to inspect or mutate according to this operation
                     * @return modeled execution outcome after applying the transition
                     */
                    public StepOutcome execute(SharedState state) { return StepOutcome.ADVANCED; }
                })));
                Program program = new Program(model.program().initialConfiguration().state(), threads);
                DfsResult dfs = new DfsExplorer().explore(program, property);
                DfsResult por = new StaticPorExplorer().explore(program, property);
                assertTrue(outcomes(dfs).contains(TraceOutcome.VIOLATION));
                assertEquals(outcomes(dfs), outcomes(por), model.name());
                for (Trace trace : por.traces()) replay(program, property, trace);
                if (extra) assertTrue(por.statesExplored() < dfs.statesExplored(), model.name());
            }
        }
    }

    /** Independently executes actual outcomes instead of trusting the stored trace outcomes. */
    private static void replay(Program program, Invariant property, Trace trace) {
        Configuration position = program.initialConfiguration();
        boolean assertion = false;
        for (int i = 0; i < trace.threadIds().size(); i++) {
            int id = trace.threadIds().get(i);
            assertTrue(position.enabledThreadIds().contains(id));
            SharedState next = position.state().deepCopy();
            StepOutcome actual = program.threads().get(id).steps().get(position.programCounters().get(id)).execute(next);
            assertEquals(trace.outcomes().get(i), actual);
            if (actual == StepOutcome.ASSERTION_FAILED) {
                assertEquals(trace.threadIds().size() - 1, i);
                assertion = true;
            } else position = position.successor(id, actual, program.threads(), next);
        }
        switch (trace.outcome()) {
            case VIOLATION -> assertTrue(assertion || !property.holds(position.state(), position));
            case COMPLETED -> assertTrue(position.allTerminated());
            case DEADLOCK -> assertTrue(position.isDeadlockCandidate());
            default -> fail("unexpected trace outcome " + trace.outcome());
        }
    }

    /**
     * Builds the two-thread flag fixture with the requested initial values.
     * @param a first modeled flag value
     * @param b second modeled flag value
     * @return two-thread flag fixture
     */
    private static Program flags(Step a, Step b) {
        return new Program(PetersonState.of(false, false, 0), List.of(
            new ModelThread(0, List.of(a)), new ModelThread(1, List.of(b))));
    }

    /** Observes one-step positions without depending on the key or graph implementation. */
    private static Set<Integer> initialBranches(Program program, Invariant property) {
        Set<Integer> branches = new HashSet<>();
        new StaticPorExplorer().explore(program, property, null, config -> {
            if (config.programCounters().stream().mapToInt(Integer::intValue).sum() == 1) {
                for (int id = 0; id < config.programCounters().size(); id++) {
                    if (config.programCounters().get(id) == 1) branches.add(id);
                }
            }
        });
        return branches;
    }

    /**
     * Builds the expected modeled-location set from stable names.
     * @param names modeled memory-location names
     * @return set of named modeled memory locations
     */
    private static Set<MemoryLocation> locations(String... names) {
        return Arrays.stream(names).map(MemoryLocation::of).collect(java.util.stream.Collectors.toSet());
    }

    /**
     * Collects the distinct terminal outcomes reached by an exploration.
     * @param result completed exploration result
     * @return distinct terminal outcomes reached by this exploration
     */
    private static Set<TraceOutcome> outcomes(DfsResult result) {
        Set<TraceOutcome> outcomes = EnumSet.noneOf(TraceOutcome.class);
        result.traces().forEach(trace -> outcomes.add(trace.outcome()));
        return outcomes;
    }

    /** Three independent cells with value equality, used as an encoder-independent test domain. */
    private static final class Cells implements SharedState {
        /** Values. */
        private final int[] values;
        /**
         * Creates an isolated snapshot of cells from the supplied values.
         * @param values modeled array values to copy
         */
        private Cells(int... values) { this.values = values.clone(); }
        /**
         * Returns deep copy for this cells.
         * @return an isolated copy preserving the modeled values
         */
        public SharedState deepCopy() { return new Cells(values); }
        /**
         * Writes this fixture’s values in a deterministic canonical order.
         * @param out destination for canonical bytes
         * @throws IOException if canonical bytes cannot be written
         */
        public void encodeTo(DataOutput out) throws IOException { for (int value : values) out.writeInt(value); }
        /**
         * Compares modeled fixture values for equality.
         * @param other object to compare with this value
         * @return whether the other object satisfies this type’s equality contract
         */
        public boolean equals(Object other) { return other instanceof Cells cells && Arrays.equals(values, cells.values); }
        /**
         * Hashes the canonical configuration value for bucket selection.
         * @return hash consistent with this type’s equality contract
         */
        public int hashCode() { return Arrays.hashCode(values); }
    }

    /** Deterministic writes, copies, guards and assertions with complete literal footprints. */
    private record CellStep(int read, int write, int value, int guard, boolean assertion) implements Step {
        /**
         * Returns reads for this cell step.
         * @return stable over-approximated modeled reads
         */
        public Set<MemoryLocation> reads() {
            Set<MemoryLocation> reads = new HashSet<>();
            if (read >= 0) reads.add(MemoryLocation.of("cell[" + read + "]"));
            if (guard >= 0) reads.add(MemoryLocation.of("cell[" + guard + "]"));
            return reads;
        }
        /**
         * Returns writes for this cell step.
         * @return stable over-approximated modeled writes
         */
        public Set<MemoryLocation> writes() { return write < 0 ? Set.of() : locations("cell[" + write + "]"); }
        /**
         * Reports whether this fixture transition is enabled in the supplied state.
         * @param state shared state to inspect or mutate according to this operation
         * @return whether this transition can execute against the supplied state
         */
        public boolean enabled(SharedState state) { return guard < 0 || ((Cells) state).values[guard] == 1; }
        /**
         * Applies this fixture transition and returns its modeled outcome.
         * @param state shared state to inspect or mutate according to this operation
         * @return modeled execution outcome after applying the transition
         */
        public StepOutcome execute(SharedState state) {
            Cells cells = (Cells) state;
            if (assertion) return cells.values[read] == 0 ? StepOutcome.ADVANCED : StepOutcome.ASSERTION_FAILED;
            if (write >= 0) cells.values[write] = read >= 0 ? cells.values[read] : value;
            return StepOutcome.ADVANCED;
        }
    }

}
