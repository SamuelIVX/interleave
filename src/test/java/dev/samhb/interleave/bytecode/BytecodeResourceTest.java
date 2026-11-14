/** Resource ownership, omitted-operation identity and complete copied state. */
package dev.samhb.interleave.bytecode;

import java.nio.file.*;
import java.util.*;
import java.util.function.Consumer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;
import static dev.samhb.interleave.bytecode.BytecodeState.*;

class BytecodeResourceTest {
    /** Setup instructions, allocations and cells are separate and diagnosed at the omitted operation.
     *
     * @param directory compiler workspace
     * @throws Exception for fixture failures */
    @Test void setupAccountingAndExactIntrinsicFuel(@TempDir Path directory) throws Exception {
        Path classes=BytecodeFixtures.compile(directory,26,"@Model public class Fixture { public Object lock=new Object(); public Fixture() {} @Actor(id=0) public void actor() {} }");
        var allocationError=assertThrows(BytecodeLoadException.class,()->BytecodeLoader.load(classes,"Fixture",
            limits(256,4096,1,8192,32)));
        assertEquals("SETUP",allocationError.category); assertTrue(allocationError.getMessage().contains("ALLOCATIONS"));
        assertEquals(5,allocationError.location.bci());
        var cellError=assertThrows(BytecodeLoadException.class,()->BytecodeLoader.load(classes,"Fixture",limits(256,4096,128,3,32)));
        assertEquals("SETUP",cellError.category); assertTrue(cellError.getMessage().contains("HEAP_CELLS"));
        assertEquals(5,cellError.location.bci());
        assertThrows(BytecodeLoadException.class,()->BytecodeLoader.load(classes,"Fixture",limits(256,4096,128,2,32)));
        Path empty=BytecodeFixtures.compile(directory.resolve("empty"),26,"@Model public class Fixture { public Fixture() {} @Actor(id=0) public void actor() {} }");
        var shortFuel=assertThrows(BytecodeLoadException.class,()->BytecodeLoader.load(empty,"Fixture",limits(256,2,128,8192,32)));
        assertEquals(1,shortFuel.location.bci());
        var exact=BytecodeLoader.load(empty,"Fixture",limits(1,4,128,8192,32));
        assertEquals(4,exact.initialState().executions.get(SETUP).instructions);
        assertEquals(0,exact.initialState().executions.get(0).instructions);
        assertEquals(Phase.TERMINATED,BytecodeFixtures.run(exact,exact.initialState(),0).executions.get(0).phase);
    }


    /** Setup failure retains the original throwing helper position after compiler cleanup rethrows it.
     *
     * @param directory compiler workspace
     * @throws Exception for fixture failures */
    @Test void setupFailureNamesOriginalFaultAfterCleanup(@TempDir Path directory) throws Exception {
        var classes=BytecodeFixtures.compile(directory,26,"""
            @Model public class Fixture {Object lock=new Object();int value;
              public Fixture(){synchronized(lock){value=divide(1,0);}}
              static int divide(int a,int b){return a/b;} @Actor(id=0)public void actor(){}
            }
            """);
        var failure=assertThrows(BytecodeLoadException.class,()->BytecodeLoader.load(classes,"Fixture",BytecodeLimits.defaults()));
        assertEquals("SETUP",failure.category);assertEquals(new BytecodeLocation("Fixture","divide","(II)I",2),failure.location);
        assertTrue(failure.getMessage().contains("java/lang/ArithmeticException"));
    }

    /** Fault on the last permitted opcode succeeds as a fault, while cleanup needs fresh fuel.
     *
     * @param directory compiler workspace
     * @throws Exception for fixture failures */
    @Test void faultAndCleanupAtExactFuelBoundary(@TempDir Path directory) throws Exception {
        Path classes=BytecodeFixtures.compile(directory,26,"""
            @Model public class Fixture { public int value; public Fixture() {}
              @Actor(id=0) public void actor() { value=1/zero(); } static int zero() {return 0;}
            }
            """);
        var program=BytecodeLoader.load(classes,"Fixture",limits(6,4096,128,8192,32));
        var failure=BytecodeFixtures.run(program,program.initialState(),0).executions.get(0);
        assertEquals(Phase.FAILED,failure.phase); assertEquals(6,failure.instructions);
        program=BytecodeLoader.load(classes,"Fixture",limits(5,4096,128,8192,32));
        var before=BytecodeFixtures.run(program,program.initialState(),0).executions.get(0);
        assertEquals(Phase.CUTOFF,before.phase); assertNull(before.fault);
        Path cleanup=BytecodeFixtures.compile(directory.resolve("cleanup"),26,"""
            @Model public class Fixture { public int value; public Object lock=new Object(); public Fixture() {}
              @Actor(id=0) public void actor() { synchronized(lock) {value=1/zero();} } static int zero() {return 0;}
            }
            """);
        program=BytecodeLoader.load(cleanup,"Fixture",limits(11,4096,128,8192,32));
        var result=BytecodeFixtures.run(program,program.initialState(),0); var cut=result.executions.get(0);
        assertEquals(Phase.CUTOFF,cut.phase); assertEquals(11,cut.instructions);
        assertEquals("java/lang/ArithmeticException",cut.fault.type());
        var actor=program.methods.get(program.actors.getFirst());
        assertEquals(actor.target(actor.code.exceptionHandlers().getFirst().handler()),cut.cutoff.location().bci());
        assertEquals(1,result.heap.get((Ref)result.fixtureField("lock")).monitorDepth);
    }

