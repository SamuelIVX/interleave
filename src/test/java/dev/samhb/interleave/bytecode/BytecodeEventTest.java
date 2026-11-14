/** Bounded single-step contracts for fuel and pending events, independent of loading/setup execution. */
package dev.samhb.interleave.bytecode;

import java.lang.classfile.*;
import java.lang.constant.*;
import java.util.*;
import java.util.function.Consumer;
import org.junit.jupiter.api.Test;
import static dev.samhb.interleave.bytecode.BytecodeState.*;
import static java.lang.classfile.ClassFile.*;
import static org.junit.jupiter.api.Assertions.*;

class BytecodeEventTest {
    /** A final return consumes its last instruction, then yields before normal terminal delivery. */
    @Test void exactReturnYieldsAndDeliversWithoutChargingEvents() {
        var program=program(0,1,CodeBuilder::return_); var state=start(program);
        var result=OpcodeExecutor.step(program,state,0); state=result.state();
        assertEquals(OpcodeExecutor.Kind.PENDING,result.kind()); assertEquals(Phase.RETURNING,state.executions.get(0).phase);
        assertEquals(1,state.executions.get(0).instructions); assertEquals(0,state.executions.get(0).eventLocation.bci());
        state=OpcodeExecutor.step(program,state,0).state();
        assertEquals(Phase.TERMINATION_PENDING,state.executions.get(0).phase); assertTrue(state.executions.get(0).frames.isEmpty());
        result=OpcodeExecutor.step(program,state,0); state=result.state();
        assertEquals(OpcodeExecutor.Kind.TERMINAL,result.kind()); assertEquals(Phase.TERMINATED,state.executions.get(0).phase);
        assertEquals(1,state.executions.get(0).instructions); assertEquals(0,state.executions.get(0).allocations);
        assertArrayEquals(state.canonicalBytes(),OpcodeExecutor.step(program,state,0).state().canonicalBytes());
    }

    /** A loop can consume exactly N instructions, but the omitted opcode is never charged. */
    @Test void exhaustionIsPendingAndEndsThePrefix() {
        var program=program(0,2,code-> { var loop=code.newLabel(); code.labelBinding(loop).goto_(loop); }); var state=start(program);
        state=OpcodeExecutor.step(program,state,0).state(); assertEquals(1,state.executions.get(0).instructions);
        state=OpcodeExecutor.step(program,state,0).state(); assertEquals(2,state.executions.get(0).instructions);
        var result=OpcodeExecutor.step(program,state,0); state=result.state();
        assertEquals(OpcodeExecutor.Kind.PENDING,result.kind()); var execution=state.executions.get(0);
        assertEquals(Phase.CUTOFF_PENDING,execution.phase); assertEquals(2,execution.instructions);
        assertEquals(new Cutoff("INSTRUCTION_FUEL",new BytecodeLocation("Fixture","actor","()V",0),2,1,0),execution.cutoff);
        result=OpcodeExecutor.step(program,state,0); state=result.state();
        assertEquals(OpcodeExecutor.Kind.TERMINAL,result.kind()); assertEquals(Phase.CUTOFF,state.executions.get(0).phase);
    }

    /** Faulting opcodes charge fuel; unwind and final failure delivery each make finite, fuel-free progress. */
    @Test void faultUnwindsOneFrameAndRetainsOriginalLocation() {
        var program=program(0,8,code->code.iconst_1().iconst_0().idiv().pop().return_()); var state=start(program);
        state=OpcodeExecutor.step(program,state,0).state(); state=OpcodeExecutor.step(program,state,0).state();
        var result=OpcodeExecutor.step(program,state,0); state=result.state();
        assertEquals(OpcodeExecutor.Kind.PENDING,result.kind()); assertEquals(Phase.EXCEPTION,state.executions.get(0).phase);
        assertEquals(3,state.executions.get(0).instructions); assertEquals("java/lang/ArithmeticException",state.executions.get(0).fault.type());
        assertEquals(new BytecodeLocation("Fixture","actor","()V",2),state.executions.get(0).fault.location());
        assertEquals(List.of(new BytecodeLocation("Fixture","actor","()V",2)),state.executions.get(0).fault.stack());
        state=OpcodeExecutor.step(program,state,0).state(); assertTrue(state.executions.get(0).frames.isEmpty());
        state=OpcodeExecutor.step(program,state,0).state(); assertEquals(Phase.FAILURE_PENDING,state.executions.get(0).phase);
        result=OpcodeExecutor.step(program,state,0); state=result.state();
        assertEquals(OpcodeExecutor.Kind.TERMINAL,result.kind()); assertEquals(Phase.FAILED,state.executions.get(0).phase);
        assertEquals(3,state.executions.get(0).instructions);
    }

