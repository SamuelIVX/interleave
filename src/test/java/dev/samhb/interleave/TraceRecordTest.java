package dev.samhb.interleave;

import dev.samhb.interleave.core.StateSnapshot;
import dev.samhb.interleave.core.StepOutcome;
import dev.samhb.interleave.search.TraceOutcome;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class TraceRecordTest {

    @Test
    void constructor4Arg_defaultsStatesToEmpty() {
        TraceRecord record = new TraceRecord(
            List.of(0, 1),
            List.of(StepOutcome.ADVANCED, StepOutcome.ADVANCED),
            TraceOutcome.VIOLATION,
            "test-hash"
        );

        assertEquals(List.of(0, 1), record.threadIds());
        assertEquals(List.of(StepOutcome.ADVANCED, StepOutcome.ADVANCED), record.outcomes());
        assertEquals(TraceOutcome.VIOLATION, record.outcome());
        assertEquals("test-hash", record.programHash());
        assertTrue(record.states().isEmpty());

        String json = record.toJson();
        assertFalse(json.contains("\"states\""));
    }

    @Test
    void constructor5Arg_serializesStatesInJson() {
        StateSnapshot s0 = StateSnapshot.ofFields(Map.of("counter", 0));
        StateSnapshot s1 = StateSnapshot.ofFields(Map.of("counter", 1));

        TraceRecord record = new TraceRecord(
            List.of(0),
            List.of(StepOutcome.ADVANCED),
            TraceOutcome.COMPLETED,
            "test-hash",
            List.of(s0, s1)
        );

        assertEquals(2, record.states().size());
        assertEquals(0, record.states().get(0).fields().get("counter"));
        assertEquals(1, record.states().get(1).fields().get("counter"));

        String json = record.toJson();
        assertTrue(json.contains("\"states\":"));
        assertTrue(json.contains("\"counter\": 0") || json.contains("\"counter\":0"));
        assertTrue(json.contains("\"counter\": 1") || json.contains("\"counter\":1"));
    }

    @Test
    void withStates_createsEnrichedCopy() {
        TraceRecord original = new TraceRecord(
            List.of(0),
            List.of(StepOutcome.ADVANCED),
            TraceOutcome.COMPLETED,
            "test-hash"
        );

        StateSnapshot snap = StateSnapshot.ofFields(Map.of("x", 42));
        TraceRecord enriched = original.withStates(List.of(snap));

        assertEquals(original.threadIds(), enriched.threadIds());
        assertEquals(original.outcomes(), enriched.outcomes());
        assertEquals(original.outcome(), enriched.outcome());
        assertEquals(original.programHash(), enriched.programHash());
        assertEquals(1, enriched.states().size());
        assertEquals(42, enriched.states().get(0).fields().get("x"));
    }
}
