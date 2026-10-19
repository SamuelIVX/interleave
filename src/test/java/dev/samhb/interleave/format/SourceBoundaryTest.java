/** Grammar, trust-boundary limits and observation-point regression cases for source models. */
package dev.samhb.interleave.format;

import dev.samhb.interleave.*;
import dev.samhb.interleave.core.*;
import dev.samhb.interleave.format.dsl.DynamicState;
import dev.samhb.interleave.format.registry.RegistryException;
import dev.samhb.interleave.search.*;
import dev.samhb.interleave.state.HashingStateStore;
import java.nio.file.*;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import static org.junit.jupiter.api.Assertions.*;

class SourceBoundaryTest {
    private final SourceProgramLoader loader = new SourceProgramLoader();

    /** Wraps a body in a one-thread initialized source model. */
    private static String model(String body) {
        return "program bounded { shared int counter = 0; thread 0 { " + body + " } }";
    }

    /** Unsupported/malformed source is rejected instead of acquiring accidental semantics. */
    @ParameterizedTest
    @ValueSource(strings = {"counter = 1;", "atomic { atomic {} }", "while (true) {}", "repeat -1 {}",
        "atomic { counter++; }", "atomic { counter = 1 / 2; }", "if (1) {}", "atomic when (1) {}",
        "atomic { counter = false; }", "atomic { local.missing = 1; }", "atomic { counter = ; }", "if (true) {"})
    void rejectsMalformedOrUnsupportedBodies(String body) {
        assertThrows(RegistryException.class, () -> loader.load(model(body)));
    }

    /** Global and private expansion caps are inclusive and checked before allocating copies. */
    @Test void instructionAndNestingLimitsAreInclusive() {
        assertEquals(64, loader.load(model("atomic {} ".repeat(64))).program().threads().getFirst().steps().size());
        assertThrows(RegistryException.class, () -> loader.load(model("atomic {} ".repeat(65))));
        assertNotNull(loader.load(model("while (true) limit 63 {}")));
        assertThrows(RegistryException.class, () -> loader.load(model("while (true) limit 64 {}")));
        assertNotNull(loader.load(model("atomic { while (true) limit 63 {} }")));
        assertThrows(RegistryException.class, () -> loader.load(model("atomic { while (true) limit 64 {} }")));
        assertNotNull(loader.load(model("if (true) {".repeat(13) + "atomic {}" + "}".repeat(13))));
        assertThrows(RegistryException.class, () -> loader.load(model("if (true) {".repeat(14) + "atomic {}" + "}".repeat(14))));
        assertThrows(RegistryException.class, () -> loader.load(model("repeat 2147483647 { repeat 2147483647 { atomic {} } }")));
    }

    /** Expanded expressions count even when a compiler could share their immutable nodes. */
    @Test void expressionBudgetsAndDepthAreIndependent() {
        assertNotNull(loader.load(model("atomic { counter = " + "1+".repeat(15) + "1; }")));
        assertThrows(RegistryException.class, () -> loader.load(model("atomic { counter = " + "1+".repeat(16) + "1; }")));
        String expression = "1";
        for (int i = 0; i < 11; i++) expression = "(" + expression + "+" + expression + ")";
        String body = "atomic { counter = " + expression + "; }";
        assertNotNull(loader.load(model(body)));
        assertThrows(RegistryException.class, () -> loader.load(model("repeat 2 { " + body + " }")));
    }

    /** Strict file decoding and exact byte identity include comments and newline style. */
    @Test void validatesUtf8AndPreservesOriginalSourceLocations(@TempDir Path directory) throws Exception {
        Path file = directory.resolve("model.ilv");
        String source = "program text {\r\n/* 😀 */ shared int counter = 0;\r\nthread 0 { atomic {} }\r\n}";
        Files.writeString(file, source);
        var loaded = loader.loadFromFile(file);
        Trace trace = new DfsExplorer().explore(loaded.program()).traces().getFirst();
        assertEquals(3, trace.evidence().executedSteps().getFirst().source().line());
        assertEquals("model.ilv", trace.evidence().executedSteps().getFirst().source().file());
        assertNotEquals(loaded.program().sourceDigest(), loader.load(source.replace("\r\n", "\n")).program().sourceDigest());
        Files.write(file, new byte[] {(byte) 0xc3, 0x28});
        assertThrows(RegistryException.class, () -> loader.loadFromFile(file));
        assertThrows(RegistryException.class, () -> loader.load(" ".repeat(SourceProgramLoader.MAX_SOURCE_BYTES + 1)));
        assertThrows(RegistryException.class, () -> loader.load(model("/* unfinished")));
        assertThrows(RegistryException.class, () -> loader.load(model("atomic {}") + " trailing"));
    }

    /** Property evaluation failures retain the actual observation point and successful outcomes. */
    @Test void propertyErrorsNeverInventFailedActionsOrRollBackCommittedWrites() {
        var model = loader.load("program property { shared int counter = 0; thread 0 { atomic { counter = 1; } } invariant always (counter == 0 || 1 % (counter - 1) == 0); }");
        Invariant property = model.invariant().orElseThrow();
        Trace trace = new DfsExplorer().explore(model.program(), property).traces().getFirst();
        assertEquals(List.of(StepOutcome.ADVANCED), trace.outcomes());
        assertEquals(FailureEvidence.Phase.PROPERTY, trace.failure().phase());
        assertEquals(FailureEvidence.Kind.RUNTIME_ERROR, trace.failure().kind());
        assertEquals(1, ((DynamicState) new TraceReplayer().replay(model.program(), trace, property).state()).getInt("counter"));
        var atStart = loader.load("program initial { shared int counter = 0; thread 0 {} invariant always (1 % counter == 0); }");
        Trace first = new DfsExplorer().explore(atStart.program(), atStart.invariant().orElseThrow()).traces().getFirst();
        assertEquals(List.of(), first.threadIds());
        assertEquals(1, new TraceReplayer().captureSnapshots(atStart.program(), first).size());
    }

    /** Earlier cutoff witnesses survive a later resource stop with its independent cause. */
    @Test void resourceLimitsPreserveEarlierLoopEvidence() {
        var model = loader.load("program limits { shared int counter = 0; thread 0 { while (true) limit 0 {} } thread 1 { atomic { counter = 1; } } }");
        TestResult result = InterleaveRunner.builder().strategy(Strategy.DFS).maxStates(2).build().run(model.program());
        assertTrue(result.limitExceeded());
        assertEquals(ResourceLimitReason.MAX_STATES, result.resourceLimitReason());
        assertEquals(List.of(CutoffEvidence.Reason.LOOP_LIMIT), result.incompletenessReasons());
        assertTrue(result.toJson().contains("MAX_STATES"));
    }

    /** Iterative deepening cannot spend larger K values on a loop-only cutoff. */
    @Test void loopOnlyCutoffDoesNotTriggerSchedulingDeepening() {
        var model = loader.load(model("while (true) limit 0 {}"));
        AtomicInteger stores = new AtomicInteger();
        TestResult result = InterleaveRunner.builder().strategy(Strategy.CONTEXT_BOUNDED)
            .maxPreemptions(5).iterativeDeepening(true).stateStoreFactory(() -> {
                stores.incrementAndGet(); return new HashingStateStore();
            }).build().run(model.program());
        assertEquals(1, stores.get());
        assertEquals(List.of(CutoffEvidence.Reason.LOOP_LIMIT), result.incompletenessReasons());
    }
}