    /** Implicit monitor entry/release are separate finite events and consume no opcode fuel. */
    @Test void synchronizedMethodEventsAcquireAndRelease() {
        var program=program(ACC_SYNCHRONIZED,1,CodeBuilder::return_); var state=start(program);
        var result=OpcodeExecutor.step(program,state,0); state=result.state();
        assertEquals(OpcodeExecutor.Kind.ADVANCED,result.kind()); assertTrue(state.frame(0).entered);
        assertEquals(FIXTURE,state.frame(0).methodMonitor); assertEquals(0,state.executions.get(0).instructions);
        assertEquals(0,state.heap.get(FIXTURE).monitorOwner); assertEquals(1,state.heap.get(FIXTURE).monitorDepth);
        state=OpcodeExecutor.step(program,state,0).state(); assertEquals(Phase.RETURNING,state.executions.get(0).phase);
        state=OpcodeExecutor.step(program,state,0).state(); assertEquals(Phase.TERMINATION_PENDING,state.executions.get(0).phase);
        assertEquals(-2,state.heap.get(FIXTURE).monitorOwner); assertEquals(0,state.heap.get(FIXTURE).monitorDepth);
        assertEquals(1,state.executions.get(0).instructions);
    }


    /** Available, competing-owner and null explicit acquisitions are distinguished without loader setup loops. */
    @Test void explicitMonitorProbeIsFiniteAndPure() {
        var program=program(0,16,code->code.aload(0).dup().astore(1).monitorenter().aload(1).monitorexit().return_());
        var ready=start(program);
        for(int instruction=0;instruction<3;instruction++)ready=OpcodeExecutor.step(program,ready,0).state();
        assertEquals(3,ready.executions.get(0).instructions);
        var result=OpcodeExecutor.step(program,ready,0);
        assertEquals(OpcodeExecutor.Kind.ADVANCED,result.kind());assertEquals(4,result.state().executions.get(0).instructions);
        assertEquals(0,result.state().heap.get(FIXTURE).monitorOwner);assertEquals(1,result.state().heap.get(FIXTURE).monitorDepth);
        var blocked=ready.copy();blocked.heap.get(FIXTURE).monitorOwner=1;blocked.heap.get(FIXTURE).monitorDepth=1;
        byte[] beforeBlocked=blocked.canonicalBytes();result=OpcodeExecutor.step(program,blocked,0);
        assertEquals(OpcodeExecutor.Kind.BLOCKED,result.kind());assertArrayEquals(beforeBlocked,blocked.canonicalBytes());
        assertArrayEquals(beforeBlocked,result.state().canonicalBytes());
        var absent=ready.copy();absent.frame(0).stack.clear();absent.frame(0).stack.add(NULL);
        result=OpcodeExecutor.step(program,absent,0);
        assertEquals(OpcodeExecutor.Kind.PENDING,result.kind());assertEquals(4,result.state().executions.get(0).instructions);
        assertEquals("java/lang/NullPointerException",result.state().executions.get(0).fault.type());
    }

    /** Builds trusted test metadata directly so a broken setup event cannot stall a regression before its assertion.
     *
     * @param flags extra actor flags
     * @param fuel actor instruction allowance
     * @param body valid category-one bytecode
     *
     * @return internal immutable program */
    private static BytecodeProgram program(int flags,int fuel,Consumer<CodeBuilder> body) {
        var parser=ClassFile.of(); byte[] bytes=parser.build(ClassDesc.of("Fixture"),builder->builder
            .withMethodBody("actor",MethodTypeDesc.of(ConstantDescs.CD_void),ACC_PUBLIC|flags,body));
        var method=new BytecodeMethod("Fixture",parser.parse(bytes).methods().getFirst());
        var limits=new BytecodeLimits(fuel,128,8192,32,64,256,4096,128,8192,1024);
        return new BytecodeProgram("Fixture",Map.of(method.id,method),Map.of(),List.of(method.id),List.of(),List.of("Fixture"),limits,"test",null);
    }

    /** Starts an isolated actor using the internal instruction seam, without invoking a target constructor.
     *
     * @param program trusted synthetic program
     * @return owned state */
    private static BytecodeState start(BytecodeProgram program) {
        var state=BytecodeState.beforeSetup(program); state.executions.get(SETUP).phase=Phase.TERMINATED;
        state.heap.get(FIXTURE).initialized=true;
        state.start(0,program.methods.get(program.actors.getFirst()),List.of(FIXTURE)); return state;
    }
}
