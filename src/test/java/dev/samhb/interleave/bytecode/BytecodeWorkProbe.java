/** Child-JVM entry point for independently validating PIT-only bytecode progress assertions. */
package dev.samhb.interleave.bytecode;

import java.nio.file.Path;
import java.util.TreeSet;

/** Never shipped in the production artifact or used as a model execution fallback. */
public final class BytecodeWorkProbe {
    /** Prevents construction of this child entry point. */
    private BytecodeWorkProbe() {}
    /** Runs a real fixture through internal search, reporting assertion failures separately from verdicts.
     * @param arguments fixture class directory and optional cancellation, budget or deadline probe mode
     */
    public static void main(String[] arguments) {
        try {
            var program=BytecodeLoader.load(Path.of(arguments[0]),"Fixture",BytecodeLimits.defaults());
            if(arguments.length>1 && arguments[1].equals("cancel")){cancelAfterCommit(program);System.out.println("CANCELLATION_OWNED");return;}
            if(arguments.length>1 && (arguments[1].equals("zero") || arguments[1].equals("positive"))) {
                var duration=arguments[1].equals("zero") ? java.time.Duration.ZERO : java.time.Duration.ofMillis(1);
                var result=new BytecodeExplorer(program,BytecodeScheduler.Mode.REFERENCE,duration,false).explore(Long.MAX_VALUE);
                boolean stopped=!result.traversalComplete() && result.metrics().states()==0 && result.witnesses().size()==1
                    && result.witnesses().getFirst().outcome().kind()==BytecodeExplorer.Kind.INTERRUPTED
                    && "MAX_TIME".equals(result.witnesses().getFirst().outcome().interruption());
                System.out.println(stopped ? arguments[1].equals("zero") ? "ZERO_DEADLINE" : "POSITIVE_DEADLINE" : "DEADLINE_MISSED");return;
            }
            long states=arguments.length>1 && arguments[1].equals("unbounded") ? Long.MAX_VALUE
                : arguments.length>1 && arguments[1].equals("state-zero") ? 0 : 100000;
            var result=new BytecodeExplorer(program,BytecodeScheduler.Mode.REFERENCE).explore(states);
            if(arguments.length>1 && arguments[1].equals("state-zero")) {
                boolean stopped=!result.traversalComplete() && result.metrics().states()==0 && result.witnesses().size()==1
                    && "MAX_STATES".equals(result.witnesses().getFirst().outcome().interruption());
                System.out.println(stopped ? "STATE_LIMIT" : "STATE_LIMIT_MISSED");return;
            }
            if(!result.exhaustive())throw new IllegalStateException("Probe traversal incomplete");
            var kinds=new TreeSet<String>();for(var witness:result.witnesses())kinds.add(witness.outcome().kind().toString());
            System.out.println("OUTCOMES "+kinds);
        } catch(AssertionError exceeded) {
            if(exceeded.getMessage()==null || !exceeded.getMessage().startsWith("Bytecode logical"))throw exceeded;
            System.out.println("BOUND_EXCEEDED");
        }
    }

    /** Verifies cancellation injected after exactly one real local, shared-write or implicit-entry step.
     * The replacement scheduler is test-only; no pause/callback is added to production code.
     * @param program immutable cancellation fixture */
    private static void cancelAfterCommit(BytecodeProgram program) {
        var scheduler=new BytecodeScheduler(program,BytecodeScheduler.Mode.BATCHED);
        for(int scenario=0;scenario<3;scenario++) {
            var parent=program.initialState();int actor=scenario==2 ? 1 : 0;
            if(scenario==1)for(int local=0;local<2;local++)parent=OpcodeExecutor.step(program,parent,actor).state();
            byte[] before=parent.canonicalBytes();
            try {scheduler.advance(parent,actor);throw new AssertionError("Injected cancellation was ignored");}
            catch(java.util.concurrent.CancellationException expected) {
                if(!Thread.currentThread().isInterrupted() || !java.util.Arrays.equals(before,parent.canonicalBytes()))throw new AssertionError("Cancelled scratch work mutated its parent");
            } finally {Thread.interrupted();}
        }
        var initial=program.initialState();var before=initial.canonicalBytes();
        try {
            var result=new BytecodeExplorer(program,BytecodeScheduler.Mode.BATCHED).explore(100000);
            if(result.traversalComplete() || result.witnesses().size()!=1 || result.witnesses().getFirst().outcome().kind()!=BytecodeExplorer.Kind.INTERRUPTED
                || !"CANCELLED".equals(result.witnesses().getFirst().outcome().interruption())
                || !java.util.Arrays.equals(before,result.witnesses().getFirst().state().canonicalBytes()))throw new AssertionError("Explorer did not preserve the external cancellation prefix");
        } finally {Thread.interrupted();}
    }

}
