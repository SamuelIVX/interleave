/** Shared approximate CBS iteration must not infer exhaustion from missing cutoff evidence. */
package dev.samhb.interleave;

import dev.samhb.interleave.format.SourceProgramLoader;
import dev.samhb.interleave.report.*;
import dev.samhb.interleave.state.*;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ApproximateCbsIterationTest {
    /** One bit hides all successor visits and therefore all preemption-cutoff evidence. */
    private static final String SOURCE = "program pair { shared int x = 0; thread 0 { atomic { x = x + 1; } } thread 1 { atomic { x = x + 1; } } }";

    /** Owned approximate CBS reaches later passes without sharing filters or losing resource identity.
     * @throws java.io.IOException if writing the actual resource fixture fails
     */
    @Test void ownedSymmetrySupportsCbsAndGlobalResourceAllowance() throws java.io.IOException {
        var model = new SourceProgramLoader().load(SOURCE);
        var result = InterleaveRunner.builder().symmetryReduction(true).symmetryBitstate(1,4)
            .strategy(Strategy.CONTEXT_BOUNDED).maxPreemptions(2).iterativeDeepening(true).maxStates(2).build().run(model.program());
        assertTrue(result.limitExceeded()); assertTrue(result.symmetryReduction());
        assertEquals(2,result.statesExplored());
        assertEquals(java.util.List.of(),result.resourcePrefix().orElseThrow().threadIds());
        var json = com.google.gson.JsonParser.parseString(result.toJson()).getAsJsonObject();
        assertEquals("BITSTATE",json.get("storeType").getAsString());
        assertEquals("MAX_STATES",json.get("resourceLimitReason").getAsString());
        var rows = new BenchmarkHarness(1,4,Set.of(StoreType.BITSTATE),Set.of("CONTEXT_BOUNDED"),2,true,true).runProgram(model);
        assertEquals(2,rows.getFirst().preemptionsUsed()); assertTrue(rows.getFirst().symmetryReduction());
        assertEquals("APPROXIMATE_PASS",rows.getFirst().verdict());
        var later = InterleaveRunner.builder().symmetryReduction(true).symmetryBitstate(1_000_003,4)
            .strategy(Strategy.CONTEXT_BOUNDED).maxPreemptions(2).iterativeDeepening(true).maxStates(5).build().run(model.program());
        assertTrue(later.limitExceeded());assertEquals(5,later.statesExplored());
        var prefix = later.resourcePrefix().orElseThrow();
        assertEquals(java.util.List.of(0),prefix.threadIds());assertEquals(java.util.List.of(1,0),prefix.programCounters());
        assertEquals(1,prefix.terminalState().fields().get("x"));
        assertEquals(2,prefix.replay(model.program(),null).states().size());
        java.nio.file.Files.createDirectories(java.nio.file.Path.of("build"));
        java.nio.file.Files.writeString(java.nio.file.Path.of("build/source-approximate-cbs-resource.json"),later.toJson());
    }

    /** The ordinary approximate runner must reach the ceiling even without an observed cutoff. */
    @Test void hiddenCutoffsDoNotStopTheOrdinaryApproximateRunner() {
        var model = new SourceProgramLoader().load(SOURCE);
        var passes = new AtomicInteger();
        var runner = InterleaveRunner.builder().strategy(Strategy.CONTEXT_BOUNDED).maxPreemptions(2)
            .iterativeDeepening(true).stateStoreFactory(() -> { passes.incrementAndGet(); return new BitstateStore(1,4,2); }).build();
        var result = runner.run(model.program());
        assertEquals(3,passes.get()); assertEquals(1,result.statesExplored());
        assertFalse(result.hasViolation()); assertFalse(result.hasIncomplete());
    }

    /** The report records the producing bound, while exact search retains its exhaustion shortcut. */
    @Test void harnessApproximationReachesCeilingAndExactStillStopsEarly() {
        var model = new SourceProgramLoader().load(SOURCE);
        var rows = new BenchmarkHarness(1,4,Set.of(StoreType.BITSTATE),Set.of("CONTEXT_BOUNDED"),2,true).runProgram(model);
        assertEquals(2,rows.getFirst().preemptionsUsed());
        assertEquals("APPROXIMATE_PASS",rows.getFirst().verdict());
        var exact = new BenchmarkHarness(1,4,Set.of(StoreType.EXACT),Set.of("CONTEXT_BOUNDED"),2,true).runProgram(model);
        assertEquals(0,exact.getFirst().preemptionsUsed());
        var passes = new AtomicInteger();
        InterleaveRunner.builder().strategy(Strategy.CONTEXT_BOUNDED).maxPreemptions(2).iterativeDeepening(true)
            .stateStoreFactory(() -> { passes.incrementAndGet(); return new HashingStateStore(); }).build().run(model.program());
        assertEquals(1,passes.get());
    }

    /** External approximate factories retain identity reuse rejection despite custom value equality. */
    @Test void externalApproximateFactoriesUseIdentityRatherThanEquals() {
        var model = new SourceProgramLoader().load(SOURCE);
        var passes = new AtomicInteger();
        var result = InterleaveRunner.builder().strategy(Strategy.CONTEXT_BOUNDED).maxPreemptions(2).iterativeDeepening(true)
            .stateStoreFactory(() -> { passes.incrementAndGet(); return new EqualApproximateStore(); }).build().run(model.program());
        assertEquals(3,passes.get());assertFalse(result.hasViolation());
        var shared = new EqualApproximateStore();
        assertThrows(IllegalStateException.class, () -> InterleaveRunner.builder().strategy(Strategy.CONTEXT_BOUNDED)
            .maxPreemptions(2).iterativeDeepening(true).stateStoreFactory(() -> shared).build().run(model.program()));
    }

    /** Earlier modeled cutoff evidence and the active pass's resource interruption stay distinct. */
    @Test void earlierLoopCutoffSurvivesALaterApproximateResourceStop() {
        var model = new SourceProgramLoader().load("program loops { shared int x = 0; thread 0 { while (true) limit 0 {} } thread 1 { while (true) limit 0 {} } }");
        var result = InterleaveRunner.builder().symmetryReduction(true).symmetryBitstate(1,4)
            .strategy(Strategy.CONTEXT_BOUNDED).maxPreemptions(2).iterativeDeepening(true).maxStates(2).build().run(model.program());
        assertTrue(result.limitExceeded());assertTrue(result.hasIncomplete());
        assertEquals(java.util.List.of(dev.samhb.interleave.core.CutoffEvidence.Reason.LOOP_LIMIT),result.incompletenessReasons());
        var prefix = result.resourcePrefix().orElseThrow();
        assertEquals(java.util.List.of(),prefix.threadIds());assertNull(prefix.evidence().cutoff());
        assertEquals(dev.samhb.interleave.core.ResourceLimitReason.MAX_STATES,prefix.reason());
        assertEquals(0,prefix.terminalState().fields().get("x"));
    }

    /** Real one-bit membership with deliberately colliding user equality. */
    private static final class EqualApproximateStore implements dev.samhb.interleave.search.ApproximateStateStore {
        /** Actual ordinary approximate membership, not a scripted lookup. */
        private final BitstateStore delegate = new BitstateStore(1,4,2);
        /** {@inheritDoc} */
        @Override public boolean isVisited(dev.samhb.interleave.core.Configuration c) { return delegate.isVisited(c); }
        /** {@inheritDoc} */
        @Override public void markVisited(dev.samhb.interleave.core.Configuration c) { delegate.markVisited(c); }
        /** {@inheritDoc} */
        @Override public boolean isVisited(dev.samhb.interleave.core.Configuration c,int last,int cost) { return delegate.isVisited(c,last,cost); }
        /** {@inheritDoc} */
        @Override public void markVisited(dev.samhb.interleave.core.Configuration c,int last,int cost) { delegate.markVisited(c,last,cost); }
        /** {@inheritDoc} */
        @Override public void clear() { delegate.clear(); }
        /** {@inheritDoc} */
        @Override public double estimatedFalsePositiveRate() { return delegate.estimatedFalsePositiveRate(); }
        /** {@inheritDoc} */
        @Override public int bitCount() { return delegate.bitCount(); }
        /** {@inheritDoc} */
        @Override public double bitDensity() { return delegate.bitDensity(); }
        /** {@inheritDoc} */
        @Override public int maxPreemptions() { return delegate.maxPreemptions(); }
        /** Distinct adapters intentionally compare equal; factory isolation still depends on identity.
         * @param other compared object
         * @return true for any adapter of this test type
         */
        @Override public boolean equals(Object other) { return other instanceof EqualApproximateStore; }
        /** Equal adapters deliberately share one user hash.
         * @return zero
         */
        @Override public int hashCode() { return 0; }
    }
}
