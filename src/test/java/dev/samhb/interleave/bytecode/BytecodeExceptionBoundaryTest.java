/** JVM-checked exception dispatch, cleanup metadata, conditional locking and version boundaries. */
package dev.samhb.interleave.bytecode;

import dev.samhb.interleave.InterleaveRunner;
import dev.samhb.interleave.core.BytecodeEvidence;
import dev.samhb.interleave.format.BytecodeProgramLoader;
import java.nio.file.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

class BytecodeExceptionBoundaryTest {
    /** Unrelated overloads remain interpreted while the prohibited Throwable-parameter family rejects.
     * @param directory compiler workspace
     * @throws Exception for compiler/JVM failures */
    @Test void unrelatedInitCauseOverloadIsAllowed(@TempDir Path directory) throws Exception {
        var classes=BytecodeFixtures.compile(directory,17,"""
            class Failure extends Throwable {int initCause(int x){return x;}}
            @Model public class Fixture {public int result;public Fixture(){}
              @Actor(id=0)public void actor(){result=new Failure().initCause(7);}}
            """);
        assertEquals("7",BytecodeFixtures.oracle(directory,classes,"Fixture f=new Fixture();f.actor();System.out.print(f.result);"));
        var program=BytecodeProgramLoader.load(classes,"Fixture");
        assertEquals(new BytecodeState.Int(7),BytecodeFixtures.run(program,program.initialState(),0).fixtureField("result"));
    }

    /** ACC_SUPER selects the immediate superclass override even when the symbolic owner is older.
     * @param directory compiler workspace
     * @throws Exception for compiler, transformation or JVM failures */
    @Test void specialGetterUsesSelectedApplicationOverride(@TempDir Path directory) throws Exception {
        var classes=BytecodeFixtures.compile(directory,17,"""
            class Middle extends RuntimeException {Middle(){super("base");}public String getMessage(){return "middle";}}
            class Leaf extends Middle {String read(){return super.getMessage();}}
            @Model public class Fixture {public boolean result;public Fixture(){}
              @Actor(id=0)public void actor(){result=new Leaf().read()=="middle";}}
            """);
        var parser=java.lang.classfile.ClassFile.of();var file=classes.resolve("Leaf.class");
        Files.write(file,parser.transformClass(parser.parse(Files.readAllBytes(file)),java.lang.classfile.ClassTransform.transformingMethodBodies(
            method->method.methodName().equalsString("read"),(code,item)->{
                if(item instanceof java.lang.classfile.instruction.InvokeInstruction call)
                    code.invokespecial(java.lang.constant.ClassDesc.of("java.lang.RuntimeException"),"getMessage",call.typeSymbol());
                else code.with(item);
            })));
        assertEquals("true",BytecodeFixtures.oracle(directory,classes,"Fixture f=new Fixture();f.actor();System.out.print(f.result);"));
        var program=BytecodeProgramLoader.load(classes,"Fixture");BytecodeBatchingOracleTest.compare(program);
        assertEquals(new BytecodeState.Int(1),BytecodeFixtures.run(program,program.initialState(),0).fixtureField("result"));
    }

    /** Exact validation payloads survive compiler cleanup; implicit subtype metadata is always available.
     * @param directory compiler workspace
     * @throws Exception for compiler/JVM failures */
    @Test void cleanupRetainsValidationPayloadAndImplicitSubtype(@TempDir Path directory) throws Exception {
        String[] actors={
            "try{synchronized(lock){error.addSuppressed(error);}}catch(IllegalArgumentException e){result=e.getMessage()!=null && e.getCause()==error;}",
            "try{synchronized(lock){absent.result=true;}}catch(RuntimeException e){result=true;}",
            "try{try{int x=1/zero;}finally{error.getSuppressed();cleanup++;}}catch(ArithmeticException e){result=cleanup==1;}"};
        for(int sample=0;sample<actors.length;sample++) {
            var work=directory.resolve("case"+sample);var classes=BytecodeFixtures.compile(work,17,
                "@Model public class Fixture {public Throwable error=new Throwable();public Object lock=new Object();public Fixture absent;public boolean result;public int zero,cleanup;public Fixture(){} @Actor(id=0)public void actor(){"+actors[sample]+"}}");
            assertEquals("true",BytecodeFixtures.oracle(work,classes,"Fixture f=new Fixture();f.actor();System.out.print(f.result);"));
            var program=BytecodeProgramLoader.load(classes,"Fixture");BytecodeBatchingOracleTest.compare(program);
            var result=InterleaveRunner.builder().build().runBytecode(program);assertFalse(result.hasViolation());assertFalse(result.hasIncomplete());
            assertEquals("int:1",result.completedTraces().getFirst().states().getLast().fields().get("setup:0.Fixture.result:Z"));
            assertTrue(result.completedTraces().getFirst().bytecodeEvidence().snapshots().getLast().heap().stream().allMatch(o->o.owner()==-2 && o.depth()==0));
        }
    }

