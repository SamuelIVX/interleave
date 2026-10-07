/** Dynamic search outcome comparisons and sleep-set dependency behavior. */
package dev.samhb.interleave.dpor;

import java.util.stream.Collectors;

import dev.samhb.interleave.bugs.*;
import dev.samhb.interleave.core.*;
import dev.samhb.interleave.por.StaticPorExplorer;
import dev.samhb.interleave.por.IndependenceRelation;
import dev.samhb.interleave.search.DfsExplorer;
import dev.samhb.interleave.search.DfsResult;
import dev.samhb.interleave.search.Invariant;
import dev.samhb.interleave.search.Trace;
import dev.samhb.interleave.search.TraceOutcome;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class DporExplorerTest {

    @Test
    void dporExploresFewerStatesThanDfs() {
        PetersonState initial = PetersonState.of(false, false, 0);

        List<Step> thread0Steps = List.of(
            new WriteFlagStep(0, true),
            new WriteTurnStep(1),
            new BusyWaitStep(0, 1),
            new CSEnterStep(0),
            new WriteFlagStep(0, false)
        );

        List<Step> thread1Steps = List.of(
            new WriteFlagStep(1, true),
            new WriteTurnStep(0),
            new BusyWaitStep(1, 0),
            new CSEnterStep(1),
            new WriteFlagStep(1, false)
        );

        ModelThread t0 = new ModelThread(0, thread0Steps);
        ModelThread t1 = new ModelThread(1, thread1Steps);

        Program program = new Program(initial, List.of(t0, t1));

        DfsExplorer dfsExplorer = new DfsExplorer();
        DfsResult dfsResult = dfsExplorer.explore(program);

        DporExplorer dporExplorer = new DporExplorer();
        DfsResult dporResult = dporExplorer.explore(program);

        assertTrue(dporResult.statesExplored() <= dfsResult.statesExplored(),
            "DPOR should explore <= states than DFS. DFS: " +
            dfsResult.statesExplored() + ", DPOR: " + dporResult.statesExplored());
    }

    /** Checks real verdicts on the ordinary-invariant exhaustive fallback; null-invariant tests cover reduction. */
    @Test
    void dporProducesSameVerdictAsDfs() {
        var model = dev.samhb.interleave.bugs.LostUpdate.program();
        var invariant = model.invariant().orElseThrow();
        var dfs = new DfsExplorer().explore(model.program(), invariant);
        var dpor = new DporExplorer().explore(model.program(), invariant);
        var expected = dfs.traces().stream().map(Trace::outcome).collect(Collectors.toSet());
        assertTrue(expected.contains(TraceOutcome.VIOLATION));
        assertEquals(expected, dpor.traces().stream().map(Trace::outcome).collect(Collectors.toSet()));
    }

    /** Filtering retains independent sleepers, wakes dependent ones, and leaves the parent set intact. */
    @Test
    void sleepSetFiltersDependenciesWithoutMutatingItsParent() {
        SleepSet parent = new SleepSet();
        Step flag0 = new WriteFlagStep(0, true);
        Step flag1 = new WriteFlagStep(1, true);
        parent.add(0, flag0);
        parent.add(1, flag1);
        SleepSet filtered = parent.copyFiltering(new IndependenceRelation(), flag0);
        assertFalse(filtered.contains(0, flag0), "a write to the same flag is dependent");
        assertTrue(filtered.contains(1, flag1), "an independent flag write can remain asleep");
        assertTrue(parent.contains(0, flag0));
        assertTrue(parent.contains(1, flag1));
        assertFalse(filtered.contains(1, new WriteTurnStep(0)), "membership includes the actual step");
    }

    @Test
    void happensBefore_computesTransitiveClosure() {
        HappensBefore hb = new HappensBefore();
        Step step = new WriteFlagStep(0, true);
        hb.record(0, 1, step, 0);
        hb.record(1, 2, step, 0);

        assertTrue(hb.happensBefore(0, 1), "Direct edge should hold");
        assertTrue(hb.happensBefore(1, 2), "Direct edge should hold");
        assertTrue(hb.happensBefore(0, 2), "Transitive edge should hold");
        assertFalse(hb.happensBefore(2, 0), "Reverse edge should not hold");
    }

    /** A read records its value so the two dependent orders are distinguishable in state. */
    @Test
    void dependentReadCanObserveBeforeAndAfterTheWrite() {
        class TestState implements SharedState {
            int a = 0;
            int b = 0;
            int observedA = -1;

            public TestState() {}

            @Override public SharedState deepCopy() {
                TestState copy = new TestState();
                copy.a = this.a;
                copy.b = this.b;
                copy.observedA = this.observedA;
                return copy;
            }

            @Override public void encodeTo(java.io.DataOutput out) throws java.io.IOException {
                out.writeInt(a);
                out.writeInt(b);
                out.writeInt(observedA);
            }

            @Override public boolean equals(Object o) {
                if (this == o) return true;
                if (!(o instanceof TestState that)) return false;
                return a == that.a && b == that.b && observedA == that.observedA;
            }

            @Override public int hashCode() {
                return Objects.hash(a, b, observedA);
            }

            @Override public String toString() {
                return String.format("TestState{a=%d, b=%d, observedA=%d}", a, b, observedA);
            }
        }


        class WriteAStep implements Step {
            private final int threadId;
            public WriteAStep(int threadId) { this.threadId = threadId; }
            @Override public Set<MemoryLocation> reads() {
                return Collections.emptySet();
            }
            @Override public Set<MemoryLocation> writes() {
                return Set.of(MemoryLocation.of("a"));
            }
            @Override public boolean enabled(SharedState state) {
                return state instanceof TestState;
            }
            @Override public StepOutcome execute(SharedState state) {
                TestState ts = (TestState) state;
                ts.a = 1;
                return StepOutcome.ADVANCED;
            }
            @Override public boolean equals(Object o) {
                if (this == o) return true;
                if (!(o instanceof WriteAStep that)) return false;
                return threadId == that.threadId;
            }
            @Override public int hashCode() { return Objects.hash(threadId); }
        }


        class WriteBStep implements Step {
            private final int threadId;
            public WriteBStep(int threadId) { this.threadId = threadId; }
            @Override public Set<MemoryLocation> reads() {
                return Collections.emptySet();
            }
            @Override public Set<MemoryLocation> writes() {
                return Set.of(MemoryLocation.of("b"));
            }
            @Override public boolean enabled(SharedState state) {
                return state instanceof TestState;
            }
            @Override public StepOutcome execute(SharedState state) {
                TestState ts = (TestState) state;
                ts.b = 1;
                return StepOutcome.ADVANCED;
            }
            @Override public boolean equals(Object o) {
                if (this == o) return true;
                if (!(o instanceof WriteBStep that)) return false;
                return threadId == that.threadId;
            }
            @Override public int hashCode() { return Objects.hash(threadId); }
        }


        class ReadAStep implements Step {
            private final int threadId;
            public ReadAStep(int threadId) { this.threadId = threadId; }
            @Override public Set<MemoryLocation> reads() {
                return Set.of(MemoryLocation.of("a"));
            }
            @Override public Set<MemoryLocation> writes() {
                return Set.of(MemoryLocation.of("observedA"));
            }
            @Override public boolean enabled(SharedState state) {
                return state instanceof TestState;
            }
            @Override public StepOutcome execute(SharedState state) {
                ((TestState) state).observedA = ((TestState) state).a;
                return StepOutcome.ADVANCED;
            }
            @Override public boolean equals(Object o) {
                if (this == o) return true;
                if (!(o instanceof ReadAStep that)) return false;
                return threadId == that.threadId;
            }
            @Override public int hashCode() { return Objects.hash(threadId); }
        }

        TestState initial = new TestState();


        List<Step> thread0Steps = List.of(
            new WriteAStep(0),
            new WriteBStep(0)
        );

        List<Step> thread1Steps = List.of(
            new ReadAStep(1)
        );

        ModelThread t0 = new ModelThread(0, thread0Steps);
        ModelThread t1 = new ModelThread(1, thread1Steps);

        Program program = new Program(initial, List.of(t0, t1));


        DporExplorer dporExplorer = new DporExplorer();
        DfsResult result = dporExplorer.explore(program);


        Set<Integer> observations = result.states().values().stream()
            .filter(Configuration::allTerminated)
            .map(config -> ((TestState) config.state()).observedA).collect(Collectors.toSet());
        assertEquals(Set.of(0, 1), observations,
            "the dependent read must retain both before-write and after-write observations");
    }

    @Test
    void dporWithoutInvariantReturnsPassForLostUpdate() {
        // The lost-update bug is only detected when an invariant is checked.
        // Without an invariant, DPOR explores all interleavings but doesn't
        // check for the lost-update condition, so it reports PASS (no VIOLATION).

        BenchmarkProgram benchmark = LostUpdate.program();
        Program program = benchmark.program();

        DporExplorer dporExplorer = new DporExplorer();
        // Call explore WITHOUT the invariant parameter - uses pure dporDfs
        DfsResult result = dporExplorer.explore(program);

        // Verify no VIOLATION traces found (all should be COMPLETED or DEADLOCK)
        boolean hasViolation = result.traces().stream()
                .anyMatch(t -> t.outcome() == TraceOutcome.VIOLATION);

        assertFalse(hasViolation,
            "DPOR without invariant should not report VIOLATION for lost-update. " +
            "Found traces: " + result.traces());

        // Should have some COMPLETED traces (the program terminates)
        boolean hasCompleted = result.traces().stream()
                .anyMatch(t -> t.outcome() == TraceOutcome.COMPLETED);
        assertTrue(hasCompleted, "Should have COMPLETED traces");

        // Verify states were explored
        assertTrue(result.statesExplored() > 0, "Should explore some states");
    }

    @Test
    void dporFindsLostUpdateViolation() {
        // The lost-update bug is detected when an invariant is provided.
        // With an invariant, DPOR uses the exhaustive DFS fallback and
        // correctly finds the VIOLATION trace where both threads read 0
        // and both write 1 (final counter = 1 instead of expected 2).

        BenchmarkProgram benchmark = LostUpdate.program();
        Program program = benchmark.program();
        Invariant invariant = benchmark.invariant().get();

        DporExplorer dporExplorer = new DporExplorer();
        // Call explore WITH the invariant parameter - triggers exhaustive DFS fallback
        DfsResult result = dporExplorer.explore(program, invariant);

        // Verify at least one VIOLATION trace is found
        boolean hasViolation = result.traces().stream()
                .anyMatch(t -> t.outcome() == TraceOutcome.VIOLATION);

        assertTrue(hasViolation,
            "DPOR with invariant should find VIOLATION for lost-update. " +
            "Found traces: " + result.traces());

        // Should also have COMPLETED traces (the non-buggy interleavings)
        boolean hasCompleted = result.traces().stream()
                .anyMatch(t -> t.outcome() == TraceOutcome.COMPLETED);
        assertTrue(hasCompleted, "Should have COMPLETED traces");

        // Verify states were explored
        assertTrue(result.statesExplored() > 0, "Should explore some states");
    }

    @Test
    void dporExploresLostUpdateInterleaving() {
        // This test exercises the pure dporDfs path (no invariant) and verifies
        // that the sleep set fix allows the critical interleaving to be explored:
        // T0.read → T1.read → T0.write → T1.write (both threads read 0 before either writes)
        //
        // Before the fix: T1's read was incorrectly put to sleep (read-read independent),
        // but T0's future write depends on T1's read (write-read conflict). The sleep set
        // prevented the violating interleaving from being explored.
        //
        // After the fix: The future-dependency check prevents sleeping T1's read,
        // allowing the interleaving where both reads happen before both writes.

        BenchmarkProgram benchmark = LostUpdate.program();
        Program program = benchmark.program();

        DporExplorer dporExplorer = new DporExplorer();
        // Pure DPOR (no invariant) - exercises dporDfs directly
        DfsResult result = dporExplorer.explore(program);

        // The fix ensures DPOR explores the interleaving where both reads execute
        // before either write. This trace should have thread sequence [0, 1, 0, 1]
        // (T0.read, T1.read, T0.write, T1.write).
        boolean hasCriticalInterleaving = result.traces().stream()
                .anyMatch(t -> {
                    List<Integer> threadIds = t.threadIds();
                    return threadIds.size() == 4 &&
                           threadIds.get(0) == 0 &&  // T0.read
                           threadIds.get(1) == 1 &&  // T1.read
                           threadIds.get(2) == 0 &&  // T0.write
                           threadIds.get(3) == 1;    // T1.write
                });

        assertTrue(hasCriticalInterleaving,
            "DPOR sleep set fix should allow the critical lost-update interleaving " +
            "(T0.read -> T1.read -> T0.write -> T1.write) to be explored. " +
            "Found traces: " + result.traces());
    }
}