    /** A disabled monitor probe has no effects; exhausted fuel has priority over blocking.
     *
     * @param directory compiler workspace
     * @throws Exception for fixture failures */
    @Test void blockedAttemptsConsumeNothing(@TempDir Path directory) throws Exception {
        Path classes=BytecodeFixtures.compile(directory,26,"""
            @Model public class Fixture { public Object lock=new Object(); public Fixture() {}
              @Actor(id=0) public void actor() {synchronized(lock){}} @Actor(id=1) public void other() {}
            }
            """);
        var program=BytecodeLoader.load(classes,"Fixture",BytecodeLimits.defaults()); var state=program.initialState();
        while(state.frame(0).method.ops.get(state.frame(0).bci).instruction().opcode()!=java.lang.classfile.Opcode.MONITORENTER)
            state=OpcodeExecutor.step(program,state,0).state();
        var lock=state.heap.get((Ref)state.fixtureField("lock")); lock.monitorOwner=1; lock.monitorDepth=1;
        var blocked=OpcodeExecutor.step(program,state,0);
        assertEquals(OpcodeExecutor.Kind.BLOCKED,blocked.kind()); assertArrayEquals(state.canonicalBytes(),blocked.state().canonicalBytes());
        state.executions.get(0).instructions=256;
        var cutoff=OpcodeExecutor.step(program,state,0);
        assertEquals(Phase.CUTOFF_PENDING,cutoff.state().executions.get(0).phase);
        assertEquals(1,cutoff.state().heap.get((Ref)state.fixtureField("lock")).monitorOwner);
    }

    /** Recursion and local infinite work terminate as prefix cutoffs with no frozen-actor continuation.
     *
     * @param directory compiler workspace
     * @throws Exception for fixture failures */
    @Test void callDepthAndLocalInfiniteComputationAreBounded(@TempDir Path directory) throws Exception {
        Path classes=BytecodeFixtures.compile(directory,26,"@Model public class Fixture {public Fixture() {} @Actor(id=0) public void actor(){helper();} static void helper(){helper();} }");
        var program=BytecodeLoader.load(classes,"Fixture",limits(256,4096,128,8192,2));
        var result=BytecodeFixtures.run(program,program.initialState(),0).executions.get(0);
        assertEquals(Phase.CUTOFF,result.phase); assertEquals("CALL_DEPTH",result.cutoff.reason());
        assertEquals(2,result.cutoff.limit()); assertEquals("helper",result.cutoff.location().name());
        Path loop=BytecodeFixtures.compile(directory.resolve("loop"),26,"@Model public class Fixture {public Fixture() {} @Actor(id=0) public void actor(){int x=0;while(true)x++;} @Actor(id=1) public void other() {} }");
        program=BytecodeLoader.load(loop,"Fixture",limits(17,4096,128,8192,32));
        result=BytecodeFixtures.run(program,program.initialState(),0).executions.get(0);
        assertEquals(Phase.CUTOFF,result.phase); assertEquals(17,result.instructions); assertEquals("INSTRUCTION_FUEL",result.cutoff.reason());
        var ended=BytecodeFixtures.run(program,program.initialState(),0); var artifact=program;
        assertThrows(IllegalStateException.class,()->BytecodeFixtures.run(artifact,ended,1),"Cutoff ends prefix rather than freezing actor0");
    }

