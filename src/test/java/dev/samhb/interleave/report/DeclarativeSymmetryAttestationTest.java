/** Positive observations and inconclusive expectations for certified declarative symmetry reports. */
package dev.samhb.interleave.report;

import com.google.gson.Gson;
import dev.samhb.interleave.bugs.BenchmarkProgram;
import dev.samhb.interleave.core.*;
import dev.samhb.interleave.format.ProgramLoader;
import dev.samhb.interleave.search.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/** Tests actual reported observations, including forged or disabled execution claims. */
class DeclarativeSymmetryAttestationTest {
    /** Real step errors validate without requiring an unrelated invariant to fail. */
    @Test void actualFinalStepFailureWithTrueInvariantIsValid() {
        var model = model(null, List.of("x = 1", "x = x % 0"), "true");
        var rows = new BenchmarkHarness(1024,4,Set.of(StoreType.EXACT),null,2,false,true).runProgram(model);
        assertEquals(4, rows.size());
        var attestation = new SoundnessAttestation(rows,List.of(model));
        assertTrue(attestation.isSound(),attestation.failureReason());
        assertTrue(attestation.formatMarkdown().contains("actual certified declarative step failures were checked with enabled original choices"));
        assertTrue(rows.stream().allMatch(row -> row.verdict().equals("VIOLATION")));
        var root=model.program().initialConfiguration();
        var scratch=(dev.samhb.interleave.format.dsl.DynamicState) root.state().deepCopy();
        assertEquals(StepOutcome.ASSERTION_FAILED,model.program().threads().getFirst().steps().getFirst().execute(scratch));
        assertEquals(1,scratch.getInt("x"),"ordered effects precede the error in private scratch");
        assertNull(TransitionExecutor.execute(model.program(),root,0).successor());
        assertEquals(0,((dev.samhb.interleave.format.dsl.DynamicState)root.state()).getInt("x"));
    }

    /** Failed attempts retain the committed prefix in full, partial and harness diagnostics.
     * @throws Exception if producer artifact writes fail
     */
    @Test void checkedFailureSnapshotsRetainCommittedPrefix() throws Exception {
        var model=new ProgramLoader().loadFromResource("dsl/symmetry-committed-failure.json");
        var rows=new BenchmarkHarness(1024,4,Set.of(StoreType.EXACT),null,2,false,true).runProgram(model);
        assertEquals(4,rows.size());
        for(var row:rows) {
            assertEquals("VIOLATION",row.verdict());
            assertCommittedFailureStates(row.failingTrace().orElseThrow().length(),row.states());
        }
        java.nio.file.Files.createDirectories(java.nio.file.Path.of("build"));
        java.nio.file.Files.writeString(java.nio.file.Path.of("build/declarative-symmetry-failure-report.json"),new ReportWriter(rows,List.of(model)).writeJson());
        for(var strategy:dev.samhb.interleave.Strategy.values())for(int limit:List.of(0,3)) {
            var result=dev.samhb.interleave.InterleaveRunner.builder().strategy(strategy).symmetryReduction(true)
                .maxStates(limit).invariant(model.invariant().orElseThrow()).build().run(model.program());
            assertTrue(result.hasViolation());assertEquals(limit>0,result.limitExceeded());
            assertFalse(result.failingTraces().isEmpty());
            for(var trace:result.failingTraces())assertCommittedFailureStates(trace.threadIds().size(),trace.states());
            if(strategy==dev.samhb.interleave.Strategy.DFS && limit==0)java.nio.file.Files.writeString(
                java.nio.file.Path.of("build/declarative-symmetry-failure-library.json"),result.toJson());
        }
        var original=rows.getFirst().failingTrace().orElseThrow();
        var legacy=new TraceReplayer().replay(model.program(),original);
        assertEquals(7,((dev.samhb.interleave.format.dsl.DynamicState)legacy.state()).getInt("x"),"ordinary legacy replay retains its existing partial-effect behavior");
    }

