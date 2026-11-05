/** Independent fixture oracles and JFR attribution boundaries for the opt-in profiler. */
package dev.samhb.profiling;

import dev.samhb.interleave.search.DfsResult;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import jdk.jfr.Recording;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import static org.junit.jupiter.api.Assertions.*;

class SearchProfileTest {
    /** Six independently enumerated owner words yield 19 prefix states and six replayable leaves.
     * @param strategy public search path
     */
    @ParameterizedTest @EnumSource(SearchProfile.Strategy.class)
    void dependentOraclesMatchEveryExplorer(SearchProfile.Strategy strategy) {
        var fixture = SearchProfile.fixture("dependent", 2);
        var result = SearchProfile.search(fixture, strategy);
        assertEquals(new SearchProfile.Counts(19, 19, 6), SearchProfile.counts(result));
        assertEquals(24, result.traces().stream().mapToInt(t -> t.threadIds().size()).sum());
        assertEquals("8ac14501cf689e81f25e74b21d06b47e20eb9368e10d58869e73f857a5e42f90", SearchProfile.validate(fixture, result));
    }

    /** A two-by-two product has nine states; reductions preserve the full terminal observation.
     * @param strategy public search path
     */
    @ParameterizedTest @EnumSource(SearchProfile.Strategy.class)
    void independentOraclesMatchEveryExplorer(SearchProfile.Strategy strategy) {
        var fixture = SearchProfile.fixture("independent", 2);
        var result = SearchProfile.search(fixture, strategy);
        long expected = switch (strategy) { case DFS -> 9; case POR -> 5; case DPOR -> 8; };
        assertEquals(expected, result.statesExplored());
        assertEquals(1, result.traces().size());
        assertEquals(SearchProfile.SINGLE_DIGEST, SearchProfile.validate(fixture, result));
    }

    /** Equal conflicting writes permit value merging while incomparable sleep contexts require revisits. */
    @Test void contextFixtureExercisesRevisits() {
        var fixture = SearchProfile.fixture("contexts", 2);
        var dfs = SearchProfile.search(fixture, SearchProfile.Strategy.DFS);
        assertEquals(9, dfs.states().size());
        var dpor = SearchProfile.search(fixture, SearchProfile.Strategy.DPOR);
        assertTrue(dpor.statesExplored() > dpor.states().size());
        assertEquals(SearchProfile.validate(fixture, dfs), SearchProfile.validate(fixture, dpor));
    }

    /** Full-size fixtures retain literal independent state and observation oracles.
     * @param strategy public search path
     */
    @ParameterizedTest @EnumSource(SearchProfile.Strategy.class)
    void measuredTraceWorkloadRetainsAllTerminalSchedules(SearchProfile.Strategy strategy) {
        var fixture = SearchProfile.fixture("dependent", 4);
        var result = SearchProfile.search(fixture, strategy);
        assertEquals(new SearchProfile.Counts(7365, 7365, 2520), SearchProfile.counts(result));
        assertEquals(20160, result.traces().stream().mapToInt(t -> t.threadIds().size()).sum());
        assertEquals(SearchProfile.DEPENDENT_DIGEST, SearchProfile.validate(fixture, result));
    }

    /** An empty witness population must fail, rather than report a false successful comparison. */
    @Test void missingWitnessesFailValidation() {
        var fixture = SearchProfile.fixture("independent", 2);
        assertThrows(IllegalStateException.class, () -> SearchProfile.validate(fixture, new DfsResult(java.util.Map.of(), List.of(), 0)));
        assertThrows(IllegalArgumentException.class, () -> SearchProfile.fixture("unknown", 2));
    }

    /** Inclusive trace frames take precedence, while coverage proxies remain separate from other sleepers. */
    @Test void stackFamiliesAreExclusiveAndMissingStacksRemainUnknown() {
        String sleep = "dev.samhb.interleave.dpor.SleepSet#";
        String coverage = "dev.samhb.interleave.dpor.DporExplorer#lambda$dporDfs$0";
        assertEquals("traceReplay", SearchProfile.category(List.of(sleep + "copy", coverage, "dev.samhb.interleave.search.Trace#create")));
        assertEquals("coverageLambda", SearchProfile.category(List.of(sleep + "entries", coverage)));
        assertEquals("coverageAccess", SearchProfile.category(List.of(sleep + "copy")));
        assertEquals("coverageAccess", SearchProfile.category(List.of(sleep + "entries")));
        assertEquals("sleepOperations", SearchProfile.category(List.of(sleep + "copyFiltering")));
        assertEquals("other", SearchProfile.category(List.of("java.util.ArrayList#add")));
        assertEquals("unknown", SearchProfile.category(List.of()));
    }

    /** Real phase events round-trip without requiring probabilistic samples on a short unit test.
     * @param directory temporary recording directory
     * @throws Exception if JFR output fails
     */
    @Test void phaseMarkerRoundTripsAndMissingMarkersFail(@TempDir Path directory) throws Exception {
        Path path = directory.resolve("phase.jfr");
        try (var recording = new Recording()) {
            recording.enable(SearchProfile.Batch.class).withThreshold(Duration.ZERO);
            recording.start();
            var event = new SearchProfile.Batch();
            event.begin(); event.operations = 7; event.end(); event.commit();
            recording.stop(); recording.dump(path);
        }
        assertEquals(java.util.Map.of("profileOperations", 7L), SearchProfile.summarize(path));
        try (var recording = new Recording()) {
            recording.start(); recording.stop(); recording.dump(path);
        }
        assertThrows(IllegalStateException.class, () -> SearchProfile.summarize(path));
    }
}
