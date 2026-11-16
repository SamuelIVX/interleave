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
