/** Pure observer timing, faults and budgets through the internal exact-search seam. */
package dev.samhb.interleave.bytecode;

import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

class BytecodeCheckTest {
    /** An initially false ALWAYS check ends the root prefix without selecting an actor.
     * @param mode scheduling granularity
     * @param directory compiler workspace
     * @throws Exception for fixture failures */
    @ParameterizedTest @EnumSource(BytecodeScheduler.Mode.class) void initialAlwaysFailurePrecedesActorChoices(BytecodeScheduler.Mode mode,@TempDir Path directory) throws Exception {
        var classes=BytecodeFixtures.compile(directory,26,"""
            @Model public class Fixture {public int x;public Fixture(){}
              @Actor(id=0)public void actor(){x=1;}
              @Check(when=Check.When.ALWAYS)public boolean valid(){return x!=0;}
            }
            """);
        var program=BytecodeLoader.load(classes,"Fixture",BytecodeLimits.defaults());
        var result=new BytecodeExplorer(program,mode).explore(10000);
        assertTrue(result.traversalComplete());assertEquals(1,result.witnesses().size());
        var witness=result.witnesses().getFirst();
        assertEquals(BytecodeExplorer.Kind.CHECK_FALSE,witness.outcome().kind());
        assertEquals("valid",witness.outcome().observer().check().name());
        assertEquals(new BytecodeState.Int(0),witness.state().fixtureField("x"));
        assertEquals(0,witness.state().executions.get(0).instructions);assertTrue(witness.schedule().isEmpty());
    }
    /** ALWAYS observes transient shared writes even when a later write would restore the property.
     * @param mode scheduling granularity
     * @param directory compiler workspace
     * @throws Exception for fixture failures */
    @ParameterizedTest @EnumSource(BytecodeScheduler.Mode.class) void transientFailureCannotBeHiddenByALaterWrite(BytecodeScheduler.Mode mode,@TempDir Path directory) throws Exception {
        var program=load(directory,"""
            @Actor(id=0)public void actor(){x=1;x=0;}
            @Check(when=Check.When.ALWAYS)public boolean valid(){return x!=1;}
            """,BytecodeLimits.defaults());
        var result=new BytecodeExplorer(program,mode).explore(10000);
        assertTrue(result.exhaustive());assertEquals(1,result.witnesses().size());
        assertEquals(BytecodeExplorer.Kind.CHECK_FALSE,result.witnesses().getFirst().outcome().kind());
        assertEquals(new BytecodeState.Int(1),result.witnesses().getFirst().state().fixtureField("x"));
    }

    /** FINAL checks distinguish the lost update from normal x=2 completion.
     * @param mode scheduling granularity
     * @param directory compiler workspace
     * @throws Exception for fixture failures */
    @ParameterizedTest @EnumSource(BytecodeScheduler.Mode.class) void finalCheckDetectsTheRealLostUpdate(BytecodeScheduler.Mode mode,@TempDir Path directory) throws Exception {
        var program=load(directory,"""
            @Actor(id=0)public void first(){x++;} @Actor(id=1)public void second(){x++;}
            @Check public boolean valid(){return x==2;}
            """,BytecodeLimits.defaults());
        var result=new BytecodeExplorer(program,mode).explore(100000);
        assertTrue(result.exhaustive());assertEquals(2,result.witnesses().size());
        for(var witness:result.witnesses()) {
            int x=((BytecodeState.Int)witness.state().fixtureField("x")).value();
            assertTrue(x==1 || x==2);assertEquals(x==1 ? BytecodeExplorer.Kind.CHECK_FALSE : BytecodeExplorer.Kind.COMPLETED,witness.outcome().kind());
            assertEquals(BytecodeState.Phase.TERMINATED,witness.state().executions.get(0).phase);
            assertEquals(BytecodeState.Phase.TERMINATED,witness.state().executions.get(1).phase);
        }
    }

    /** An observer exception aborts only its private invocation and is honestly incomplete.
     * @param mode scheduling granularity
     * @param directory compiler workspace
     * @throws Exception for fixture failures */
    @ParameterizedTest @EnumSource(BytecodeScheduler.Mode.class) void observerArithmeticFaultIsNotAnActorViolation(BytecodeScheduler.Mode mode,@TempDir Path directory) throws Exception {
        var program=load(directory,"""
            @Actor(id=0)public void actor(){x=1;}
            @Check(when=Check.When.ALWAYS)public boolean valid(){return 1/x>0;}
            """,BytecodeLimits.defaults());
        var result=new BytecodeExplorer(program,mode).explore(10000);
        assertTrue(result.traversalComplete());assertFalse(result.exhaustive());
        var witness=result.witnesses().getFirst();assertEquals(BytecodeExplorer.Kind.OBSERVER_ERROR,witness.outcome().kind());
        assertEquals("java/lang/ArithmeticException",witness.outcome().observer().fault().type());
        assertEquals(new BytecodeMethod.Id("Fixture","valid","()Z"),witness.outcome().observer().check());
        assertEquals(new BytecodeLocation("Fixture","valid","()Z",5),witness.outcome().observer().location());
        assertEquals(4,result.metrics().checkInstructions());assertEquals(1,result.metrics().checkInvocations());
        assertEquals(0,witness.state().executions.get(0).instructions);
        assertEquals(java.util.Set.of(-1,0),witness.state().executions.keySet());
    }

