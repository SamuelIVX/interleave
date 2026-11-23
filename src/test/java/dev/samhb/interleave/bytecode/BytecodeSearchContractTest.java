/** Exact byte payload membership, checker interruptions and real internal witness choices. */
package dev.samhb.interleave.bytecode;

import java.nio.file.Path;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

class BytecodeSearchContractTest {
    /** Different terminal int payloads collide under Arrays.hashCode and must remain distinct.
     * @param directory compiler workspace
     * @throws Exception for fixture failures */
    @Test void hashCollisionsStillCompareCompletePayloads(@TempDir Path directory) throws Exception {
        var classes=BytecodeFixtures.compile(directory,26,"""
            @Model public class Fixture {public int x;public Fixture(){}
              @Actor(id=0)public void first(){x=31;} @Actor(id=1)public void second(){x=256;}
            }
            """);
        var program=BytecodeLoader.load(classes,"Fixture",BytecodeLimits.defaults());
        var result=new BytecodeExplorer(program,BytecodeScheduler.Mode.REFERENCE).explore(100000);
        assertTrue(result.exhaustive());assertEquals(2,result.witnesses().size());
        var first=result.witnesses().get(0).state().canonicalBytes();var second=result.witnesses().get(1).state().canonicalBytes();
        assertFalse(Arrays.equals(first,second));assertEquals(Arrays.hashCode(first),Arrays.hashCode(second));
        assertEquals(Set.of(31,256),result.witnesses().stream().map(w->((BytecodeState.Int)w.state().fixtureField("x")).value()).collect(java.util.stream.Collectors.toSet()));
        // Seven stages per actor; the sixteen positions after both writes have two possible values:49+16=65.
        // Independent Python stage enumeration also yields65 states and108 enabled edges.
        assertEquals(65,result.metrics().states());assertEquals(108,result.metrics().transitions());assertEquals(109,result.metrics().encodingCalls());
    }

    /** Checker limits/cancellation stay external incomplete results, with no fabricated modeled cutoff.
     * @param directory compiler workspace
     * @throws Exception for fixture failures */
    @Test void externalStopsAreNotModeledCutoffs(@TempDir Path directory) throws Exception {
        var program=load(directory);
        for(var budget:List.of(new BytecodeExplorer.Budget(0,1000000),new BytecodeExplorer.Budget(10000,0),new BytecodeExplorer.Budget(1,1000000))) {
            var result=new BytecodeExplorer(program,BytecodeScheduler.Mode.REFERENCE).explore(budget);
            assertFalse(result.traversalComplete());assertFalse(result.exhaustive());
            var witness=result.witnesses().getLast();assertEquals(BytecodeExplorer.Kind.INTERRUPTED,witness.outcome().kind());
            assertNull(witness.outcome().cutoff());assertNull(witness.outcome().observer());assertNull(witness.outcome().fault());
        }
        try {
            Thread.currentThread().interrupt();
            var result=new BytecodeExplorer(program,BytecodeScheduler.Mode.REFERENCE).explore(10000);
            assertEquals("CANCELLED",result.witnesses().getFirst().outcome().interruption());assertEquals(0,result.metrics().states());
            assertTrue(Thread.currentThread().isInterrupted());
        } finally {Thread.interrupted();}
        assertThrows(IllegalArgumentException.class,()->new BytecodeExplorer.Budget(-1,1));
        assertThrows(IllegalArgumentException.class,()->new BytecodeExplorer.Budget(1,-1));
    }

    /** Returned schedules contain enabled original choices and reproduce their actual internal states.
     * This is not the public strict replay format owned by94C.
     * @param directory compiler workspace
     * @throws Exception for fixture failures */
    @Test void internalWitnessesFollowActualEnabledChoices(@TempDir Path directory) throws Exception {
        var program=load(directory);var initial=program.initialState().canonicalBytes();
        for(var mode:BytecodeScheduler.Mode.values()) {
            var explorer=new BytecodeExplorer(program,mode);var result=explorer.explore(100000);
            assertTrue(result.exhaustive());
            for(var witness:result.witnesses()) {
                var scheduler=new BytecodeScheduler(program,mode);var state=program.initialState();
                for(var selection:witness.schedule()) {
                    assertTrue(scheduler.choices(state).contains(selection.actor()));
                    var next=scheduler.advance(state,selection.actor());assertEquals(selection.executed(),next.executed());state=next.state();
                }
                assertArrayEquals(witness.state().canonicalBytes(),state.canonicalBytes());
                assertFalse(state.executions.containsKey(BytecodeState.OBSERVER));
            }
            var again=explorer.explore(100000);assertEquals(result.metrics().states(),again.metrics().states());
            assertArrayEquals(initial,program.initialState().canonicalBytes());
        }
    }

