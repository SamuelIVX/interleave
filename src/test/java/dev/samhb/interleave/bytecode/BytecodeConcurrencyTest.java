/** Concurrent choices and exact outcomes through the internal94B scheduling/search seams. */
package dev.samhb.interleave.bytecode;

import java.nio.file.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static dev.samhb.interleave.bytecode.BytecodeState.*;
import static org.junit.jupiter.api.Assertions.*;

class BytecodeConcurrencyTest {
    /** Shared x++ keeps a scheduling choice between its read and write.
     * @param directory compiler workspace
     * @throws Exception for compiler/resource failures */
    @Test void sharedReadAndWriteRemainSeparateChoices(@TempDir Path directory) throws Exception {
        String source;
        try(var resource=getClass().getResourceAsStream("/bytecode/concurrency/lost-update/Fixture.java")) {
            assertNotNull(resource);source=new String(resource.readAllBytes(),java.nio.charset.StandardCharsets.UTF_8);
        }
        var program=BytecodeLoader.load(BytecodeFixtures.compile(directory,26,source),"Fixture",BytecodeLimits.defaults());
        var scheduler=new BytecodeScheduler(program,BytecodeScheduler.Mode.REFERENCE);
        var initial=program.initialState();byte[] before=initial.canonicalBytes();
        assertEquals(List.of(0,1),scheduler.choices(initial));assertArrayEquals(before,initial.canonicalBytes());
        var state=initial;
        for(int actor:new int[]{0,1})for(int instruction=0;instruction<3;instruction++)state=scheduler.advance(state,actor).state();
        assertEquals(new Int(0),state.fixtureField("x"));
        assertEquals(3,state.executions.get(0).instructions);assertEquals(3,state.executions.get(1).instructions);
        for(int actor:new int[]{0,1})for(int instruction=0;instruction<3;instruction++)state=scheduler.advance(state,actor).state();
        assertEquals(new Int(1),state.fixtureField("x"));
        assertArrayEquals(before,initial.canonicalBytes());
    }
    /** Exact search retains both lost and nonlost final observations.
     * @param directory compiler workspace
     * @throws Exception for fixture failures */
    @Test void exactDfsFindsBothIncrementOutcomes(@TempDir Path directory) throws Exception {
        var classes=BytecodeFixtures.compile(directory,26,"""
            @Model public class Fixture {public int x;public Fixture(){}
              @Actor(id=0)public void first(){x++;} @Actor(id=1)public void second(){x++;}
            }
            """);
        var program=BytecodeLoader.load(classes,"Fixture",BytecodeLimits.defaults());
        var result=new BytecodeExplorer(program,BytecodeScheduler.Mode.REFERENCE).explore(100000);
        assertTrue(result.traversalComplete());assertTrue(result.exhaustive());
        var values=new TreeSet<Integer>();
        for(var witness:result.witnesses()) {
            assertEquals(BytecodeExplorer.Kind.COMPLETED,witness.outcome().kind());
            values.add(((Int)witness.state().fixtureField("x")).value());
            assertFalse(witness.schedule().isEmpty());
        }
        assertEquals(Set.of(1,2),values);
    }

    /** A real two-lock cycle is distinguished from schedules which release both monitors normally.
     * @param directory compiler workspace
     * @throws Exception for fixture failures */
    @Test void opposingLocksProduceDeadlockAndCompletion(@TempDir Path directory) throws Exception {
        String source;try(var resource=getClass().getResourceAsStream("/bytecode/concurrency/two-lock/Fixture.java")) {
            assertNotNull(resource);source=new String(resource.readAllBytes(),java.nio.charset.StandardCharsets.UTF_8);
        }
        var program=BytecodeLoader.load(BytecodeFixtures.compile(directory,26,source),"Fixture",BytecodeLimits.defaults());
        var result=new BytecodeExplorer(program,BytecodeScheduler.Mode.REFERENCE).explore(100000);
        assertTrue(result.traversalComplete());assertTrue(result.exhaustive());
        assertEquals(Set.of(BytecodeExplorer.Kind.DEADLOCK,BytecodeExplorer.Kind.COMPLETED),
            result.witnesses().stream().map(w->w.outcome().kind()).collect(java.util.stream.Collectors.toSet()));
        for(var witness:result.witnesses()) {
            if(witness.outcome().kind()==BytecodeExplorer.Kind.DEADLOCK) {
                assertEquals(List.of(new BytecodeExplorer.Wait(0,1,new Ref(SETUP,2),1),
                    new BytecodeExplorer.Wait(1,0,new Ref(SETUP,1),1)),witness.outcome().waits());
                assertEquals(new Int(0),witness.state().fixtureField("x"));
            } else {
                assertEquals(new Int(2),witness.state().fixtureField("x"));
                assertTrue(witness.state().heap.values().stream().allMatch(object->object.monitorDepth==0));
            }
        }
    }

    /** Class monitors on static synchronized helpers prevent lost updates.
     * @param directory compiler workspace
     * @throws Exception for fixture failures */
    @Test void staticSynchronizedHelpersProtectTheCounter(@TempDir Path directory) throws Exception {
        var classes=BytecodeFixtures.compile(directory,26,"""
            @Model public class Fixture {public int x;public Fixture(){}
              @Actor(id=0)public void first(){increment(this);} @Actor(id=1)public void second(){increment(this);}
              private static synchronized void increment(Fixture fixture){fixture.x++;}
            }
            """);
        var program=BytecodeLoader.load(classes,"Fixture",BytecodeLimits.defaults());
        var result=new BytecodeExplorer(program,BytecodeScheduler.Mode.REFERENCE).explore(100000);
        assertTrue(result.exhaustive());assertEquals(1,result.witnesses().size());
        assertEquals(BytecodeExplorer.Kind.COMPLETED,result.witnesses().getFirst().outcome().kind());
        assertEquals(new Int(2),result.witnesses().getFirst().state().fixtureField("x"));
    }

}