    /** Dynamic null accesses and null monitor acquisition are VM faults, not load-time rejection.
     *
     * @param directory compiler workspace
     * @throws Exception for fixture failures */
    @Test void nullFaultsRemainRuntimeBehavior(@TempDir Path directory) throws Exception {
        for(String body:List.of("value=other.value;","synchronized(lock) {value=1;}")) {
            Path classes=BytecodeFixtures.compile(directory.resolve(Integer.toString(body.length())),26,
                "@Model public class Fixture { public Fixture other; public Object lock; public int value; public Fixture() {} @Actor(id=0) public void actor(){"+body+"} }");
            var program=BytecodeLoader.load(classes,"Fixture",BytecodeLimits.defaults()); var result=BytecodeFixtures.run(program,program.initialState(),0);
            assertEquals(Phase.FAILED,result.executions.get(0).phase);
            assertEquals("java/lang/NullPointerException",result.executions.get(0).fault.type());
            assertEquals(new Int(0),result.fixtureField("value"));
        }
    }

    /** Copies and typed canonical payloads retain every future-relevant family of state.
     *
     * @param directory compiler workspace
     * @throws Exception for fixture failures */
    @Test void canonicalStateAndCopiesRetainResourcesHistoryAndFrames(@TempDir Path directory) throws Exception {
        Path classes=BytecodeFixtures.compile(directory,26,"@Model public class Fixture {public int value=7; public Fixture() {} @Actor(id=0) public void actor(){for(int i=0;i<8;i++)value++;} }");
        var program=BytecodeLoader.load(classes,"Fixture",BytecodeLimits.defaults()); var initial=program.initialState();
        for(int step=0;step<17;step++) initial=OpcodeExecutor.step(program,initial,0).state();
        assertEquals(17,initial.executions.get(0).instructions);
        List<Consumer<BytecodeState>> changes=List.of(
            s->s.executions.get(0).instructions++,s->s.executions.get(0).allocations++,s->s.executions.get(0).cells++,
            s->s.executions.get(0).nextSequence++,s->s.heap.get(FIXTURE).shared=false,s->s.heap.get(FIXTURE).initialized=false,
            s->s.heap.get(FIXTURE).monitorOwner=0,s->s.heap.get(FIXTURE).monitorDepth=1,
            s->s.frame(0).locals.put(3,new Int(1)),s->s.frame(0).stack.add(new Int(1)),s->s.frame(0).bci=1,
            s->s.frame(0).entered=false,s->s.frame(0).methodMonitor=FIXTURE,s->s.executions.get(0).phase=Phase.TERMINATION_PENDING,
            s->s.executions.get(0).returned=new Int(2),s->s.executions.get(0).eventLocation=new BytecodeLocation("Fixture","actor","()V",0),
            s->s.executions.get(0).cutoff=new Cutoff("INSTRUCTION_FUEL",new BytecodeLocation("Fixture","actor","()V",0),256,1,0),
            s->s.executions.get(0).fault=new Fault("java/lang/NullPointerException",new BytecodeLocation("Fixture","actor","()V",0),List.of())
        );
        byte[] key=initial.canonicalBytes();
        for(var change:changes) { var changed=initial.copy(); assertArrayEquals(key,changed.canonicalBytes());
            assertEquals(17,changed.executions.get(0).instructions); change.accept(changed); assertFalse(Arrays.equals(key,changed.canonicalBytes())); assertArrayEquals(key,initial.canonicalBytes()); }
        var distinct=initial.copy(); distinct.frame(0).stack.add(new Ref(0,1L<<40));
        var other=initial.copy(); other.frame(0).stack.add(new Ref(1,1L<<40));
        assertFalse(Arrays.equals(distinct.canonicalBytes(),other.canonicalBytes()));
        // UTF-16 names must not collapse when UTF-8 would replace unmatched surrogates.
        var first=initial.copy(); first.statics.put(new BytecodeProgram.FieldId("Fixture",String.valueOf((char)0xd800),"I"),new Int(1));
        var second=initial.copy(); second.statics.put(new BytecodeProgram.FieldId("Fixture",String.valueOf((char)0xd801),"I"),new Int(1));
        assertFalse(Arrays.equals(first.canonicalBytes(),second.canonicalBytes()));
    }

    /** Builds a profile with independently chosen actor/setup allowances.
     *
     * @param actor actor work
     * @param setup setup work
     * @param allocations setup objects
     * @param cells setup cells
     * @param depth frames
     *
     * @return immutable limits */
    private static BytecodeLimits limits(int actor,int setup,int allocations,int cells,int depth) {
        return new BytecodeLimits(actor,128,8192,depth,64,256,setup,allocations,cells,1024);
    }
}
