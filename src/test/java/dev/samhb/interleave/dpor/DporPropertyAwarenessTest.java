/** Exercises property-aware dynamic partial order reduction and sleep-set visibility contracts. */
package dev.samhb.interleave.dpor;

import dev.samhb.interleave.bugs.BenchmarkProgram;
import dev.samhb.interleave.bugs.BugCorpus;
import dev.samhb.interleave.core.*;
import dev.samhb.interleave.search.*;
import dev.samhb.interleave.por.StaticPorExplorer;
import org.junit.jupiter.api.Test;
import java.io.DataOutput;
import java.io.IOException;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

/** Exercises property-aware dynamic partial order reduction and sleep-set visibility contracts. */
class DporPropertyAwarenessTest {

    /** Verifies that observed property reduces independent invisible work compared to exhaustive DFS. */
    @Test
    void observedPropertyReducesIndependentInvisibleWork() {
        // 3 threads:
        // Thread 0: 2 invisible steps on cell 0
        // Thread 1: 2 invisible steps on cell 1
        // Thread 2: 1 step on cell 2 (the only observed location)
        Cells initial = new Cells(0, 0, 0);
        Program program = new Program(initial, List.of(
            new ModelThread(0, List.of(
                new CellStep(-1, 0, 1, -1, false),
                new CellStep(-1, 0, 2, -1, false)
            )),
            new ModelThread(1, List.of(
                new CellStep(-1, 1, 1, -1, false),
                new CellStep(-1, 1, 2, -1, false)
            )),
            new ModelThread(2, List.of(
                new CellStep(-1, 2, 1, -1, false)
            ))
        ));

        Invariant property = Invariant.observing(locations("cell[2]"),
            state -> ((Cells) state).values[2] >= 0);

        DfsResult dfs = new DfsExplorer().explore(program, property);
        DfsResult dpor = new DporExplorer().explore(program, property);

        assertTrue(dpor.statesExplored() < dfs.statesExplored(),
            "DPOR with property awareness should explore fewer states than DFS. DFS: "
            + dfs.statesExplored() + ", DPOR: " + dpor.statesExplored());
        assertEquals(outcomes(dfs), outcomes(dpor), "Outcomes must match");
        assertTrue(outcomes(dpor).contains(TraceOutcome.COMPLETED));
    }

    /** Verifies that visible independent writes cannot hide intermediate violations. */
    @Test
    void visibleIndependentWritesCannotHideIntermediateViolations() {
        Program program = new Program(PetersonState.of(false, false, 0), List.of(
            new ModelThread(0, List.of(new WriteFlagStep(0, true))),
            new ModelThread(1, List.of(new WriteFlagStep(1, true)))
        ));

        // Invariant holds initially (flag(0)=false, flag(1)=false => !flag(1) is true).
        // If thread 1 executes first: flag(0)=false, flag(1)=true => VIOLATION!
        // If thread 0 executes first: flag(0)=true, flag(1)=false => holds.
        // Both writes are visible to the property observation footprint.
        Invariant property = Invariant.observing(locations("flag[0]", "flag[1]"), state -> {
            PetersonState flags = (PetersonState) state;
            return flags.flag(0) || !flags.flag(1);
        });

        DfsResult dfs = new DfsExplorer().explore(program, property);
        DfsResult dpor = new DporExplorer().explore(program, property);

        assertTrue(outcomes(dpor).contains(TraceOutcome.VIOLATION),
            "DPOR must not prune the violating intermediate interleaving when writes are visible");
        assertEquals(outcomes(dfs), outcomes(dpor));
    }

    /** Verifies that executing a visible step refreshes the sleep set so subsequent actions can observe it. */
    @Test
    void visibleStepRefreshesSleepSet() {
        // Thread 0: step 0 writes cell[0]=1 (invisible).
        //           step 1 reads cell[1]: if cell[1]==1, sets cell[2]=2 (causes violation!).
        // Thread 1: step 0 writes cell[1]=1 (visible!).
        Cells initial = new Cells(0, 0, 0);
        Step conditionalViolate = new Step() {
            public Set<MemoryLocation> reads() { return locations("cell[1]"); }
            public Set<MemoryLocation> writes() { return locations("cell[2]"); }
            public boolean enabled(SharedState state) { return true; }
            public StepOutcome execute(SharedState state) {
                Cells c = (Cells) state;
                if (c.values[1] == 1) {
                    c.values[2] = 2; // Violation value
                }
                return StepOutcome.ADVANCED;
            }
        };

        Program program = new Program(initial, List.of(
            new ModelThread(0, List.of(
                new CellStep(-1, 0, 1, -1, false),
                conditionalViolate
            )),
            new ModelThread(1, List.of(
                new CellStep(-1, 1, 1, -1, false)
            ))
        ));

        // Invariant observes cell[1] and cell[2]
        Invariant property = Invariant.observing(locations("cell[1]", "cell[2]"),
            state -> ((Cells) state).values[2] != 2);

        DfsResult dfs = new DfsExplorer().explore(program, property);
        DfsResult dpor = new DporExplorer().explore(program, property);

        assertTrue(outcomes(dfs).contains(TraceOutcome.VIOLATION));
        assertTrue(outcomes(dpor).contains(TraceOutcome.VIOLATION),
            "Executing a visible step must refresh sleep sets so Thread 0 can observe the updated cell[1]");
        assertEquals(outcomes(dfs), outcomes(dpor));
    }

