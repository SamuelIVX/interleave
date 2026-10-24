/** Public all-asleep cutoff evidence for compiler steps used through a digest-free Program wrapper. */
package dev.samhb.interleave.dpor;

import dev.samhb.interleave.core.*;
import dev.samhb.interleave.format.SourceProgramLoader;
import dev.samhb.interleave.search.*;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import static org.junit.jupiter.api.Assertions.*;

class DporFallbackCutoffTest {
    /** Independent compiler cutoffs asleep after a committed write retain their first prefix and callbacks.
     * @param observed whether to check the compiler's known-empty property
     * @param source real bounded source model, wrapped without its diagnostic digest for exploration
     * @param visits independently enumerated committed positions before any terminal attempt
     * @param events literal ordered public notifications
     * @param expected literal cutoff prefixes and owners, independently asserted for result and callbacks
     */
    @ParameterizedTest
    @MethodSource("fallbackCases")
    void sleepingCutoffsRetainFirstEvidence(boolean observed, String source, long visits,
            List<String> events, List<Witness> expected) {
        var model = new SourceProgramLoader().load(source);
        Program wrapped = withoutDigest(model.program());
        Invariant property = observed ? model.invariant().orElseThrow() : null;
        if (property != null) assertTrue(property.observedLocations().orElseThrow().isEmpty());
        var observer = new RecordingVisitor();
        DfsResult result = new DporExplorer().explore(wrapped, property, null, observer);
        assertEquals(visits, result.statesExplored());
        assertEquals(visits, result.states().size());
        assertEquals(events, observer.events);
        assertEquals(expected, observer.witnesses);
        assertEquals(expected, result.traces().stream().map(DporFallbackCutoffTest::witness).toList());
        verifyAgainstSource(model.program(), result, property);
    }

    /** Omitting the visitor leaves both distinct cutoff witnesses intact and does not throw.
     * @param observed whether to check the compiler's known-empty property
     */
    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void sleepingCutoffsDoNotRequireAVisitor(boolean observed) {
        var model = new SourceProgramLoader().load(source(1, 2));
        Invariant property = observed ? model.invariant().orElseThrow() : null;
        DfsResult result = new DporExplorer().explore(withoutDigest(model.program()), property, null, null);
        assertEquals(2, result.statesExplored());
        assertEquals(List.of(new Witness(List.of(0), 1), new Witness(List.of(0), 2)),
            result.traces().stream().map(DporFallbackCutoffTest::witness).toList());
        verifyAgainstSource(model.program(), result, property);
    }

    /** Enumerates one/two committed writes and one/two independent terminal loop owners.
     * @return six cases with independently derived literal visits, callbacks and cutoff evidence
     */
    private static Stream<Arguments> fallbackCases() {
        return Stream.of(false, true).flatMap(observed -> Stream.of(
            Arguments.of(observed, source(1, 1), 2L,
                List.of("visit:[0, 0]", "visit:[1, 0]", "cutoff:1"),
                List.of(new Witness(List.of(0), 1))),
            Arguments.of(observed, source(2, 1), 3L,
                List.of("visit:[0, 0]", "visit:[1, 0]", "visit:[2, 0]", "cutoff:1"),
                List.of(new Witness(List.of(0, 0), 1))),
            Arguments.of(observed, source(1, 2), 2L,
                List.of("visit:[0, 0, 0]", "visit:[1, 0, 0]", "cutoff:1", "cutoff:2"),
                List.of(new Witness(List.of(0), 1), new Witness(List.of(0), 2)))));
    }

    /** Builds finite compiler input; all loop conditions and empty bodies are independent of x.
     * @param writes one or two committed writes in owner zero
     * @param loops one or two distinct zero-limit loop owners
     * @return source with a known-empty always-true property
     */
    private static String source(int writes, int loops) {
        return "program fallback { shared int x = 0; thread 0 { atomic { x = 1; }"
            + (writes == 2 ? " atomic { x = 2; }" : "")
            + " } thread 1 { while (true) limit 0 {} }"
            + (loops == 2 ? " thread 2 { while (true) limit 0 {} }" : "")
            + " invariant always (true); }";
    }

    /** Uses the public legacy constructor while retaining the compiler's immutable SourceStep definitions.
     * @param original real compiler-produced program with source identity
     * @return digest-free program using identical definitions and initial values
     */
    private static Program withoutDigest(Program original) {
        assertTrue(original.isSource());
        assertTrue(original.threads().stream().flatMap(thread -> thread.steps().stream())
            .allMatch(step -> step instanceof SourceStep));
        Program wrapped = new Program(original.initialConfiguration().state(), original.threads());
        assertFalse(wrapped.isSource());
        return wrapped;
    }

    /** Checks literal cutoff fields before extracting a value tuple for an independent oracle.
     * @param trace actual callback or returned terminal evidence
     * @return recorded committed prefix and omitted loop owner
     */
    private static Witness witness(Trace trace) {
        assertEquals(TraceOutcome.INCOMPLETE, trace.outcome());
        assertEquals(java.util.Collections.nCopies(trace.threadIds().size(), StepOutcome.ADVANCED), trace.outcomes());
        CutoffEvidence cutoff = trace.cutoff();
        assertNotNull(cutoff);
        assertEquals(CutoffEvidence.Reason.LOOP_LIMIT, cutoff.reason());
        assertEquals(0, cutoff.instructionIndex());
        assertEquals(0, cutoff.limit());
        assertEquals(Integer.valueOf(0), cutoff.iterations());
        assertNotNull(cutoff.source());
        assertEquals(1, cutoff.expansionPath().size());
        assertEquals(0, cutoff.expansionPath().getFirst().iteration());
        return new Witness(trace.threadIds(), cutoff.threadId());
    }

    /** Verifies the supplied cutoff through the original source-aware replay protocol.
     * Plain legacy replay calls Step.execute directly, so is not used for these SourceStep wrappers.
     * @param original compiler-produced model with its diagnostic identity
     * @param result digest-free exploration evidence
     * @param property exploration property, or null for a true replay-only fallback
     */
    private static void verifyAgainstSource(Program original, DfsResult result, Invariant property) {
        Invariant replayProperty = property != null ? property : (state, config) -> true;
        for (Trace trace : result.traces()) {
            Trace enriched = Trace.create(original, trace.threadIds(), trace.outcomes(), trace.outcome(),
                replayProperty, trace.cutoff());
            new TraceReplayer().replay(original, enriched, replayProperty);
        }
    }

    /** Independent cutoff oracle; fixed reason, PC and bound fields are checked separately.
     * @param prefix successful owner choices preceding the omitted attempt
     * @param owner omitted loop owner
     */
    private record Witness(List<Integer> prefix, int owner) {}

    /** Non-mutating observer asserting terminal notifications independently of returned evidence. */
    private static final class RecordingVisitor implements StateVisitor {
        /** Ordered visit and cutoff notifications. */
        private final List<String> events = new ArrayList<>();
        /** Values extracted from terminal callbacks. */
        private final List<Witness> witnesses = new ArrayList<>();

        /** Records committed positions without modifying the model.
         * @param config newly visited configuration
         */
        @Override public void onStateVisited(Configuration config) {
            events.add("visit:" + config.programCounters());
        }

        /** Validates and records each actual cutoff callback.
         * @param trace emitted evidence
         */
        @Override public void onTraceCreated(Trace trace) {
            Witness value = witness(trace);
            events.add("cutoff:" + value.owner());
            witnesses.add(value);
        }
    }
}
