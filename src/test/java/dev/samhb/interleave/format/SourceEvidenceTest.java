/** Corrupt evidence cannot change source execution, terminal classification or scheduling cost. */
package dev.samhb.interleave.format;

import dev.samhb.interleave.cb.ContextBoundedExplorer;
import dev.samhb.interleave.core.*;
import dev.samhb.interleave.search.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class SourceEvidenceTest {
    private final SourceProgramLoader loader = new SourceProgramLoader();

    /** Supplied cutoff reason/limit/PC must reproduce, and cannot invent terminal completion. */
    @Test void loopWitnessValidationRejectsForgedLimitAndTerminalKind() {
        var model = loader.load("program evidence { shared int x = 0; thread 0 { while (true) limit 1 { atomic { x = x + 1; } } } invariant always (true); }");
        Invariant property = model.invariant().orElseThrow();
        Trace trace = new DfsExplorer().explore(model.program(), property).traces().getFirst();
        CutoffEvidence actual = trace.cutoff();
        assertNotNull(actual);
        assertEquals(1, actual.iterations());
        CutoffEvidence forged = new CutoffEvidence(actual.reason(), actual.threadId(), actual.instructionIndex(), 0, 0,
            actual.source(), actual.expansionPath());
        Trace wrong = new Trace(trace.threadIds(), trace.outcomes(), trace.outcome(),
            new TraceEvidence(model.program().sourceDigest(), trace.evidence().executedSteps(), null, forged));
        assertThrows(CorruptedTraceException.class, () -> new TraceReplayer().replay(model.program(), wrong, property));
        Trace completed = new Trace(trace.threadIds(), trace.outcomes(), TraceOutcome.COMPLETED,
            new TraceEvidence(model.program().sourceDigest(), trace.evidence().executedSteps(), null, null));
        assertThrows(CorruptedTraceException.class, () -> new TraceReplayer().replay(model.program(), completed, property));
        Trace missing = new Trace(trace.threadIds(), trace.outcomes(), TraceOutcome.INCOMPLETE,
            new TraceEvidence(model.program().sourceDigest(), trace.evidence().executedSteps(), null, null));
        assertThrows(CorruptedTraceException.class, () -> new TraceReplayer().replay(model.program(), missing, property));
        new TraceReplayer().replay(model.program(), trace, property);
    }

    /** A bound witness requires an in-bound prefix and one enabled choice that would exceed K. */
    @Test void schedulingWitnessRejectsPrefixAlreadyOverBoundAndInsufficientCost() {
        var model = loader.load("program scheduling { shared int x = 0; thread 0 { atomic {} atomic { x = 1; } } thread 1 { atomic {} atomic { x = 2; } } }");
        Trace trace = new ContextBoundedExplorer().explore(model.program(), null, null, null, 0).traces().stream()
            .filter(t -> t.cutoff() != null).findFirst().orElseThrow();
        CutoffEvidence actual = trace.cutoff();
        List<CutoffEvidence> wrongIdentities = List.of(
            new CutoffEvidence(actual.reason(), actual.threadId(), actual.instructionIndex(), actual.limit(), null,
                new SourceLocation("wrong.ilv", 1, 1, 1, 2), actual.expansionPath()),
            new CutoffEvidence(actual.reason(), actual.threadId(), actual.instructionIndex(), actual.limit(), null,
                actual.source(), List.of(new ExecutedStep.Expansion(0, 1))),
            new CutoffEvidence(actual.reason(), actual.threadId(), actual.instructionIndex(), actual.limit(), 0,
                actual.source(), actual.expansionPath()));
        for (CutoffEvidence wrongIdentity : wrongIdentities) {
            Trace wrong = new Trace(trace.threadIds(), trace.outcomes(), trace.outcome(),
                new TraceEvidence(model.program().sourceDigest(), trace.evidence().executedSteps(), null, wrongIdentity));
            assertThrows(CorruptedTraceException.class, () -> new TraceReplayer().replay(model.program(), wrong));
        }
        new TraceReplayer().replay(model.program(), trace);
        CutoffEvidence higher = new CutoffEvidence(actual.reason(), actual.threadId(), actual.instructionIndex(), 1, null,
            actual.source(), actual.expansionPath());
        Trace wrongCost = new Trace(trace.threadIds(), trace.outcomes(), trace.outcome(),
            new TraceEvidence(model.program().sourceDigest(), trace.evidence().executedSteps(), null, higher));
        assertThrows(CorruptedTraceException.class, () -> new TraceReplayer().replay(model.program(), wrongCost));
        ExecutedStep omitted = ((SourceStep) model.program().threads().get(1).steps().get(1)).metadata(1);
        CutoffEvidence over = new CutoffEvidence(CutoffEvidence.Reason.PREEMPTION_BOUND, 1, 1, 0, null,
            omitted.source(), omitted.expansionPath());
        Trace overPrefix = Trace.create(model.program(), List.of(0, 1, 0), Collections.nCopies(3, StepOutcome.ADVANCED),
            TraceOutcome.INCOMPLETE, null, over);
        assertThrows(CorruptedTraceException.class, () -> new TraceReplayer().replay(model.program(), overPrefix));
    }

    /** Two voluntary prefix switches cost two; an enabled omitted switch would cost three. */
    @Test void schedulingWitnessAcceptsAnInBoundPrefixWithActualPreemptions() {
        var model = loader.load("program switches { shared int x = 0; thread 0 { atomic {} atomic {} atomic {} } thread 1 { atomic {} atomic {} atomic {} } }");
        ExecutedStep omitted = ((SourceStep) model.program().threads().get(1).steps().get(1)).metadata(1);
        CutoffEvidence cutoff = new CutoffEvidence(CutoffEvidence.Reason.PREEMPTION_BOUND, 1, 1, 2, null,
            omitted.source(), omitted.expansionPath());
        Trace trace = Trace.create(model.program(), List.of(0, 1, 0), Collections.nCopies(3, StepOutcome.ADVANCED),
            TraceOutcome.INCOMPLETE, null, cutoff);
        assertEquals(List.of(2, 1), new TraceReplayer().replay(model.program(), trace).programCounters());
    }

    /** Error categories depend on evaluator prefixes rather than user-chosen array identifiers. */
    @Test void arrayNamedZeroRemainsAnArrayBoundsFailure() {
        for (String body : List.of("thread 0 { atomic { x = zero[0]; } }", "thread 0 {} invariant always (zero[0] == 0);")) {
            var model = loader.load("program categories { shared int[] zero = []; shared int x = 0; " + body + " }");
            Trace trace = new DfsExplorer().explore(model.program(), model.invariant().orElse(null)).traces().getFirst();
            assertEquals(FailureEvidence.Category.ARRAY_BOUNDS, trace.failure().category());
        }
    }

    /** A property error is reproducible evidence with a distinct kind from a false predicate. */
    @Test void propertyFailureClassificationCannotBeOverriddenByMetadata() {
        var model = loader.load("program property { shared int x = 0; thread 0 {} invariant always (1 % x == 0); }");
        Invariant property = model.invariant().orElseThrow();
        Trace trace = new DfsExplorer().explore(model.program(), property).traces().getFirst();
        FailureEvidence falsePredicate = new FailureEvidence(FailureEvidence.Kind.INVARIANT,
            FailureEvidence.Phase.PROPERTY, trace.failure().source(), null);
        Trace wrong = new Trace(trace.threadIds(), trace.outcomes(), trace.outcome(),
            new TraceEvidence(model.program().sourceDigest(), List.of(), falsePredicate, null));
        assertThrows(CorruptedTraceException.class, () -> new TraceReplayer().replay(model.program(), wrong, property));
    }
}