    /** Verifies that invisible steps commute safely without hiding an invariant violation. */
    @Test
    void invisibleStepsCommuteSafelyWithoutHidingViolation() {
        Cells initial = new Cells(0, 0, 0);
        Program program = new Program(initial, List.of(
            new ModelThread(0, List.of(
                new CellStep(-1, 0, 1, -1, false), // cell[0] = 1 (invisible)
                new CellStep(-1, 2, 5, -1, false)  // cell[2] = 5 (visible, violates cell[2] < 5)
            )),
            new ModelThread(1, List.of(
                new CellStep(-1, 1, 1, -1, false)  // cell[1] = 1 (invisible)
            ))
        ));

        Invariant property = Invariant.observing(locations("cell[2]"),
            state -> ((Cells) state).values[2] < 5);

        DfsResult dfs = new DfsExplorer().explore(program, property);
        DfsResult dpor = new DporExplorer().explore(program, property);

        assertTrue(outcomes(dpor).contains(TraceOutcome.VIOLATION));
        assertEquals(outcomes(dfs), outcomes(dpor));
    }

    /** Verifies that an invariant with unknown observations falls back to exhaustive DFS. */
    @Test
    void unknownObservationsFallBackToExhaustiveDfs() {
        Cells initial = new Cells(0, 0, 0);
        Program program = new Program(initial, List.of(
            new ModelThread(0, List.of(new CellStep(-1, 0, 1, -1, false))),
            new ModelThread(1, List.of(new CellStep(-1, 1, 1, -1, false)))
        ));

        // Unannotated lambda without observedLocations()
        Invariant unannotated = (state, config) -> true;
        assertTrue(unannotated.observedLocations().isEmpty());

        DfsResult dfs = new DfsExplorer().explore(program, unannotated);
        DfsResult dpor = new DporExplorer().explore(program, unannotated);

        assertEquals(dfs.statesExplored(), dpor.statesExplored(),
            "Unknown observations must fall back to exhaustive DFS, exploring identical state count");
        assertEquals(outcomes(dfs), outcomes(dpor));
    }

    /** Verifies that an empty observation set treats all transitions as invisible and maximizes reduction. */
    @Test
    void emptyObservationsReducesCompletely() {
        Cells initial = new Cells(0, 0, 0);
        Program program = new Program(initial, List.of(
            new ModelThread(0, List.of(
                new CellStep(-1, 0, 1, -1, false),
                new CellStep(-1, 0, 2, -1, false)
            )),
            new ModelThread(1, List.of(
                new CellStep(-1, 1, 1, -1, false),
                new CellStep(-1, 1, 2, -1, false)
            ))
        ));

        Invariant emptyProperty = Invariant.observing(Set.of(), state -> true);
        assertTrue(emptyProperty.observedLocations().isPresent());
        assertTrue(emptyProperty.observedLocations().get().isEmpty());

        DfsResult dfs = new DfsExplorer().explore(program, emptyProperty);
        DfsResult dpor = new DporExplorer().explore(program, emptyProperty);

        assertTrue(dpor.statesExplored() <= dfs.statesExplored());
        assertEquals(outcomes(dfs), outcomes(dpor));
    }

    /** Verifies that DSL final properties fall back to exhaustive DFS and find the lost update bug. */
    @Test
    void dslFinalPropertyFallsBackToExhaustiveDfs() {
        BenchmarkProgram benchmark = BugCorpus.lostUpdate();
        Invariant invariant = benchmark.invariant().orElseThrow();
        assertTrue(invariant.observedLocations().isEmpty(),
            "LostUpdate uses a termination-checked property with unknown state observations");

        DfsResult dfs = new DfsExplorer().explore(benchmark.program(), invariant);
        DfsResult dpor = new DporExplorer().explore(benchmark.program(), invariant);

        assertEquals(dfs.statesExplored(), dpor.statesExplored(),
            "DSL final property must use exhaustive DFS fallback");
        assertTrue(outcomes(dpor).contains(TraceOutcome.VIOLATION));
        assertEquals(outcomes(dfs), outcomes(dpor));
    }

