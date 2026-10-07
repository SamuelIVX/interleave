/** Deadline boundary checks with a controlled clock and real explorers, never sleeps. */
package dev.samhb.interleave;

import dev.samhb.interleave.core.*;
import dev.samhb.interleave.search.StateStore;
import dev.samhb.interleave.state.HashingStateStore;
import dev.samhb.interleave.testsupport.TestPrograms;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.ToLongFunction;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import static org.junit.jupiter.api.Assertions.*;

/** Drives elapsed time from visited positions independently of wall-clock scheduling. */
class InterleaveRunnerTimeLimitTest {
    /** Clock epoch separates absolute time from the elapsed duration under test. */
    private static final long EPOCH = 1_000;

    /**
     * Checks a 10ms deadline at 9, 10 and 11ms with the same two-step program.
     * @param strategy explorer that must honor the shared runner limit
     */
    @ParameterizedTest
    @EnumSource(Strategy.class)
    void positiveDeadlineStopsAtAndAboveTheBoundary(Strategy strategy) {
        for (long elapsed : new long[] {9, 10, 11}) {
            AtomicLong clock = new AtomicLong(EPOCH);
            InterleaveRunner runner = runner(strategy, Duration.ofMillis(10), clock,
                config -> config.programCounters().getFirst() == 0 ? 0 : elapsed);
            TestResult result = runner.run(twoSteps(), clock::get);
            assertEquals(elapsed >= 10, result.limitExceeded(), strategy + " at " + elapsed);
            assertEquals(elapsed < 10 ? 3 : 2, result.statesExplored());
            assertEquals(elapsed < 10 ? 1 : 0, result.completedTraces().size());
            assertTrue(result.failingTraces().isEmpty());
            assertTrue(result.deadlockedTraces().isEmpty());
            assertFalse(result.hasIncomplete(), "a resource deadline is not a preemption bound");
            assertEquals(elapsed, result.wallTimeMs(), "report elapsed time, not the clock epoch");
        }
    }

    /**
     * A missing time limit must not stop a run even when the clock advances far beyond 10ms.
     * @param strategy explorer that must retain no-limit behavior
     */
    @ParameterizedTest
    @EnumSource(Strategy.class)
    void nullDeadlineLeavesTheSearchUnlimited(Strategy strategy) {
        AtomicLong clock = new AtomicLong(EPOCH);
        TestResult result = runner(strategy, null, clock,
            config -> config.programCounters().getFirst() == 0 ? 0 : 1_000_000)
            .run(twoSteps(), clock::get);
        assertFalse(result.limitExceeded());
        assertEquals(3, result.statesExplored());
        assertEquals(1, result.completedTraces().size());
        assertEquals(1_000_000, result.wallTimeMs());
    }

    /**
     * Zero retains the existing immediate-deadline behavior at the initial configuration.
     * @param strategy explorer that must report the captured initial visit
     */
    @ParameterizedTest
    @EnumSource(Strategy.class)
    void zeroDeadlineStopsAtTheInitialVisit(Strategy strategy) {
        AtomicLong clock = new AtomicLong(EPOCH);
        TestResult result = runner(strategy, Duration.ZERO, clock, config -> 0)
            .run(twoSteps(), clock::get);
        assertTrue(result.limitExceeded());
        assertEquals(1, result.statesExplored());
        assertTrue(result.completedTraces().isEmpty());
        assertEquals(0, result.wallTimeMs());
    }

    /** Checks that a deadline on a later DFS branch retains a completion from the earlier branch. */
    @Test
    void interruptedRunPreservesAlreadyRecordedTraces() {
        AtomicLong clock = new AtomicLong(EPOCH);
        TestResult result = runner(Strategy.DFS, Duration.ofMillis(10), clock,
            config -> config.programCounters().equals(List.of(0, 1)) ? 10 : 0)
            .run(TestPrograms.independentFlags(), clock::get);
        assertTrue(result.limitExceeded());
        assertEquals(4, result.statesExplored());
        assertEquals(1, result.completedTraces().size());
        assertEquals(List.of(0, 1), result.completedTraces().getFirst().threadIds());
    }

    /**
     * Creates a real runner with a store observer that advances only the test clock.
     * @param strategy explorer to exercise
     * @param limit deadline, or null for no limit
     * @param clock test-owned millisecond clock
     * @param elapsed elapsed time assigned to each newly marked position
     * @return runner with fresh stores and unchanged modeled step semantics
     */
    private static InterleaveRunner runner(Strategy strategy, Duration limit, AtomicLong clock,
                                           ToLongFunction<Configuration> elapsed) {
        return InterleaveRunner.builder().strategy(strategy).maxTime(limit)
            .stateStoreFactory(() -> new ClockObservingStore(clock, elapsed)).build();
    }

    /** @return one thread with two deterministic writes and three reachable positions */
    private static Program twoSteps() {
        return new Program(PetersonState.of(false, false, 0), List.of(
            new ModelThread(0, List.of(new WriteFlagStep(0, true), new WriteFlagStep(1, true)))));
    }

    /** Observes visits without changing duplicate suppression or modeled execution. */
    private static final class ClockObservingStore implements StateStore {
        private final HashingStateStore delegate = new HashingStateStore();
        private final AtomicLong clock;
        private final ToLongFunction<Configuration> elapsed;

        /**
         * Captures the clock and a position-based elapsed-time schedule.
         * @param clock test-owned clock
         * @param elapsed elapsed milliseconds when a position is marked
         */
        private ClockObservingStore(AtomicLong clock, ToLongFunction<Configuration> elapsed) {
            this.clock = clock;
            this.elapsed = elapsed;
        }

        /** {@inheritDoc} */
        @Override public boolean isVisited(Configuration config) { return delegate.isVisited(config); }
        /** {@inheritDoc} */
        @Override public void markVisited(Configuration config) {
            delegate.markVisited(config);
            clock.set(EPOCH + elapsed.applyAsLong(config));
        }
        /** {@inheritDoc} */
        @Override public boolean isVisited(Configuration config, int last, int preemptions) {
            return delegate.isVisited(config, last, preemptions);
        }
        /** {@inheritDoc} */
        @Override public void markVisited(Configuration config, int last, int preemptions) {
            delegate.markVisited(config, last, preemptions);
            clock.set(EPOCH + elapsed.applyAsLong(config));
        }
        /** {@inheritDoc} */
        @Override public void clear() { delegate.clear(); }
    }
}