    /** Schema1 cannot relabel new intrinsic protocol events, even when its identity is legacy-shaped.
     * @param directory compiler workspace
     * @throws Exception for fixture failures */
    @Test void legacySchemaRejectsIntrinsicEvents(@TempDir Path directory) throws Exception {
        var classes=BytecodeFixtures.compile(directory,17,"@Model public class Fixture {public Fixture(){} @Actor(id=0)public void actor(){new Throwable();}}");
        var program=BytecodeProgramLoader.load(classes,"Fixture");var data=InterleaveRunner.builder().build().runBytecode(program).completedTraces().getFirst().bytecodeEvidence();
        var id=data.identity();var legacy=new BytecodeEvidence.Identity(id.artifactDigest(),"BYTECODE-SC-2",id.modelClass(),id.actors(),id.checks(),id.limits(),id.mode(),id.requestedStrategy(),id.effectiveStrategy(),id.fallbackReason());
        assertThrows(IllegalArgumentException.class,()->new BytecodeEvidence(1,legacy,data.transitions(),data.snapshots(),data.terminal(),null));
        assertDoesNotThrow(()->new BytecodeEvidence(2,id,data.transitions(),data.snapshots(),data.terminal(),null));
    }

    /** An explicit NPE null message takes its JDK26 receiver monitor, unlike a nonnull detail message.
     * The separate JVM harness holds that monitor and observes the worker block before releasing it.
     * @param directory compiler workspace
     * @throws Exception for compiler/JVM failures */
    @Test void nullNpeMessageParticipatesInMonitorDeadlock(@TempDir Path directory) throws Exception {
        var classes=BytecodeFixtures.compile(directory,17,"""
            @Model public class Fixture {public NullPointerException error=new NullPointerException();public Object lock=new Object();public boolean result;public Fixture(){}
              @Actor(id=0)public void first(){synchronized(error){synchronized(lock){}}}
              @Actor(id=1)public void second(){synchronized(lock){result=error.getMessage()==null;}}}
            """);
        assertEquals("true:true:true",BytecodeFixtures.oracle(directory,classes,"""
            Fixture f=new Fixture();Thread worker=new Thread(f::second);boolean blocked;
            synchronized(f.error){worker.start();long end=System.nanoTime()+2000000000L;
              while(worker.isAlive() && worker.getState()!=Thread.State.BLOCKED && System.nanoTime()<end)java.util.concurrent.locks.LockSupport.parkNanos(1000000L);
              blocked=worker.getState()==Thread.State.BLOCKED;}
            try{worker.join(2000);}catch(InterruptedException failure){throw new RuntimeException(failure);}
            System.out.print(blocked+":"+f.result+":"+!worker.isAlive());
            """));
        var program=BytecodeProgramLoader.load(classes,"Fixture");BytecodeBatchingOracleTest.compare(program);
        var result=InterleaveRunner.builder().build().runBytecode(program);assertFalse(result.hasIncomplete());assertFalse(result.deadlockedTraces().isEmpty());
    }

    /** A potentially synchronizing NPE getter cannot execute inside an observer.
     * @param directory compiler workspace
     * @throws Exception for fixture failures */
    @Test void npeMessageGetterRejectsObserverSynchronization(@TempDir Path directory) throws Exception {
        var classes=BytecodeFixtures.compile(directory,17,"""
            @Model public class Fixture {public NullPointerException error=new NullPointerException();public Fixture(){}
              @Actor(id=0)public void actor(){} @Check public boolean valid(){return error.getMessage()==null;}}
            """);
        assertEquals("PURITY",assertThrows(BytecodeLoadException.class,()->BytecodeProgramLoader.load(classes,"Fixture")).category);
    }

