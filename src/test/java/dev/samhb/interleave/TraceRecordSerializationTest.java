/** Complete strict-JSON trace-record oracles, including genuine source terminal evidence. */
package dev.samhb.interleave;

import com.google.gson.JsonObject;
import dev.samhb.interleave.core.*;
import dev.samhb.interleave.format.SourceProgramLoader;
import dev.samhb.interleave.search.*;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import static dev.samhb.interleave.testsupport.JsonAssertions.parseObject;
import static org.junit.jupiter.api.Assertions.*;

class TraceRecordSerializationTest {
    /** Exact source bytes independently hashed with Python hashlib and shasum. */
    private static final String FAILURE_SOURCE =
        "program record { shared int x = 0; thread 0 { atomic { x = 1 % 0; } } }";
    /** A zero-limit omitted action with genuine expansion ancestry. */
    private static final String CUTOFF_SOURCE =
        "program record { shared int x = 0; thread 0 { while (true) limit 0 {} } }";

    /** Complete legacy payloads retain ordered choices/outcomes, escaped strings, arrays and locals. */
    @Test
    void legacyPayloadRetainsAllTypedValuesAndEscapedStrings() {
        String hash = "hash\"\\\n\t<>&='雪";
        StateSnapshot snapshot = new StateSnapshot(
            Map.of("counter", 7, "values", new int[]{1, 4}, "flags", new boolean[]{true, false}),
            Map.of("2", Map.of("tmp", -2)));
        TraceRecord record = new TraceRecord(List.of(2, 0),
            List.of(StepOutcome.ADVANCED, StepOutcome.ASSERTION_FAILED), TraceOutcome.VIOLATION,
            hash, List.of(snapshot));
        JsonObject expected = parseObject("""
            {"threads":[2,0],"outcomes":["ADVANCED","ASSERTION_FAILED"],"outcome":"VIOLATION",
             "programHash":null,"states":[{"fields":{"counter":7,"values":[1,4],"flags":[true,false]},
             "locals":{"2":{"tmp":-2}}}]}
            """);
        expected.addProperty("programHash", "hash\"\\\n\t<>&='雪");
        assertEquals(expected, parseObject(record.toJson()));
    }

    /** Null/empty snapshot inputs normalize to an empty immutable value and omit optional wire fields.
     * @param nullStates whether the explicit snapshot argument is null rather than empty
     */
    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void nullAndEmptySnapshotsOmitAllOptionalFields(boolean nullStates) {
        TraceRecord record = new TraceRecord(List.of(), List.of(), TraceOutcome.COMPLETED, null,
            nullStates ? null : List.of());
        JsonObject expected = parseObject("""
            {"threads":[],"outcomes":[],"outcome":"COMPLETED","programHash":null}
            """);
        assertEquals(List.of(), record.states());
        assertNull(record.evidence());
        assertEquals(expected, parseObject(record.toJson()));
        assertEquals(expected, parseObject(new TraceRecord(List.of(), List.of(),
            TraceOutcome.COMPLETED, null).toJson()));
    }

    /** Enrichment preserves genuine source failure diagnostics and leaves the original record untouched. */
    @Test
    void sourceFailureRetainsCompleteEvidenceAfterSnapshotEnrichment() {
        TraceRecord original = sourceTrace(FAILURE_SOURCE).toRecord();
        TraceRecord enriched = original.withStates(List.of(StateSnapshot.ofFields(Map.of("x", 0))));
        JsonObject expected = parseObject("""
            {"threads":[0],"outcomes":["ASSERTION_FAILED"],"outcome":"VIOLATION","programHash":"",
             "states":[{"fields":{"x":0},"locals":{}}],
             "sourceDigest":"7fbbaf463008f20e53ffe0a8a50d5df07ab4d7827d077fed749d3a2587a604f4",
             "executedSteps":[{"instructionIndex":0,
               "source":{"file":"<source>","line":1,"column":47,"endLine":1,"endColumn":68},
               "label":"atomic { x = 1 % 0; }","expansionPath":[]}],
             "failure":{"kind":"RUNTIME_ERROR","phase":"STEP",
               "source":{"file":"<source>","line":1,"column":56,"endLine":1,"endColumn":66},
               "category":"MODULO_BY_ZERO"}}
            """);
        assertEquals(expected, parseObject(enriched.toJson()));
        assertEquals(original.evidence(), enriched.evidence());
        assertTrue(original.states().isEmpty());
        assertFalse(parseObject(original.toJson()).has("states"));
    }

    /** A cutoff remains an omitted action, with empty recorded choices but exact source/ancestry evidence. */
    @Test
    void sourceCutoffDoesNotInventAnExecutedAttempt() {
        TraceRecord record = sourceTrace(CUTOFF_SOURCE).toRecord();
        JsonObject expected = parseObject("""
            {"threads":[],"outcomes":[],"outcome":"INCOMPLETE","programHash":"",
             "sourceDigest":"21e419ad70e03cf280a0e2f7edb700f458663316abd36e4fc7c09a656c56581a",
             "executedSteps":[],"cutoff":{"reason":"LOOP_LIMIT","threadId":0,"instructionIndex":0,
               "limit":0,"iterations":0,
               "source":{"file":"<source>","line":1,"column":47,"endLine":1,"endColumn":70},
               "expansionPath":[{"sourceOffset":46,"iteration":0}]}}
            """);
        assertEquals(expected, parseObject(record.toJson()));
    }

    /** The carrier rejects misaligned outcomes and source metadata rather than publishing mismatched arrays. */
    @Test
    void constructorsRejectMisalignedChoicesAndSourceEvidence() {
        assertThrows(IllegalArgumentException.class, () -> new TraceRecord(List.of(0), List.of(),
            TraceOutcome.VIOLATION, ""));
        TraceEvidence genuine = sourceTrace(FAILURE_SOURCE).evidence();
        assertThrows(IllegalArgumentException.class, () -> new TraceRecord(List.of(), List.of(),
            TraceOutcome.VIOLATION, "", List.of(), genuine));
    }

    /** Explores a one-owner terminal fixture and replays its actual evidence without reconstruction.
     * @param source exact UTF-8 source text with one deterministic terminal path
     * @return the authentic emitted trace, verified with its actual exploration property
     */
    private static Trace sourceTrace(String source) {
        var model = new SourceProgramLoader().load(source);
        Invariant property = Invariant.observing(Set.of(), state -> true);
        DfsResult result = new DfsExplorer().explore(model.program(), property);
        assertEquals(1, result.traces().size());
        Trace trace = result.traces().getFirst();
        new TraceReplayer().replay(model.program(), trace, property);
        return trace;
    }
}