    /** Actor and observer instruction allowances are independent and observer fuel resets per invocation.
     * @param mode scheduling granularity
     * @param directory compiler workspace
     * @throws Exception for fixture failures */
    @ParameterizedTest @EnumSource(BytecodeScheduler.Mode.class) void observersHaveTheirOwnFuel(BytecodeScheduler.Mode mode,@TempDir Path directory) throws Exception {
        var program=load(directory,"""
            @Actor(id=0)public void actor(){}
            @Check public boolean valid(){return x==0;}
            """,limits(1,16,32));
        var result=new BytecodeExplorer(program,mode).explore(10000);
        assertTrue(result.exhaustive());assertEquals(BytecodeExplorer.Kind.COMPLETED,result.witnesses().getFirst().outcome().kind());
        assertEquals(1,result.metrics().checkInvocations());assertTrue(result.metrics().checkInstructions()>1);
        assertEquals(1,result.witnesses().getFirst().state().executions.get(0).instructions);
        program=load(directory.resolve("repeated"),"""
            @Actor(id=0)public void actor(){x++;}
            @Check(when=Check.When.ALWAYS)public boolean valid(){return x>=0;}
            """,limits(16,16,32));
        result=new BytecodeExplorer(program,mode).explore(10000);
        assertTrue(result.exhaustive());assertEquals(BytecodeExplorer.Kind.COMPLETED,result.witnesses().getFirst().outcome().kind());
        assertTrue(result.metrics().checkInstructions()>16);assertTrue(result.metrics().checkInvocations()>1);
        program=load(directory.resolve("loop"),"""
            @Actor(id=0)public void actor(){}
            @Check(when=Check.When.ALWAYS)public boolean valid(){while(true){}}
            """,limits(1,5,32));
        result=new BytecodeExplorer(program,mode).explore(10000);
        var witness=result.witnesses().getFirst();assertEquals(BytecodeExplorer.Kind.OBSERVER_LIMIT,witness.outcome().kind());
        assertEquals(new BytecodeMethod.Id("Fixture","valid","()Z"),witness.outcome().observer().check());
        assertEquals(5,witness.outcome().observer().cutoff().limit());assertEquals(5,result.metrics().checkInstructions());
        assertEquals(0,witness.state().executions.get(0).instructions);assertEquals(4,witness.state().executions.get(-1).instructions);
        assertFalse(result.exhaustive());
    }

    /** Observer recursion stops at the selected frame allowance, separately from actor completion.
     * @param mode scheduling granularity
     * @param directory compiler workspace
     * @throws Exception for fixture failures */
    @ParameterizedTest @EnumSource(BytecodeScheduler.Mode.class) void observerDepthExhaustionIsIncomplete(BytecodeScheduler.Mode mode,@TempDir Path directory) throws Exception {
        var program=load(directory,"""
            @Actor(id=0)public void actor(){}
            @Check public boolean valid(){return recurse();} static boolean recurse(){return recurse();}
            """,limits(1,100,2));
        var result=new BytecodeExplorer(program,mode).explore(10000);
        var witness=result.witnesses().getFirst();assertEquals(BytecodeExplorer.Kind.OBSERVER_LIMIT,witness.outcome().kind());
        assertEquals("CALL_DEPTH",witness.outcome().observer().cutoff().reason());
        assertEquals(2,witness.outcome().observer().cutoff().limit());assertEquals("recurse",witness.outcome().observer().location().name());
        assertEquals(BytecodeState.Phase.TERMINATED,witness.state().executions.get(0).phase);assertFalse(result.exhaustive());
    }

    /** FINAL cannot replace actor failure or cutoff with a false-check result.
     * @param mode scheduling granularity
     * @param directory compiler workspace
     * @throws Exception for fixture failures */
    @ParameterizedTest @EnumSource(BytecodeScheduler.Mode.class) void finalNeverRunsOnAnAbnormalPrefix(BytecodeScheduler.Mode mode,@TempDir Path directory) throws Exception {
        var bodies=java.util.List.of("x=1/zero();","while(true){}");
        var expected=java.util.List.of(BytecodeExplorer.Kind.UNCAUGHT_EXCEPTION,BytecodeExplorer.Kind.CUTOFF);
        for(int index=0;index<bodies.size();index++) {
            var program=load(directory.resolve("case"+index),"@Actor(id=0)public void actor(){"+bodies.get(index)
                +"} static int zero(){return 0;} @Check public boolean valid(){return false;}",limits(16,16,32));
            var result=new BytecodeExplorer(program,mode).explore(10000);
            assertEquals(expected.get(index),result.witnesses().getFirst().outcome().kind());assertEquals(0,result.metrics().checkInvocations());
            assertNull(result.witnesses().getFirst().outcome().observer());
        }
    }

