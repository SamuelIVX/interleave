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
    /** A local-prefix arithmetic fault yields while a competing actor is still enabled.
     * @param directory compiler workspace
     * @throws Exception for fixture failures */
    @Test void batchYieldsBeforeDeliveringLocalFailure(@TempDir Path directory) throws Exception {
        var classes=BytecodeFixtures.compile(directory,26,"""
            @Model public class Fixture {public int x;public Fixture(){}
              @Actor(id=0)public void first(){int a=1;int b=a-1;int c=1/b;}
              @Actor(id=1)public void second(){x=1;}
            }
            """);
        var program=BytecodeLoader.load(classes,"Fixture",BytecodeLimits.defaults());
        var scheduler=new BytecodeScheduler(program,BytecodeScheduler.Mode.BATCHED);var parent=program.initialState();var before=parent.canonicalBytes();
        var fault=scheduler.advance(parent,0);assertEquals(Phase.EXCEPTION,fault.state().executions.get(0).phase);
        assertEquals(OpcodeExecutor.Kind.PENDING,fault.kind());assertTrue(fault.executed().size()>1);
        assertEquals(List.of(0,1),scheduler.choices(fault.state()));
        var other=scheduler.advance(fault.state(),1);assertEquals(new Int(1),other.state().fixtureField("x"));
        assertEquals(Phase.EXCEPTION,other.state().executions.get(0).phase);assertArrayEquals(before,parent.canonicalBytes());
    }

    /** Publication/rewiring refreshes actual frame roots and never erases historical sharing.
     * @param directory compiler workspace
     * @throws Exception for fixture failures */
    @Test void frameRootsAndRewiringKeepSharingHistory(@TempDir Path directory) throws Exception {
        var classes=BytecodeFixtures.compile(directory,26,"""
            @Model public class Fixture {public Object lock=new Object();public Fixture(){}
              @Actor(id=0)public void first(){Object remembered=lock;lock=null;synchronized(remembered){}}
            }
            """);
        var program=BytecodeLoader.load(classes,"Fixture",BytecodeLimits.defaults());var state=program.initialState();var lock=(Ref)state.fixtureField("lock");
        var initial=BytecodeSharing.refresh(state);assertEquals(new BytecodeSharing.Work(1,5,2,0),initial);
        var scheduler=new BytecodeScheduler(program,BytecodeScheduler.Mode.BATCHED);
        state=scheduler.advance(state,0).state();var publication=scheduler.advance(state,0);state=publication.state();
        assertEquals(NULL,state.fixtureField("lock"));assertEquals(new BytecodeSharing.Work(1,5,0,0),publication.sharing());
        assertTrue(state.frame(0).locals.containsValue(lock));assertTrue(state.heap.get(lock).shared);
        var historic=state.canonicalBytes();state.heap.get(lock).shared=false;assertFalse(Arrays.equals(historic,state.canonicalBytes()));
        // This synthetic flag erasure now establishes sole actor reachability; real promotions are irreversible.
        assertEquals(0,BytecodeSharing.refresh(state).promotions());
        state.heap.get(lock).shared=true;assertArrayEquals(historic,state.canonicalBytes());
        // Unknown/unreachable setup objects are also shared; the profile never infers exclusivity.
        state.frame(0).locals.values().removeIf(lock::equals);state.frame(0).stack.clear();state.heap.get(lock).shared=false;
        assertEquals(1,BytecodeSharing.refresh(state).promotions());assertTrue(state.heap.get(lock).shared);
        assertEquals(0,BytecodeSharing.refresh(state).promotions());
    }
    /** A failed reference write is a fault, not a committed publication traversal.
     * @param directory compiler workspace
     * @throws Exception for fixture failures */
    @Test void failedReferenceWriteDoesNotRefreshPublication(@TempDir Path directory) throws Exception {
        var classes=BytecodeFixtures.compile(directory,26,"""
            @Model public class Fixture {public Object lock=new Object();public Fixture(){}
              @Actor(id=0)public void first(){Fixture absent=null;absent.lock=lock;}
            }
            """);
        var program=BytecodeLoader.load(classes,"Fixture",BytecodeLimits.defaults());
        var scheduler=new BytecodeScheduler(program,BytecodeScheduler.Mode.BATCHED);var read=scheduler.advance(program.initialState(),0);
        var write=scheduler.advance(read.state(),0);assertEquals(Phase.EXCEPTION,write.state().executions.get(0).phase);
        assertEquals("java/lang/NullPointerException",write.state().executions.get(0).fault.type());assertEquals(BytecodeSharing.Work.NONE,write.sharing());
    }
    /** A cancelled selection aborts externally without committing any target state or clearing the flag.
     * Controlled child instrumentation separately tests cancellation after a primitive commit.
     * @param directory compiler workspace
     * @throws Exception for fixture failures */
    @Test void cancelledSelectionHonorsHostFlag(@TempDir Path directory) throws Exception {
        var classes=BytecodeFixtures.compile(directory,26,"""
            @Model public class Fixture {public Fixture(){} @Actor(id=0)public void actor(){while(true){}}}
            """);
        var program=BytecodeLoader.load(classes,"Fixture",BytecodeLimits.defaults());var initial=program.initialState();var before=initial.canonicalBytes();
        for(var mode:BytecodeScheduler.Mode.values()) {
            try {
                Thread.currentThread().interrupt();var scheduler=new BytecodeScheduler(program,mode);
                assertThrows(java.util.concurrent.CancellationException.class,()->scheduler.advance(initial,0));
                assertTrue(Thread.currentThread().isInterrupted());assertArrayEquals(before,initial.canonicalBytes());
            } finally {Thread.interrupted();}
        }
    }
    /** A disabled selected monitor returns an owned unchanged result and no charged or recorded work.
     * @param directory compiler workspace
     * @throws Exception for fixture failures */
    @Test void disabledSelectionAndInvalidActorsHaveExplicitContracts(@TempDir Path directory) throws Exception {
        var classes=BytecodeFixtures.compile(directory,26,"""
            @Model public class Fixture {public Fixture(){}
              @Actor(id=0)public synchronized void first(){} @Actor(id=1)public synchronized void second(){}}
            """);
        var program=BytecodeLoader.load(classes,"Fixture",BytecodeLimits.defaults());
        for(var mode:BytecodeScheduler.Mode.values()) {
            var scheduler=new BytecodeScheduler(program,mode);var held=scheduler.advance(program.initialState(),0).state();
            var before=held.canonicalBytes();assertEquals(List.of(0),scheduler.choices(held));var blocked=scheduler.advance(held,1);
            assertEquals(OpcodeExecutor.Kind.BLOCKED,blocked.kind());assertArrayEquals(before,blocked.state().canonicalBytes());
            assertNotSame(held,blocked.state());assertTrue(blocked.executed().isEmpty());assertEquals(0,blocked.instructions());assertEquals(BytecodeSharing.Work.NONE,blocked.sharing());
            assertThrows(IllegalArgumentException.class,()->scheduler.advance(held,-1));assertThrows(IllegalArgumentException.class,()->scheduler.advance(held,2));
        }
    }

    /** Actual javac increment BCIs/opcodes survive batching and keep the write in a later transition.
     * @param directory compiler workspace
     * @throws Exception for fixture failures */
    @Test void batchedTraceRetainsRealReadWritePositions(@TempDir Path directory) throws Exception {
        var classes=BytecodeFixtures.compile(directory,26,"""
            @Model public class Fixture {public int x;public Fixture(){} @Actor(id=0)public void actor(){x++;}}
            """);
        var program=BytecodeLoader.load(classes,"Fixture",BytecodeLimits.defaults());var scheduler=new BytecodeScheduler(program,BytecodeScheduler.Mode.BATCHED);
        var read=scheduler.advance(program.initialState(),0);
        assertEquals(List.of(0,1,2,5,6),read.executed().stream().map(step->step.location().bci()).toList());
        assertEquals(List.of(java.lang.classfile.Opcode.ALOAD_0,java.lang.classfile.Opcode.DUP,java.lang.classfile.Opcode.GETFIELD,
            java.lang.classfile.Opcode.ICONST_1,java.lang.classfile.Opcode.IADD),read.executed().stream().map(BytecodeScheduler.Executed::opcode).toList());
        assertEquals(5,read.instructions());assertEquals(new Int(0),read.state().fixtureField("x"));
        var write=scheduler.advance(read.state(),0);assertEquals(1,write.instructions());assertEquals(new Int(1),write.state().fixtureField("x"));
        assertEquals(List.of(new BytecodeScheduler.Executed(new BytecodeLocation("Fixture","actor","()V",7),java.lang.classfile.Opcode.PUTFIELD)),write.executed());
        var returned=scheduler.advance(write.state(),0);assertEquals(1,returned.instructions());assertEquals(Phase.RETURNING,returned.state().executions.get(0).phase);
        assertEquals(List.of(new BytecodeScheduler.Executed(new BytecodeLocation("Fixture","actor","()V",10),java.lang.classfile.Opcode.RETURN)),returned.executed());
    }
    /** Reference static publication refreshes roots and charges historical promotion on the owned successor.
     * @param directory compiler workspace
     * @throws Exception for fixture failures */
    @Test void staticReferencePublicationRefreshesSharedness(@TempDir Path directory) throws Exception {
        var classes=BytecodeFixtures.compile(directory,26,"""
            @Model public class Fixture {public static Object published;public Object lock=new Object();public Fixture(){}
              @Actor(id=0)public void actor(){published=lock;}}
            """);
        var program=BytecodeLoader.load(classes,"Fixture",BytecodeLimits.defaults());var scheduler=new BytecodeScheduler(program,BytecodeScheduler.Mode.BATCHED);
        var read=scheduler.advance(program.initialState(),0).state();var field=new BytecodeProgram.FieldId("Fixture","published","Ljava/lang/Object;");
        assertEquals(NULL,read.statics.get(field));var published=scheduler.advance(read,0);
        assertEquals(new Ref(SETUP,1),published.state().statics.get(field));assertEquals(new BytecodeSharing.Work(1,5,2,0),published.sharing());
        // A trusted history mutation checks promotion accounting without claiming95's reachable local heap.
        var history=read.copy();history.heap.get(new Ref(SETUP,1)).shared=false;var before=history.canonicalBytes();
        var promoted=scheduler.advance(history,0);assertEquals(new BytecodeSharing.Work(1,5,2,1),promoted.sharing());
        assertTrue(promoted.state().heap.get(new Ref(SETUP,1)).shared);assertFalse(history.heap.get(new Ref(SETUP,1)).shared);assertArrayEquals(before,history.canonicalBytes());
    }

}