    /** Trusted concrete override dispatch preserves an explicit nonnull NPE message without an app subclass.
     * @param directory compiler workspace
     * @throws Exception for compiler/JVM failures */
    @Test void explicitNpeMessageUsesConcreteOverride(@TempDir Path directory) throws Exception {
        var classes=BytecodeFixtures.compile(directory,17,"""
            @Model public class Fixture {public boolean result;public Fixture(){}
              @Actor(id=0)public void actor(){Throwable error=new NullPointerException("x");result=error.getMessage()=="x";}}
            """);
        assertEquals("true",BytecodeFixtures.oracle(directory,classes,"Fixture f=new Fixture();f.actor();System.out.print(f.result);"));
        var program=BytecodeProgramLoader.load(classes,"Fixture");BytecodeBatchingOracleTest.compare(program);
        assertEquals(new BytecodeState.Int(1),BytecodeFixtures.run(program,program.initialState(),0).fixtureField("result"));
    }
    /** Numeric suppression order survives double-digit indices and detached result arrays.
     * @param directory compiler workspace
     * @throws Exception for compiler/JVM failures */
    @Test void longSuppressionListRetainsIdentityOrder(@TempDir Path directory) throws Exception {
        var classes=BytecodeFixtures.compile(directory,17,"""
            @Model public class Fixture {public Throwable primary=new Throwable();public boolean result;public Fixture(){}
              @Actor(id=0)public void actor(){Throwable[] errors=new Throwable[12];
                for(int i=0;i<12;i++){errors[i]=new Throwable();primary.addSuppressed(errors[i]);}
                Throwable[] copy=primary.getSuppressed();result=copy.length==12;
                for(int i=0;i<12;i++)result=result && copy[i]==errors[i];}}
            """);
        assertEquals("true",BytecodeFixtures.oracle(directory,classes,"Fixture f=new Fixture();f.actor();System.out.print(f.result);"));
        var limits=new BytecodeLimits(4096,128,8192,32,64,256,4096,128,8192,1024);
        var program=BytecodeProgramLoader.load(classes,"Fixture",limits);BytecodeBatchingOracleTest.compare(program);
        var state=BytecodeFixtures.run(program,program.initialState(),0);
        assertEquals(new BytecodeState.Int(1),state.fixtureField("result"));
        // Twelve six-cell errors plus two thirteen-cell arrays: 72+26=98 cells, fourteen headers.
        assertEquals(14,state.executions.get(0).allocations);assertEquals(98,state.executions.get(0).cells);
    }

    /** Returned-array bounds and caller cell budgets end the intrinsic body with the monitor held.
     * @param directory compiler workspace
     * @throws Exception for fixture failures */
    @Test void suppressionArrayResourceCutoffsRetainReceiver(@TempDir Path directory) throws Exception {
        var classes=BytecodeFixtures.compile(directory,17,"""
            @Model public class Fixture {public Throwable primary=new Throwable();public Fixture(){
                primary.addSuppressed(new Throwable());primary.addSuppressed(new Throwable());primary.addSuppressed(new Throwable());}
              @Actor(id=0)public void actor(){primary.getSuppressed();}}
            """);
        for(boolean length:java.util.List.of(true,false)) {
            var limits=new BytecodeLimits(256,128,length ? 8192 : 3,32,length ? 2 : 64,256,4096,128,8192,1024);
            var program=BytecodeProgramLoader.load(classes,"Fixture",limits);BytecodeBatchingOracleTest.compare(program);
            var result=InterleaveRunner.builder().build().runBytecode(program);
            assertFalse(result.hasViolation());assertTrue(result.hasIncomplete());
            var data=result.incompleteTraces().getFirst().bytecodeEvidence();var cutoff=data.terminal().cutoff();
            assertEquals(length ? "ARRAY_LENGTH" : "HEAP_CELLS",cutoff.reason());
            assertEquals(length ? 3 : 4,cutoff.required());assertEquals(length ? 2 : 3,cutoff.remaining());
            assertEquals(0,cutoff.budgetOwner());assertEquals("actor",cutoff.location().name());
            assertTrue(data.snapshots().getLast().heap().stream().anyMatch(o->o.owner()==0 && o.depth()==1));
        }
    }

    /** Whole-program message admission includes helpers without their own exception tables.
     * @param directory compiler workspace
     * @throws Exception for fixture failures */
    @Test void forwardedImplicitMessageRejects(@TempDir Path directory) throws Exception {
        var classes=BytecodeFixtures.compile(directory,17,"""
            @Model public class Fixture {public Object absent;public boolean result;public Fixture(){}
              static boolean read(Throwable fault){return fault.getMessage()!=null;}
              @Actor(id=0)public void actor(){try{synchronized(absent){}}catch(NullPointerException fault){result=read(fault);}}}
            """);
        var failure=assertThrows(BytecodeLoadException.class,()->BytecodeProgramLoader.load(classes,"Fixture"));
        assertEquals("UNSUPPORTED",failure.category);assertTrue(failure.getMessage().contains("message"));
    }

    /** Trusted module metadata cannot be replaced by same-named directory entries or model selection.
     * @param directory compiler workspace
     * @throws Exception for fixture failures */
    @Test void trustedHierarchyIgnoresPlatformShadowFiles(@TempDir Path directory) throws Exception {
        var classes=BytecodeFixtures.compile(directory,17,"""
            @Model public class Fixture {public boolean result;public Fixture(){}
              @Actor(id=0)public void actor(){result=new Throwable("known").getMessage()=="known";}}
            """);
        var shadow=classes.resolve("java/lang/Throwable.class");Files.createDirectories(shadow.getParent());Files.write(shadow,new byte[]{0});
        var program=BytecodeProgramLoader.load(classes,"Fixture");
        assertEquals(new BytecodeState.Int(1),BytecodeFixtures.run(program,program.initialState(),0).fixtureField("result"));
        assertEquals("UNSUPPORTED",assertThrows(BytecodeLoadException.class,()->BytecodeProgramLoader.load(classes,"java.lang.Throwable")).category);
    }

}