    /** Deterministic check order preserves the first false check before a later observer error.
     * @param mode scheduling granularity
     * @param directory compiler workspace
     * @throws Exception for fixture failures */
    @ParameterizedTest @EnumSource(BytecodeScheduler.Mode.class) void orderedChecksStopAtTheFirstFailure(BytecodeScheduler.Mode mode,@TempDir Path directory) throws Exception {
        var program=load(directory,"""
            @Actor(id=0)public void actor(){}
            @Check(when=Check.When.ALWAYS)public boolean zError(){return 1/x>0;}
            @Check(when=Check.When.ALWAYS)public boolean aFalse(){return false;}
            """,BytecodeLimits.defaults());
        var result=new BytecodeExplorer(program,mode).explore(10000);
        assertEquals(BytecodeExplorer.Kind.CHECK_FALSE,result.witnesses().getFirst().outcome().kind());
        assertEquals("aFalse",result.witnesses().getFirst().outcome().observer().check().name());assertEquals(1,result.metrics().checkInvocations());
    }

    /** Read-only observers receive actual static state, which also reaches normalized terminal evidence.
     * @param directory compiler workspace
     * @throws Exception for fixture failures */
    @Test void observersReadAndReportCommittedStatics(@TempDir Path directory) throws Exception {
        var classes=BytecodeFixtures.compile(directory,26,"""
            @Model public class Fixture {public static int shared;public int x;public Fixture(){shared=2;}
              @Actor(id=0)public void actor(){x++;}
              @Check(when=Check.When.ALWAYS)public boolean valid(){return shared==2 && x>=0;}}
            """);
        var program=BytecodeLoader.load(classes,"Fixture",BytecodeLimits.defaults());
        for(var mode:BytecodeScheduler.Mode.values()) {
            var result=new BytecodeExplorer(program,mode).explore(100000);assertTrue(result.exhaustive());
            assertEquals(BytecodeExplorer.Kind.COMPLETED,result.witnesses().getFirst().outcome().kind());
            var cell=new BytecodeExplorer.Cell(BytecodeState.NULL,new BytecodeProgram.FieldId("Fixture","shared","I"));
            assertEquals(new BytecodeState.Int(2),result.witnesses().getFirst().outcome().shared().get(cell));
        }
    }

    /** Cancellation of the private observer leaves committed state and the interrupt flag intact.
     * @param directory compiler workspace
     * @throws Exception for fixture failures */
    @Test void observerCancellationLeavesCommittedStateUntouched(@TempDir Path directory) throws Exception {
        var program=load(directory,"@Actor(id=0)public void actor(){} @Check(when=Check.When.ALWAYS)public boolean valid(){return true;}",BytecodeLimits.defaults());
        var state=program.initialState();var before=state.canonicalBytes();
        try {
            Thread.currentThread().interrupt();var result=BytecodeChecks.evaluate(program,state,true);
            assertEquals(BytecodeChecks.Kind.INTERRUPTED,result.failure().kind());assertEquals(1,result.invocations());assertEquals(0,result.instructions());
            assertEquals(new BytecodeMethod.Id("Fixture","valid","()Z"),result.failure().check());
            assertNull(result.failure().location());assertNull(result.failure().fault());assertNull(result.failure().cutoff());
            assertTrue(Thread.currentThread().isInterrupted());assertArrayEquals(before,state.canonicalBytes());
        } finally {Thread.interrupted();}
    }

    /** Compiles a first-profile fixture with caller-selected actor/check declarations.
     * @param directory compiler workspace
     * @param members actor/check/helper declarations
     * @param limits immutable execution profile
     * @return loaded immutable artifact
     * @throws Exception for fixture failures */
    private static BytecodeProgram load(Path directory,String members,BytecodeLimits limits) throws Exception {
        var classes=BytecodeFixtures.compile(directory,26,"@Model public class Fixture {public int x;public Fixture(){} "+members+"}");
        return BytecodeLoader.load(classes,"Fixture",limits);
    }

    /** Chooses independent actor/observer fuel and common frame depth for boundary cases.
     * @param actor actor fuel
     * @param check per-check fuel
     * @param depth frame allowance
     * @return immutable limits */
    private static BytecodeLimits limits(int actor,int check,int depth) {
        return new BytecodeLimits(actor,128,8192,depth,64,256,4096,128,8192,check);
    }

}
