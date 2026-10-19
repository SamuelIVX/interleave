/** Cross-boundary source acceptance cases with explicit values, schedules and malformed evidence. */
package dev.samhb.interleave.format;

import dev.samhb.interleave.*;
import dev.samhb.interleave.bugs.BenchmarkProgram;
import dev.samhb.interleave.core.*;
import dev.samhb.interleave.format.dsl.DynamicState;
import dev.samhb.interleave.format.registry.RegistryException;
import dev.samhb.interleave.minimize.DeltaDebugger;
import dev.samhb.interleave.por.StaticPorExplorer;
import dev.samhb.interleave.report.*;
import dev.samhb.interleave.search.*;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class SourceAcceptanceTest {
    private final SourceProgramLoader loader = new SourceProgramLoader();

    /** Private loop allowances reset for each enclosing invocation, including local array values. */
    @Test void nestedInvocationsAndLocalArraysRemainIndependent() {
        var model = loader.load("""
            program nested { shared int counter = 0; local int[] scratch = [0];
              thread 0 { repeat 2 { atomic { local.scratch[0] = 0; }
                while (local.scratch[0] < 2) limit 2 {
                  atomic { counter = counter + 1; local.scratch[0] = local.scratch[0] + 1; }
                }
              } }
              invariant final (counter == 4);
            }
            """);
        Trace trace = new DfsExplorer().explore(model.program(), model.invariant().orElseThrow()).traces().getFirst();
        assertEquals(TraceOutcome.COMPLETED, trace.outcome());
        assertEquals(4, ((DynamicState) new TraceReplayer().replay(model.program(), trace).state()).getInt("counter"));
        assertTrue(trace.evidence().executedSteps().stream().anyMatch(s -> s.expansionPath().size() == 2));
    }

    /** No property observation occurs between private writes within an atomic block. */
    @Test void privateIntermediateStatesAndShortCircuitErrorsAreUnobservable() {
        var model = loader.load("program private_state { shared int counter = 0; thread 0 { atomic { counter = 9; if (true || 1 % 0 == 0) { counter = 0; } } } invariant always (counter == 0); }");
        var execution = new ExecutionDriver().runDetailed(model.program(), new Schedule(List.of(0)), model.invariant().orElseThrow());
        assertEquals(ScheduleExecution.Kind.COMPLETED, execution.kind());
        assertEquals(2, execution.states().size());
        assertEquals(execution.states().getFirst(), execution.states().getLast());
    }

    /** The committed producer makes the consumer enabled; without it the model genuinely deadlocks. */
    @Test void guardWaitingAndDeadlockAreDifferentFromLoopCutoff() {
        var handoff = loader.loadFromFile(Path.of("examples/programs/guarded-handoff.ilv"));
        assertEquals(TraceOutcome.COMPLETED, new DfsExplorer().explore(handoff.program(), handoff.invariant().orElseThrow()).traces().getFirst().outcome());
        var dead = loader.load("program waiting { shared bool ready = false; thread 0 { atomic when (ready) {} } }");
        Trace trace = new DfsExplorer().explore(dead.program()).traces().getFirst();
        assertEquals(TraceOutcome.DEADLOCK, trace.outcome());
        assertNull(trace.cutoff());
        var guardError = loader.load("program guard_error { shared int counter = 0; thread 0 { atomic when (1 % counter == 0) {} } }");
        assertEquals(FailureEvidence.Kind.RUNTIME_ERROR, new DfsExplorer().explore(guardError.program()).traces().getFirst().failure().kind());
    }

    /** Minimization must regenerate source PCs when deleting a choice changes the selected arm. */
    @Test void minimizationRebuildsChangedBranchAndRejectsStaleSourceIdentity() {
        var model = loader.load("program changing { shared bool flag = false; shared int value = 0; thread 0 { if (flag) { atomic { value = 1; } } else { atomic { value = 2; } } } thread 1 { atomic { flag = true; } } invariant always (value == 0); }");
        Invariant property = model.invariant().orElseThrow();
        Trace original = new ExecutionDriver().runDetailed(model.program(), new Schedule(List.of(1, 0, 0)), property).trace();
        Trace minimal = new DeltaDebugger().minimize(model.program(), original, TraceOutcome.VIOLATION, property);
        assertEquals(List.of(0, 0), minimal.threadIds());
        assertEquals(List.of(0, 2), minimal.evidence().executedSteps().stream().map(ExecutedStep::instructionIndex).toList());
        new TraceReplayer().replay(model.program(), minimal, property);
        Trace wrongDigest = new Trace(minimal.threadIds(), minimal.outcomes(), minimal.outcome(),
            new TraceEvidence("0".repeat(64), minimal.evidence().executedSteps(), minimal.failure(), null));
        assertThrows(CorruptedTraceException.class, () -> new TraceReplayer().replay(model.program(), wrongDigest, property));
        assertThrows(IllegalArgumentException.class, () -> new Trace(minimal.threadIds(), minimal.outcomes(), minimal.outcome(),
            new TraceEvidence(model.program().sourceDigest(), List.of(), null, null)));
        assertEquals(minimal.evidence(), minimal.toRecord().withStates(List.of()).evidence());
    }

    /** Every teaching fixture is loaded from disk and attested using its actual source program. */
    @Test void teachingFixturesMatchTheirDeclaredOrExplicitTerminalOracles() throws Exception {
        Map<String, String> expected = Map.of("split-increment", "VIOLATION", "atomic-increment", "PASS",
            "branching-flag", "PASS", "bounded-loops", "INCOMPLETE", "atomic-cutoff", "INCOMPLETE",
            "atomic-runtime-error", "VIOLATION", "guarded-handoff", "PASS");
        for (var entry : expected.entrySet()) {
            BenchmarkProgram model = loader.loadFromFile(Path.of("examples/programs/" + entry.getKey() + ".ilv"));
            var results = new BenchmarkHarness().runProgram(model);
            assertTrue(results.stream().allMatch(row -> (entry.getValue().equals(row.verdict()) || entry.getValue().equals("PASS") && row.verdict().equals("APPROXIMATE_PASS"))), entry.getKey());
            var report = com.google.gson.JsonParser.parseString(new ReportWriter(results, List.of(model)).writeJson()).getAsJsonObject();
            assertTrue(report.get("soundness").getAsBoolean());
            for (var row : report.getAsJsonArray("benchmarks")) if (row.getAsJsonObject().has("failingTrace")) {
                var failing = row.getAsJsonObject().getAsJsonObject("failingTrace");
                assertEquals(failing.get("threads"), failing.get("threadIds"));
            }
        }
    }

    /** Declarations, identifiers and byte budgets are inclusive independently of expression limits. */
    @Test void declarationAndEncodingLimitsRejectOversizeInput() {
        String fields = "";
        for (int i = 0; i < 32; i++) fields += "shared int f" + i + " = 0;";
        String locals = "";
        for (int i = 0; i < 8; i++) locals += "local bool l" + i + " = false;";
        assertNotNull(loader.load("program caps { " + fields + locals + "thread 0 {} }"));
        String tooManyFields = fields + "shared int extra = 0;";
        assertThrows(RegistryException.class, () -> loader.load("program caps { " + tooManyFields + "thread 0 {} }"));
        String tooManyLocals = fields + locals + "local int extra = 0;";
        assertThrows(RegistryException.class, () -> loader.load("program caps { " + tooManyLocals + "thread 0 {} }"));
        String base = "program caps { shared int counter = 0; ";
        assertNotNull(loader.load(base + "thread 0 {} thread 1 {} thread 2 {} thread 3 {} thread 4 {} thread 5 {} thread 6 {} thread 7 {} }"));
        assertThrows(RegistryException.class, () -> loader.load(base + "thread 0 {} thread 2 {} }"));
        assertThrows(RegistryException.class, () -> loader.load(base + "thread 0 {} thread 1 {} thread 2 {} thread 3 {} thread 4 {} thread 5 {} thread 6 {} thread 7 {} thread 8 {} }"));
        String array64 = String.join(",", Collections.nCopies(64, "0"));
        assertNotNull(loader.load("program arrays { shared int[] values = [" + array64 + "]; local int[] empty = []; thread 0 {} }"));
        assertThrows(RegistryException.class, () -> loader.load("program arrays { shared int[] values = [" + array64 + ",0]; thread 0 {} }"));
        String identifier = "x".repeat(64);
        assertNotNull(loader.load("program caps { shared int " + identifier + " = 0; thread 0 {} }"));
        assertThrows(RegistryException.class, () -> loader.load("program caps { shared int " + identifier + "x = 0; thread 0 {} }"));
        assertThrows(RegistryException.class, () -> loader.load("/*" + "é".repeat(524288) + "*/"));
        assertThrows(RegistryException.class, () -> loader.load("\ud800"));
    }

    /** Both unchosen private arms and final-loop conditions must contribute complete footprints. */
    @Test void atomicFootprintsIncludeGuardsConditionsAndBothArms() {
        var model = loader.load("program footprints { shared int flag = 0; shared int left = 0; shared int right = 0; shared int target = 0; shared int[] values = [0]; thread 0 { atomic when (flag >= 0) { if (left == 0) { target = right; } else { target = values[flag]; } while (right < 0) limit 0 {} } } }");
        Step atomic = model.program().threads().getFirst().steps().getFirst();
        assertEquals(Set.of(MemoryLocation.of("flag"), MemoryLocation.of("left"), MemoryLocation.of("right"), MemoryLocation.of("values")), atomic.reads());
        assertEquals(Set.of(MemoryLocation.of("target")), atomic.writes());
    }

    /** Array runtime failures carry the inner source span and preserve the last committed state. */
    @Test void arrayErrorsClassifyBothStepAndPropertyFailures() {
        var model = loader.load("program arrays {\n shared int[] values = [0];\n thread 0 { atomic {\n values[0] = 9;\n values[2] = 1;\n } } }");
        Trace failure = new DfsExplorer().explore(model.program()).traces().getFirst();
        assertEquals(FailureEvidence.Category.ARRAY_BOUNDS, failure.failure().category());
        assertEquals(5, failure.failure().source().line());
        assertEquals(2, new TraceReplayer().captureSnapshots(model.program(), failure).size());
        var property = loader.load("program property { shared int[] values = []; thread 0 {} invariant final (values[0] == 0); }");
        Trace predicate = new DfsExplorer().explore(property.program(), property.invariant().orElseThrow()).traces().getFirst();
        assertEquals(FailureEvidence.Category.ARRAY_BOUNDS, predicate.failure().category());
        assertEquals(FailureEvidence.Phase.PROPERTY, predicate.failure().phase());
        new TraceReplayer().replay(property.program(), predicate, property.invariant().orElseThrow());
    }

    /** A terminal prepared block is evaluated once per parent even when it expands all candidates. */
    @Test void preparedCutoffIsReusedDuringConservativeExpansion() {
        var model = loader.load("program prepared { shared int counter = 0; thread 0 { atomic { while (true) limit 0 {} } } thread 1 { atomic { counter = 1; } } }");
        SourceStep original = (SourceStep) model.program().threads().getFirst().steps().getFirst();
        AtomicInteger attempts = new AtomicInteger();
        SourceStep counted = new SourceStep() {
            /** Delegates immutable production footprints. */
            public Set<MemoryLocation> reads() { return original.reads(); }
            /** Delegates immutable production footprints. */
            public Set<MemoryLocation> writes() { return original.writes(); }
            /** Delegates guard semantics without external modeled effects. */
            public boolean enabled(SharedState state) { return original.enabled(state); }
            /** Counts harness attempts only; no counter enters modeled state. */
            public StepTransition transition(List<ModelThread> threads, Configuration parent, int owner, SharedState scratch) {
                attempts.incrementAndGet(); return original.transition(threads, parent, owner, scratch);
            }
            /** Delegates actual source identity. */
            public ExecutedStep metadata(int pc) { return original.metadata(pc); }
        };
        Program program = new Program(model.program().initialConfiguration().state(),
            List.of(new ModelThread(0, List.of(counted)), model.program().threads().get(1)), model.program().sourceDigest());
        assertTrue(new StaticPorExplorer().explore(program).traces().stream().anyMatch(t -> t.outcome() == TraceOutcome.INCOMPLETE));
        assertEquals(2, attempts.get());
    }
}
