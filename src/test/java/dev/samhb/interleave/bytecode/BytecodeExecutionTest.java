/** Sequential execution, exact resources and terminal-event regressions at the owned executor seam. */
package dev.samhb.interleave.bytecode;

import java.nio.file.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;
import static dev.samhb.interleave.bytecode.BytecodeState.*;

class BytecodeExecutionTest {
    /** Branches, loops and value-returning helpers agree with a separate JVM, across input versions.
     *
     * @param directory compiler workspace
     * @throws Exception for fixture/oracle failures */
    @Test void integerAndReferenceExecutionMatchesJvm(@TempDir Path directory) throws Exception {
        for(int release : new int[]{17,21,26}) {
            Path work=directory.resolve("release"+release);
            Path classes=BytecodeFixtures.compile(work,release,"""
                @Model public class Fixture {
                  public int value; public boolean same; public Object left=new Object(); public Object right=left;
                  public Fixture() {} @Actor(id=0) public void actor() {
                    int x=3; for(int i=0;i<5;i++) x=helper(x,i);
                    value=x; same=left==right;
                  }
                  static int helper(int x,int y) { return (x*7-y) ^ (y<<2); }
                }
                """);
            var program=BytecodeLoader.load(classes,"Fixture",BytecodeLimits.defaults());
            var original=program.initialState(); var result=BytecodeFixtures.run(program,original,0);
            String oracle=BytecodeFixtures.oracle(work,classes,"Fixture f=new Fixture(); f.actor(); System.out.println(f.value+\":\"+f.same);");
            assertEquals(oracle,((Int)result.fixtureField("value")).value()+":"+(((Int)result.fixtureField("same")).value()!=0));
            assertEquals(Phase.TERMINATED,result.executions.get(0).phase);
            assertEquals(new Int(0),original.fixtureField("value"));
            assertArrayEquals(original.canonicalBytes(),program.initialState().canonicalBytes());
        }
    }


    /** Static/reference helpers, null/identity branches and booleans retain JVM semantics.
     *
     * @param directory compiler workspace
     * @throws Exception for fixture/oracle failures */
    @Test void referenceBranchesStaticsAndCheckMetadata(@TempDir Path directory) throws Exception {
        var classes=BytecodeFixtures.compile(directory,26,"""
            @Model public class Fixture { public Object left=new Object();public Object right=new Object(); public Object absent;
              public int value;public boolean flag;static Fixture published;static final boolean YES=true;static int count;
              public Fixture(){published=this;}
              @Actor(id=0) public void actor(){
                Object x=alias(left);int mask=0;if(x!=null)mask+=1;if(absent==null)mask+=2;
                if(x!=right)mask+=4;if(x==left)mask+=8;if(absent!=null)mask+=16;if(x==right)mask+=32;
                count=mask;published.value=count;flag=YES&&truth();sync(this);
              }
              static Object alias(Object x){return x;} static boolean truth(){return true;}
              static synchronized void sync(Fixture x){x.value++;}
              @Check(when=Check.When.ALWAYS) public boolean zAlways(){return value>=0;}
              @Check public boolean aFinal(){return value>=0;}
              @Check(when=Check.When.FINAL) public boolean bFinal(){return value>=0;}
            }
            """);
        var program=BytecodeLoader.load(classes,"Fixture",BytecodeLimits.defaults());
        assertEquals(List.of("aFinal","bFinal","zAlways"),program.checks.stream().map(c->c.method().name()).toList());
        assertEquals(List.of(false,false,true),program.checks.stream().map(BytecodeProgram.Check::always).toList());
        var result=BytecodeFixtures.run(program,program.initialState(),0);
        assertEquals(new Int(16),result.fixtureField("value"));assertEquals(new Int(1),result.fixtureField("flag"));
        assertEquals("16:true",BytecodeFixtures.oracle(directory,classes,"Fixture f=new Fixture();f.actor();System.out.println(f.value+\":\"+f.flag);"));
        assertEquals(0,result.heap.get(new Ref(-2,0)).monitorDepth);
    }

    /** Exactly N opcodes permit return, while N-1 omits the actual next instruction.
     *
     * @param directory compiler workspace
     * @throws Exception for fixture failures */
    @Test void exactFuelReturnAndTerminalYield(@TempDir Path directory) throws Exception {
        Path classes=BytecodeFixtures.compile(directory,26,"@Model public class Fixture { public Fixture() {} @Actor(id=0) public void actor() {} }");
        var defaults=BytecodeLimits.defaults();
        var limits=new BytecodeLimits(1,128,8192,32,64,256,4096,128,8192,1024);
        var program=BytecodeLoader.load(classes,"Fixture",limits);
        var state=program.initialState(); var after=OpcodeExecutor.step(program,state,0);
        assertEquals(OpcodeExecutor.Kind.PENDING,after.kind());
        assertEquals(Phase.RETURNING,after.state().executions.get(0).phase);
        assertEquals(1,after.state().executions.get(0).instructions);
        assertEquals(Phase.TERMINATED,BytecodeFixtures.run(program,after.state(),0).executions.get(0).phase);
        Path other=directory.resolve("other");
        classes=BytecodeFixtures.compile(other,26,"@Model public class Fixture { public int value; public Fixture() {} @Actor(id=0) public void actor() { value=1; } }");
        var bounded=BytecodeLoader.load(classes,"Fixture",limits);
        var cutoff=BytecodeFixtures.run(bounded,bounded.initialState(),0).executions.get(0);
        assertEquals(Phase.CUTOFF,cutoff.phase); assertEquals(1,cutoff.instructions);
        assertEquals("INSTRUCTION_FUEL",cutoff.cutoff.reason()); assertEquals(1,cutoff.cutoff.location().bci());
        assertEquals(1,cutoff.cutoff.limit()); assertEquals(1,cutoff.cutoff.required()); assertEquals(0,cutoff.cutoff.remaining());
        assertEquals(256,defaults.actorInstructions());
    }

