/** Independent spans, labels and loop occurrence paths for executed and omitted source actions. */
package dev.samhb.interleave.format;

import dev.samhb.interleave.core.*;
import dev.samhb.interleave.format.dsl.DynamicState;
import dev.samhb.interleave.search.*;
import java.util.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import static org.junit.jupiter.api.Assertions.*;

class SourceLoopProvenanceContractTest {
    /**
     * Distinguishes both repeated copies, every while occurrence and its final bound check.
     * @param condition completing or permanently true while condition
     * @param outcome independently expected terminal classification
     * @param committed literal committed prefix length, excluding an omitted cutoff action
     */
    @ParameterizedTest
    @CsvSource(delimiter = '|', textBlock = """
        counter < 2 | COMPLETED  | 12
        true        | INCOMPLETE | 5
        """)
    void compiledAndRecordedLoopProvenanceMatchesTheOriginalSource(
            String condition, TraceOutcome outcome, int committed) {
        var model = new SourceProgramLoader().load(source(condition));
        var steps = model.program().threads().getFirst().steps();
        assertEquals(12, steps.size());

        var r0 = new ExecutedStep.Expansion(61, 0);
        var r1 = new ExecutedStep.Expansion(61, 1);
        var w0 = new ExecutedStep.Expansion(104, 0);
        var w1 = new ExecutedStep.Expansion(104, 1);
        var w2 = new ExecutedStep.Expansion(104, 2);
        List<List<ExecutedStep.Expansion>> paths = List.of(
            List.of(r0), List.of(r0, w0), List.of(r0, w0), List.of(r0, w1), List.of(r0, w1), List.of(r0, w2),
            List.of(r1), List.of(r1, w0), List.of(r1, w0), List.of(r1, w1), List.of(r1, w1), List.of(r1, w2));
        var reset = new SourceLocation("<source>", 5, 5, 5, 28);
        var loop = new SourceLocation("<source>", 6, 5, 8, 6);
        var body = new SourceLocation("<source>", 7, 7, 7, 40);
        List<SourceLocation> spans = List.of(
            reset, loop, body, loop, body, loop, reset, loop, body, loop, body, loop);
        String resetLabel = "atomic { counter = 0; }";
        String loopLabel = "while (" + condition + ") limit 2 {";
        String bodyLabel = "atomic { counter = counter + 1; }";
        List<String> labels = List.of(resetLabel, loopLabel, bodyLabel, loopLabel, bodyLabel, loopLabel,
            resetLabel, loopLabel, bodyLabel, loopLabel, bodyLabel, loopLabel);
        List<ExecutedStep> expected = new ArrayList<>();
        for (int pc = 0; pc < 12; pc++) {
            var identity = new ExecutedStep(pc, spans.get(pc), labels.get(pc), paths.get(pc));
            assertEquals(identity, ((SourceStep) steps.get(pc)).metadata(pc), "compiled identity at PC " + pc);
            expected.add(identity);
        }

        DfsResult result = new DfsExplorer().explore(model.program());
        assertEquals(1, result.traces().size());
        Trace trace = result.traces().getFirst();
        assertEquals(outcome, trace.outcome());
        assertEquals(Collections.nCopies(committed, 0), trace.threadIds());
        assertEquals(Collections.nCopies(committed, StepOutcome.ADVANCED), trace.outcomes());
        assertEquals(expected.subList(0, committed), trace.evidence().executedSteps());
        if (outcome == TraceOutcome.INCOMPLETE) {
            assertEquals(new CutoffEvidence(CutoffEvidence.Reason.LOOP_LIMIT, 0, 5, 2, 2,
                loop, List.of(r0, w2)), trace.cutoff());
        } else assertNull(trace.cutoff());

        Configuration end = new TraceReplayer().replay(model.program(), trace);
        assertEquals(List.of(committed), end.programCounters());
        assertEquals(2, ((DynamicState) end.state()).getInt("counter"));
    }

    /**
     * Keeps loop keywords at independent literal offsets while varying only the while condition.
     * @param condition bounded while expression, with no side effects
     * @return complete source with two repeats, a reset and two allowed increments per repeat
     */
    private static String source(String condition) {
        return "program loop_identity {\nshared int counter = 0;\nthread 0 {\n"
            + "  repeat 2 {\n    atomic { counter = 0; }\n    while (" + condition + ") limit 2 {\n"
            + "      atomic { counter = counter + 1; }\n    }\n  }\n}\n}";
    }
}