    /** Checks one immutable state per action boundary, repeating the last committed state on failure.
     * @param length attempted choices including the failed last action
     * @param states exported state snapshots
     */
    private static void assertCommittedFailureStates(int length,List<StateSnapshot> states) {
        assertEquals(length+1,states.size());
        assertEquals(0,states.getFirst().fields().get("x"));
        assertEquals(1,states.getLast().fields().get("x"));
        assertEquals(states.get(states.size()-2),states.getLast());
        assertTrue(states.stream().noneMatch(state->Integer.valueOf(7).equals(state.fields().get("x"))));
    }

    /** Legacy replay would execute disabled choices and cannot authenticate a no-property violation. */
    @Test void fabricatedAndTrailingViolationsFailWithoutThrowing() {
        var blocked = model("false",List.of("x = x % 0"),null);
        assertFalse(attest(blocked,new Trace(List.of(0),List.of(StepOutcome.ASSERTION_FAILED),TraceOutcome.VIOLATION)));
        var safe=model(null,List.of("x = 1"),null);
        assertFalse(attest(safe,new Trace(List.of(0),List.of(StepOutcome.ADVANCED),TraceOutcome.VIOLATION)));
        assertFalse(attest(safe,new Trace(List.of(),List.of(),TraceOutcome.VIOLATION)));
        assertFalse(attest(safe,new Trace(List.of(9),List.of(StepOutcome.ADVANCED),TraceOutcome.VIOLATION)));
        assertFalse(attest(safe,new Trace(List.of(-1),List.of(StepOutcome.ADVANCED),TraceOutcome.VIOLATION)));
        assertFalse(attest(safe,new Trace(List.of(0,0),List.of(StepOutcome.ADVANCED,StepOutcome.ADVANCED),TraceOutcome.VIOLATION)));
        var failure=model(null,List.of("x = x % 0"),"true");
        assertFalse(attest(failure,new Trace(List.of(0,1),List.of(StepOutcome.ASSERTION_FAILED,StepOutcome.ASSERTION_FAILED),TraceOutcome.VIOLATION)));
        assertFalse(attest(failure,new Trace(List.of(0),List.of(StepOutcome.ADVANCED),TraceOutcome.VIOLATION)));
        var always=model(null,List.of("x = 1"),"x == 0");
        assertTrue(attest(always,new Trace(List.of(0),List.of(StepOutcome.ADVANCED),TraceOutcome.VIOLATION)));
        assertFalse(attest(always,new Trace(List.of(0,1),List.of(StepOutcome.ADVANCED,StepOutcome.ADVANCED),TraceOutcome.VIOLATION)));
        var root=model(null,List.of("x = 1"),"false");
        assertTrue(attest(root,new Trace(List.of(),List.of(),TraceOutcome.VIOLATION)));
        assertFalse(attest(root,new Trace(List.of(0),List.of(StepOutcome.ADVANCED),TraceOutcome.VIOLATION)));
    }

    /** A real step error cannot authenticate fabricated source or cutoff diagnostics. */
    @Test void declarativeViolationRejectsForeignEvidence() {
        var model=model(null,List.of("x = x % 0"),"true");
        var foreign=new TraceEvidence(null,List.of(),null,
            new CutoffEvidence(CutoffEvidence.Reason.PREEMPTION_BOUND,0,0,0,null,null,List.of()));
        assertFalse(attest(model,new Trace(List.of(0),List.of(StepOutcome.ASSERTION_FAILED),TraceOutcome.VIOLATION,foreign)));
    }

    /** Invalid positive metadata fails closed with an actionable subject/strategy/store diagnostic. */
    @Test void positiveCertificationFailuresKeepConcreteDiagnostics() {
        var model=model(null,List.of("x = 1"),null);
        var trace=new Trace(List.of(0),List.of(StepOutcome.ADVANCED),TraceOutcome.VIOLATION);
        var row=new BenchmarkResult("DPOR",model.name(),1,0,0,"VIOLATION",trace,StoreType.EXACT)
            .withExploration(model.program(),new DfsResult(Map.of(),List.of(trace),1),true);
        var invalid=new SoundnessAttestation(List.of(row),List.of(model));
        assertFalse(invalid.isSound());
        assertEquals("Invalid declarative symmetry violation for subject under DPOR (EXACT)",invalid.failureReason());
        var state=(dev.samhb.interleave.format.dsl.DynamicState)model.program().initialConfiguration().state();
        var property=new dev.samhb.interleave.format.dsl.DslInvariant(new dev.samhb.interleave.format.dsl.Expr.BoolLit(false),state.decl());
        var untrusted=new BenchmarkProgram(model.name(),model.program(),null,property);
        assertFalse(attest(untrusted,new Trace(List.of(),List.of(),TraceOutcome.VIOLATION)));
        var callback=new BenchmarkProgram(model.name(),model.program(),null,(value,config)->{throw new AssertionError("uncertified property invoked");});
        assertFalse(attest(callback,trace));
    }

