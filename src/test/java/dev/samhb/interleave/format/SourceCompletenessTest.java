/** Scheduling witnesses, reason retention and visitor contracts for bounded source search. */
package dev.samhb.interleave.format;

import dev.samhb.interleave.cb.ContextBoundedExplorer;
import dev.samhb.interleave.*;
import dev.samhb.interleave.core.*;
import dev.samhb.interleave.format.registry.RegistryException;
import dev.samhb.interleave.search.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class SourceCompletenessTest {
    private final SourceProgramLoader loader = new SourceProgramLoader();

    /** First preemption cutoff follows a forced switch and targets an already advanced source PC. */
    @Test void preemptionWitnessRetainsActualPcLocationAndExpansionIdentity() {
        var model = loader.load("program scheduling { shared int x = 0; thread 0 { repeat 1 { atomic { x = 1; } atomic when (x == 0) { x = 2; } } } thread 1 { atomic { x = 0; } atomic { x = 3; } } }");
        var result = new ContextBoundedExplorer().explore(model.program(), null, null, null, 0);
        Trace witness = result.traces().stream().filter(t -> t.cutoff() != null).findFirst().orElseThrow();
        assertEquals(1, result.traces().stream().filter(t -> t.cutoff() != null && t.cutoff().reason() == CutoffEvidence.Reason.PREEMPTION_BOUND).count());
        CutoffEvidence cutoff = witness.cutoff();
        assertEquals(CutoffEvidence.Reason.PREEMPTION_BOUND, cutoff.reason());
        assertEquals(List.of(0, 1), witness.threadIds());
        assertEquals(0, cutoff.threadId());
        assertEquals(1, cutoff.instructionIndex());
        ExecutedStep expected = ((SourceStep) model.program().threads().getFirst().steps().get(1)).metadata(1);
        assertEquals(expected.source(), cutoff.source());
        assertEquals(expected.expansionPath(), cutoff.expansionPath());
        assertFalse(cutoff.expansionPath().isEmpty());
        new TraceReplayer().replay(model.program(), witness);
    }

    /** Deduplication keeps the first witness and emits exactly one matching visitor callback. */
    @Test void loopWitnessesAreDeduplicatedWithoutLosingVisitorEvidence() {
        var model = loader.load("program dedup { shared int x = 0; thread 0 { atomic { x = 1; } atomic { x = 2; } } thread 1 { atomic {} while (true) limit 0 {} } }");
        List<Trace> seen = new ArrayList<>();
        StateVisitor observer = new StateVisitor() {
            /** No modeled effects enter the visitor. */
            public void onStateVisited(Configuration configuration) {}
            /** Collects actual emitted evidence. */
            public void onTraceCreated(Trace trace) { seen.add(trace); }
        };
        var result = new ContextBoundedExplorer().explore(model.program(), null, null, observer, 10);
        List<Trace> incomplete = result.traces().stream().filter(t -> t.outcome() == TraceOutcome.INCOMPLETE).toList();
        assertEquals(1, incomplete.size());
        assertEquals(1, incomplete.getFirst().cutoff().threadId());
        assertEquals(1, incomplete.getFirst().cutoff().instructionIndex());
        assertEquals(incomplete, seen.stream().filter(t -> t.outcome() == TraceOutcome.INCOMPLETE).toList());
        new TraceReplayer().replay(model.program(), incomplete.getFirst());
    }

    /** Reusing an explorer must reset cutoff indexes and preserve callbacks for every new run. */
    @Test void loopDeduplicationResetsAcrossAllExplorerRuns() {
        var model = loader.load("program dedup { shared int x = 0; thread 0 { atomic { x = 1; } atomic { x = 2; } } thread 1 { atomic {} while (true) limit 0 {} } }");
        var dfs = new DfsExplorer();
        var por = new dev.samhb.interleave.por.StaticPorExplorer();
        var dpor = new dev.samhb.interleave.dpor.DporExplorer();
        var cb = new ContextBoundedExplorer();
        List<java.util.function.Function<StateVisitor,DfsResult>> searches = List.of(
            visitor -> dfs.explore(model.program(), null, null, visitor),
            visitor -> por.explore(model.program(), null, null, visitor),
            visitor -> dpor.explore(model.program(), null, null, visitor),
            visitor -> cb.explore(model.program(), null, null, visitor, 10));
        for (var search : searches) for (int run = 0; run < 2; run++) {
            List<Trace> seen = new ArrayList<>();
            StateVisitor observer = new StateVisitor() {
                /** Observes without changing modeled state. */
                @Override public void onStateVisited(Configuration configuration) {}
                /** Collects each emitted witness callback. */
                @Override public void onTraceCreated(Trace trace) { seen.add(trace); }
            };
            List<Trace> incomplete = search.apply(observer).traces().stream()
                .filter(t -> t.outcome() == TraceOutcome.INCOMPLETE).toList();
            assertEquals(1, incomplete.size());
            assertEquals(incomplete, seen.stream().filter(t -> t.outcome() == TraceOutcome.INCOMPLETE).toList());
            new TraceReplayer().replay(model.program(), incomplete.getFirst());
        }
    }

    /** Distinct loop occurrences must never share a deduplication key. */
    @Test void distinctLoopWitnessesSurviveEveryExplorer() {
        var model = loader.load("program distinct { shared int x = 0; thread 0 { while (true) limit 0 {} } thread 1 { while (true) limit 0 {} } }");
        List<DfsResult> results = List.of(
            new DfsExplorer().explore(model.program()),
            new dev.samhb.interleave.por.StaticPorExplorer().explore(model.program()),
            new dev.samhb.interleave.dpor.DporExplorer().explore(model.program()),
            new ContextBoundedExplorer().explore(model.program(), null, null, null, 10));
        for (DfsResult result : results) {
            List<Trace> incomplete = result.traces().stream()
                .filter(t -> t.outcome() == TraceOutcome.INCOMPLETE).toList();
            assertEquals(2, incomplete.size());
            assertEquals(Set.of(0, 1), incomplete.stream().map(t -> t.cutoff().threadId())
                .collect(java.util.stream.Collectors.toSet()));
            for (Trace trace : incomplete) new TraceReplayer().replay(model.program(), trace);
        }
    }

    /** A real bug does not erase source scheduling or loop incompleteness categories. */
    @Test void failuresRetainBothKindsOfBoundEvidence() {
        var model = loader.load("program reasons { shared int x = 0; thread 0 { atomic { x = 1; } atomic { x = 1 % 0; } } thread 1 { while (true) limit 0 {} } }");
        var result = new ContextBoundedExplorer().explore(model.program(), null, null, null, 0);
        assertTrue(result.traces().stream().anyMatch(t -> t.outcome() == TraceOutcome.VIOLATION));
        assertEquals(Set.of(CutoffEvidence.Reason.LOOP_LIMIT, CutoffEvidence.Reason.PREEMPTION_BOUND),
            result.traces().stream().filter(t -> t.cutoff() != null).map(t -> t.cutoff().reason()).collect(java.util.stream.Collectors.toSet()));
        for (Trace trace : result.traces()) new TraceReplayer().replay(model.program(), trace);
    }

    /** A scheduling cutoff must reach partial results before a later state visit interrupts DFS. */
    @Test void resourceStopRetainsAlreadyEncounteredPreemptionWitness() {
        var model = loader.load("program interrupted { shared int x = 0; thread 0 { atomic { x = 1; } atomic when (x == 0) { x = 2; } } thread 1 { atomic { x = 0; } atomic { x = 3; } } }");
        TestResult partial = InterleaveRunner.builder().strategy(Strategy.CONTEXT_BOUNDED)
            .maxPreemptions(0).maxStates(4).build().run(model.program());
        assertTrue(partial.limitExceeded());
        assertEquals(List.of(CutoffEvidence.Reason.PREEMPTION_BOUND), partial.incompletenessReasons());
    }

    /** Earlier bound witnesses remain valid evidence when a later deepening pass hits its budget. */
    @Test void interruptedDeepeningRetainsEarlierLoopAndSchedulingEvidence() {
        var model = loader.load("program deepening { shared int x = 0; thread 0 { atomic {} while (true) limit 0 {} } thread 1 { atomic {} atomic {} } }");
        var first = new ContextBoundedExplorer().explore(model.program(), null, null, null, 0);
        TestResult partial = InterleaveRunner.builder().strategy(Strategy.CONTEXT_BOUNDED)
            .maxPreemptions(2).iterativeDeepening(true).maxStates((int) first.statesExplored() + 1).build().run(model.program());
        assertTrue(partial.limitExceeded());
        assertEquals(List.of(CutoffEvidence.Reason.LOOP_LIMIT, CutoffEvidence.Reason.PREEMPTION_BOUND), partial.incompletenessReasons());
        for (TraceRecord record : partial.incompleteTraces()) {
            new TraceReplayer().replay(model.program(), new Trace(record.threadIds(), record.outcomes(), record.outcome(), record.evidence()));
        }
    }

    /** Library source output records the actual approximate store, even when no absence proof exists. */
    @Test void libraryResultsPreserveApproximateStoreIdentity() {
        var model = loader.load("program approximate { shared int x = 0; thread 0 { atomic {} } }");
        TestResult result = InterleaveRunner.builder().strategy(Strategy.DFS)
            .stateStoreFactory(() -> new dev.samhb.interleave.state.BitstateStore(128, 2)).build().run(model.program());
        assertEquals("BITSTATE", com.google.gson.JsonParser.parseString(result.toJson()).getAsJsonObject().get("storeType").getAsString());
    }

    /** Shared validation errors point into original source rather than generated JSON declarations. */
    @Test void declarationDiagnosticsIdentifyTheOffendingInitializerAndLimit() {
        var wrong = assertThrows(RegistryException.class, () -> loader.load("program diagnostic {\nshared int x = false;\nthread 0 {} }"));
        assertTrue(wrong.getMessage().startsWith("<source>:2:16: type:"), wrong.getMessage());
        assertFalse(wrong.getMessage().contains("state.fields"));
        String elements = String.join(",", Collections.nCopies(65, "0"));
        var tooLong = assertThrows(RegistryException.class, () -> loader.load("program diagnostic {\nshared int[] x = [" + elements + "];\nthread 0 {} }"));
        assertTrue(tooLong.getMessage().startsWith("<source>:2:"));
        assertTrue(tooLong.getMessage().contains("limit: Array length exceeds 64"));
        var tooManySteps = assertThrows(RegistryException.class, () -> loader.load("program diagnostic {\n shared int x = 0;\n thread 0 { repeat 65 { atomic {} } }\n}"));
        assertTrue(tooManySteps.getMessage().startsWith("<source>:3:2: limit:"), tooManySteps.getMessage());
        String expression = "1";
        for (int i = 0; i < 11; i++) expression = "(" + expression + "+" + expression + ")";
        String source = "program diagnostic {\n shared int x = 0;\n thread 0 { repeat 2 { atomic { x = " + expression + "; } } }\n}";
        var tooManyExpressions = assertThrows(RegistryException.class, () -> loader.load(source));
        assertTrue(tooManyExpressions.getMessage().startsWith("<source>:3:2: limit:"), tooManyExpressions.getMessage());
    }
}