    /** Faulting instructions consume fuel; earlier writes and original interpreted call stack remain.
     *
     * @param directory compiler workspace
     * @throws Exception for fixture failures */
    @Test void dynamicFaultPreservesCommittedWritesAndStack(@TempDir Path directory) throws Exception {
        Path classes=BytecodeFixtures.compile(directory,26,"""
            @Model public class Fixture {
              public int value; public Fixture() {} @Actor(id=0) public void actor() { value=7; value=divide(1,0); }
              static int divide(int a,int b) { return a/b; }
            }
            """);
        var program=BytecodeLoader.load(classes,"Fixture",BytecodeLimits.defaults());
        var state=BytecodeFixtures.run(program,program.initialState(),0); var execution=state.executions.get(0);
        assertEquals(Phase.FAILED,execution.phase); assertEquals(new Int(7),state.fixtureField("value"));
        assertEquals("java/lang/ArithmeticException",execution.fault.type());
        assertEquals("divide",execution.fault.location().name()); assertEquals(2,execution.fault.location().bci());
        assertEquals(2,execution.fault.stack().size()); assertEquals("actor",execution.fault.stack().get(1).name());
        // actor has seven opcodes before callee entry; iload/iload/idiv add three.
        assertEquals(10,execution.instructions);
    }

    /** Compiler cleanup and implicit synchronized exit preserve reentrant depth after a fault.
     *
     * @param directory compiler workspace
     * @throws Exception for fixture failures */
    @Test void cleanupReleasesNestedAndMethodMonitors(@TempDir Path directory) throws Exception {
        Path classes=BytecodeFixtures.compile(directory,21,"""
            @Model public class Fixture {
              public int value; public Object lock=new Object(); public Fixture() {}
              @Actor(id=0) public synchronized void actor() {
                synchronized(lock) { synchronized(lock) { value=7; value=1/zero(); } }
              }
              static int zero() { return 0; }
            }
            """);
        var program=BytecodeLoader.load(classes,"Fixture",BytecodeLimits.defaults());
        var state=BytecodeFixtures.run(program,program.initialState(),0);
        assertEquals(Phase.FAILED,state.executions.get(0).phase);
        assertEquals(new Int(7),state.fixtureField("value"));
        for(var object : state.heap.values()) { assertEquals(0,object.monitorDepth); assertEquals(-2,object.monitorOwner); }
    }

    /** Both JVM switch encodings select real branch targets including the default.
     *
     * @param directory compiler workspace
     * @throws Exception for compiler/oracle failures */
    @Test void switchAndPrimitiveEdgesMatchJvm(@TempDir Path directory) throws Exception {
        Path classes=BytecodeFixtures.compile(directory,17,"""
            @Model public class Fixture {
              public int value; public int input=-2147483648; public int minus=-1; public int shift=33; public int narrow=65535;
              public Fixture() {} @Actor(id=0) public void actor() {
                int x=input; value=(x/minus)+(x%minus)+(minus>>>shift)+(byte)narrow+(char)minus+(short)narrow;
                value+=dense(value&3)+sparse(value&3);
              }
              static int dense(int x) { switch(x) { case 0:return 10;case 1:return 20;case 2:return 30;default:return 40; } }
              static int sparse(int x) { switch(x) { case -100:return 10;case 700:return 20;case 99999:return 30;default:return 40; } }
            }
            """);
        var program=BytecodeLoader.load(classes,"Fixture",BytecodeLimits.defaults());
        var opcodes=program.methods.get(program.actors.getFirst()).ops.values().stream().map(o->o.instruction().opcode()).collect(java.util.stream.Collectors.toSet());
        assertTrue(opcodes.containsAll(Set.of(java.lang.classfile.Opcode.IUSHR,java.lang.classfile.Opcode.I2B,
            java.lang.classfile.Opcode.I2C,java.lang.classfile.Opcode.I2S)));
        var state=BytecodeFixtures.run(program,program.initialState(),0);
        assertEquals(BytecodeFixtures.oracle(directory,classes,"Fixture f=new Fixture(); f.actor(); System.out.println(f.value);"),
            Integer.toString(((Int)state.fixtureField("value")).value()));
    }
}