    /** Source symmetry retains its existing qualified attestation prose. */
    @Test void sourceSymmetryRetainsExistingAttestationWording() {
        var model=new dev.samhb.interleave.format.SourceProgramLoader().load(
            "program source { shared int x = 0; thread 0 { atomic { x = x + 1; } } thread 1 { atomic { x = x + 1; } } invariant final (x == 2); }");
        var rows=new BenchmarkHarness(1024,4,Set.of(StoreType.EXACT),null,2,false,true).runProgram(model);
        var attestation=new SoundnessAttestation(rows,List.of(model));
        assertTrue(attestation.isSound(),attestation.failureReason());
        var prose=attestation.formatMarkdown();
        assertTrue(prose.contains("Source DFS results that were INCOMPLETE"));
        assertTrue(prose.contains("source runtime or assertion failures were authenticated"));
        assertFalse(prose.contains("certified declarative"));
    }

    /** A real interrupted DFS is inconclusive, while an untrusted marker grants no exception. */
    @Test void certifiedIncompleteDoesNotBecomeExpectationMismatch() {
        var model=model(null,List.of("x = 1"),"true");
        var expected=new BenchmarkProgram(model.name(),model.program(),"PASS",model.invariant().orElseThrow());
        var incomplete=new BenchmarkResult("DFS",model.name(),1,0,0,"INCOMPLETE",null,StoreType.EXACT)
            .withExploration(model.program(),new DfsResult(Map.of(),List.of(),1),true);
        assertTrue(new SoundnessAttestation(List.of(incomplete),List.of(expected)).isSound());
        var forged=new BenchmarkProgram(expected.name(),expected.program(),"PASS",(s,c)->true);
        assertFalse(new SoundnessAttestation(List.of(incomplete),List.of(forged)).isSound());
        var expectedFailure=new BenchmarkProgram(model.name(),model.program(),"VIOLATION",model.invariant().orElseThrow());
        var inconclusive=new SoundnessAttestation(List.of(incomplete),List.of(expectedFailure));
        assertTrue(inconclusive.isSound(),inconclusive.failureReason());
        assertEquals("INCOMPLETE",incomplete.verdict());
        assertTrue(inconclusive.formatMarkdown().contains("inconclusive, not matched verdicts"));
    }

    /** Attests a marked non-DFS row so positive-witness checks run independently of expectations.
     * @param model registered trusted model
     * @param trace claimed positive observation
     * @return whether concrete positive validation succeeds
     */
    private static boolean attest(BenchmarkProgram model,Trace trace) {
        var row=new BenchmarkResult("STATIC_POR",model.name(),1,0,0,"VIOLATION",trace,StoreType.EXACT)
            .withExploration(model.program(),new DfsResult(Map.of(),List.of(trace),1),true);
        return new SoundnessAttestation(List.of(row),List.of(model)).isSound();
    }

    /** Loads matching actions with an optional shared always predicate.
     * @param guard optional guard
     * @param effects ordered effects
     * @param property optional shared predicate
     * @return real loader-validated pair
     */
    private static BenchmarkProgram model(String guard,List<String> effects,String property) {
        var action=new LinkedHashMap<String,Object>();
        action.put("effects",effects);if(guard!=null)action.put("guard",guard);
        var document=new LinkedHashMap<String,Object>();
        document.put("format","declarative");document.put("name","subject");
        document.put("state",Map.of("fields",List.of(Map.of("name","x","type","int","init",0)),"locals",List.of()));
        document.put("threads",List.of(Map.of("id",0,"steps",List.of(action)),Map.of("id",1,"steps",List.of(action))));
        if(property!=null)document.put("invariant",Map.of("when","always","expr",property));
        return new ProgramLoader().load(new Gson().toJson(document));
    }
}