    /** Verifies differential equivalence across all benchmarks in BugCorpus. */
    @Test
    void bugCorpusAllVerdictsPreserved() {
        for (BenchmarkProgram benchmark : BugCorpus.all()) {
            Invariant invariant = benchmark.invariant().orElse(null);
            DfsResult dporResult = new DporExplorer().explore(benchmark.program(), invariant);
            DfsResult dfsResult = new DfsExplorer().explore(benchmark.program(), invariant);

            Set<TraceOutcome> dporOutcomes = outcomes(dporResult);
            Set<TraceOutcome> dfsOutcomes = outcomes(dfsResult);

            assertEquals(dfsOutcomes, dporOutcomes,
                "Outcomes must match for corpus program: " + benchmark.name());

            TraceOutcome expectedOutcome = switch (benchmark.expectedVerdict()) {
                case "VIOLATION" -> TraceOutcome.VIOLATION;
                case "DEADLOCK" -> TraceOutcome.DEADLOCK;
                case "PASS", "COMPLETED" -> TraceOutcome.COMPLETED;
                default -> throw new IllegalArgumentException("Unknown expected verdict: " + benchmark.expectedVerdict());
            };

            assertTrue(dporOutcomes.contains(expectedOutcome),
                "Benchmark " + benchmark.name() + " expected " + expectedOutcome + " but got " + dporOutcomes);
        }
    }

    /** Verifies differential equivalence against StaticPorExplorer for observed properties. */
    @Test
    void differentialCheckAgainstStaticPor() {
        Program program = new Program(PetersonState.of(false, false, 0), List.of(
            new ModelThread(0, List.of(new WriteFlagStep(0, true))),
            new ModelThread(1, List.of(new WriteFlagStep(1, true)))
        ));
        Invariant property = Invariant.observing(locations("flag[0]", "flag[1]"), state -> {
            PetersonState flags = (PetersonState) state;
            return flags.flag(0) || !flags.flag(1);
        });

        DfsResult dpor = new DporExplorer().explore(program, property);
        DfsResult staticPor = new StaticPorExplorer().explore(program, property);

        assertEquals(outcomes(staticPor), outcomes(dpor));
        assertTrue(outcomes(dpor).contains(TraceOutcome.VIOLATION));
    }

    private static Set<MemoryLocation> locations(String... names) {
        return Arrays.stream(names).map(MemoryLocation::of).collect(java.util.stream.Collectors.toSet());
    }

    private static Set<TraceOutcome> outcomes(DfsResult result) {
        Set<TraceOutcome> outcomes = EnumSet.noneOf(TraceOutcome.class);
        result.traces().forEach(trace -> outcomes.add(trace.outcome()));
        return outcomes;
    }

    /** Simple three-cell shared state for tests. */
    private static final class Cells implements SharedState {
        private final int[] values;

        Cells(int... values) { this.values = values.clone(); }

        public SharedState deepCopy() { return new Cells(values); }

        public void encodeTo(DataOutput out) throws IOException {
            for (int value : values) out.writeInt(value);
        }

        public boolean equals(Object other) {
            return other instanceof Cells cells && Arrays.equals(values, cells.values);
        }

        public int hashCode() {
            return Arrays.hashCode(values);
        }
    }

    /** Simple deterministic step modifying cells. */
    private record CellStep(int read, int write, int value, int guard, boolean assertion) implements Step {
        public Set<MemoryLocation> reads() {
            Set<MemoryLocation> reads = new HashSet<>();
            if (read >= 0) reads.add(MemoryLocation.of("cell[" + read + "]"));
            if (guard >= 0) reads.add(MemoryLocation.of("cell[" + guard + "]"));
            return reads;
        }

        public Set<MemoryLocation> writes() {
            return write < 0 ? Set.of() : locations("cell[" + write + "]");
        }

        public boolean enabled(SharedState state) {
            return guard < 0 || ((Cells) state).values[guard] == 1;
        }

        public StepOutcome execute(SharedState state) {
            Cells cells = (Cells) state;
            if (assertion) {
                return cells.values[read] == 0 ? StepOutcome.ADVANCED : StepOutcome.ASSERTION_FAILED;
            }
            if (write >= 0) {
                cells.values[write] = read >= 0 ? cells.values[read] : value;
            }
            return StepOutcome.ADVANCED;
        }
    }
}