    /** A full state budget still permits edges to already-stored states.
     * @param directory compiler workspace
     * @throws Exception for fixture failures */
    @Test void exactStateBudgetAllowsRemainingDuplicateEdges(@TempDir Path directory) throws Exception {
        var program=load(directory);
        var explorer=new BytecodeExplorer(program,BytecodeScheduler.Mode.REFERENCE);long start=System.nanoTime();var result=explorer.explore(140);long elapsed=System.nanoTime()-start;
        assertTrue(result.exhaustive());assertEquals(140,result.metrics().states());assertEquals(246,result.metrics().transitions());
        assertEquals(2,result.witnesses().size());assertEquals(178,result.metrics().instructions());assertEquals(178,result.metrics().opcodes());
        assertEquals(247,result.metrics().encodingCalls());assertEquals(121274,result.metrics().encodingBytes());assertEquals(68698,result.metrics().retainedKeyBytes());
        assertTrue(result.metrics().encodingNanos()>0);assertTrue(result.metrics().encodingNanos()<=elapsed);
        assertEquals(0,result.metrics().checkInvocations());assertEquals(0,result.metrics().checkInstructions());
        assertEquals(new BytecodeSharing.Work(1,4,0,0),result.metrics().sharing());
        assertTrue(explorer.explore(new BytecodeExplorer.Budget(140,68698)).exhaustive());
        var shortKeys=explorer.explore(new BytecodeExplorer.Budget(140,68697));assertFalse(shortKeys.exhaustive());
        assertEquals("KEY_BYTES",shortKeys.witnesses().getLast().outcome().interruption());
        var shortStates=explorer.explore(139);assertFalse(shortStates.exhaustive());assertEquals(139,shortStates.metrics().states());
        assertTrue(explorer.explore(140).exhaustive(),"A prior interruption must not leak into a reused explorer");
        assertThrows(NullPointerException.class,()->explorer.explore((BytecodeExplorer.Budget)null));
    }

    /** Independent concurrent runs share metadata without target state or search-store interference.
     * @param directory compiler workspace
     * @throws Exception for fixture or worker failures */
    @Test void immutableProgramSupportsIndependentConcurrentRuns(@TempDir Path directory) throws Exception {
        var program=load(directory);var initial=program.initialState().canonicalBytes();
        try(var workers=java.util.concurrent.Executors.newFixedThreadPool(2)) {
            var reference=workers.submit(()->new BytecodeExplorer(program,BytecodeScheduler.Mode.REFERENCE).explore(100000));
            var batched=workers.submit(()->new BytecodeExplorer(program,BytecodeScheduler.Mode.BATCHED).explore(100000));
            try {
                var first=reference.get(10,java.util.concurrent.TimeUnit.SECONDS);var second=batched.get(10,java.util.concurrent.TimeUnit.SECONDS);
                assertTrue(first.exhaustive());assertTrue(second.exhaustive());
                assertEquals(BytecodeBatchingOracleTest.independentProjection(first),BytecodeBatchingOracleTest.independentProjection(second));
                assertArrayEquals(initial,program.initialState().canonicalBytes());
            } finally {reference.cancel(true);batched.cancel(true);}
        }
    }

    /** Loads a tiny shared-state model without observer callbacks.
     * @param directory compiler workspace
     * @return validated model
     * @throws Exception for fixture failures */
    private static BytecodeProgram load(Path directory) throws Exception {
        var classes=BytecodeFixtures.compile(directory,26,"""
            @Model public class Fixture {public int x;public Fixture(){}
              @Actor(id=0)public void first(){x++;} @Actor(id=1)public void second(){x++;}
            }
            """);
        return BytecodeLoader.load(classes,"Fixture",BytecodeLimits.defaults());
    }
}
