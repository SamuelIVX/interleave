/** Owned integration operations shared by the public loader, runner and strict replay route. */
package dev.samhb.interleave.bytecode;

import dev.samhb.interleave.*;
import dev.samhb.interleave.core.*;
import dev.samhb.interleave.search.TraceOutcome;
import java.lang.classfile.Attributes;
import java.nio.file.Path;
import java.time.Duration;
import java.util.*;

/** Deep bytecode integration seam; no mutable execution state or caller-supplied callback is exposed.
 * Library clients normally use {@link dev.samhb.interleave.format.BytecodeProgramLoader}, whose class
 * example shows loading with limits, execution, retained-trace selection and matching strict replay. */
public final class BytecodeRuntime {
    /** Prevents construction of this stateless integration facade. */
    private BytecodeRuntime() {}

    /** Freezes and validates one immutable regular-file directory classpath and interprets setup.
     * @param directory immutable input directory during loading
     * @param modelClass selected binary fixture class name
     * @param limits validated immutable execution allowances
     * @return immutable interpreted artifact, independent of subsequent input changes
     * @throws BytecodeLoadException for input, metadata, unsupported behavior or setup failure */
    public static BytecodeProgram load(Path directory,String modelClass,BytecodeLimits limits){return BytecodeLoader.load(directory,modelClass,limits);}

    /** Runs owned exact DFS in the released batched profile, retaining all normalized witness categories.
     * External deadlines are checked at scheduling boundaries; they do not preempt individual opcodes.
     * @param program immutable loaded artifact
     * @param maxStates maximum admitted states, zero for the runner's unlimited selection
     * @param maxTime optional boundary-based millisecond deadline
     * @return immutable result with distinct BYTECODE diagnostics and exact storage metadata
     * @throws NullPointerException for a null program
     * @throws IllegalArgumentException for a negative state limit
     * @throws ArithmeticException if the duration cannot be represented in milliseconds */
    public static TestResult run(BytecodeProgram program,long maxStates,Duration maxTime) {
        Objects.requireNonNull(program);if(maxStates<0)throw new IllegalArgumentException("Negative state budget");
        long started=System.nanoTime(),before=usedHeap();
        var explorer=new BytecodeExplorer(program,BytecodeScheduler.Mode.BATCHED,maxTime,true);
        var result=explorer.explore(maxStates==0 ? Long.MAX_VALUE : maxStates);
        var failed=new ArrayList<TraceRecord>();var deadlocked=new ArrayList<TraceRecord>();
        var completed=new ArrayList<TraceRecord>();var incomplete=new ArrayList<TraceRecord>();
        ResourceLimitReason resource=null;
        for(var witness:result.witnesses()) {
            var trace=record(program,witness);switch(trace.outcome()) {
                case VIOLATION->failed.add(trace);case DEADLOCK->deadlocked.add(trace);
                case COMPLETED->completed.add(trace);case INCOMPLETE->incomplete.add(trace);
            }
            if(witness.outcome().interruption()!=null)resource=switch(witness.outcome().interruption()) {
                case "MAX_STATES"->ResourceLimitReason.MAX_STATES;case "MAX_TIME"->ResourceLimitReason.MAX_TIME;default->null;
            };
        }
        return new TestResult(Strategy.DFS,result.metrics().states(),(System.nanoTime()-started)/1_000_000,usedHeap()-before,
            failed,deadlocked,completed,incomplete,!result.traversalComplete()).withRunMetadata(false,resource,false);
    }

    /** Authenticates BYTECODE identity, operations, checks, snapshots, monitors and exact terminal meaning.
     * External records authenticate their captured prefix and retain the original incomplete outcome.
     * @param program frozen selected artifact
     * @param record immutable original record
     * @return owned replay record with separate actual prefix observations
     * @throws dev.samhb.interleave.search.CorruptedTraceException for identity/evidence mismatches
     * @throws IllegalScheduleException for a disabled or foreign actor
     * @throws java.util.concurrent.CancellationException for host interruption */
    public static TraceRecord replay(BytecodeProgram program,TraceRecord record){return BytecodeReplay.replay(program,record);}

    /** Constructs selected declaration identity independently of the manifest's possible shared closure.
     * @param program frozen loaded definition
     * @return immutable public profile/model identity */
    static BytecodeEvidence.Identity identity(BytecodeProgram program) {
        var actors=new ArrayList<BytecodeEvidence.Actor>();
        for(int actor=0;actor<program.actors.size();actor++)actors.add(new BytecodeEvidence.Actor(actor,method(program.actors.get(actor))));
        var checks=program.checks.stream().map(check->new BytecodeEvidence.Check(method(check.method()),check.always() ? "ALWAYS" : "FINAL")).toList();
        return new BytecodeEvidence.Identity(program.artifactDigest(),program.profileVersion(),program.modelClass(),actors,checks,
            program.limits(),"BATCHED","DFS","DFS",null);
    }

    /** Converts a genuine retained witness without reexecuting cancelled or faulted model code.
     * @param program frozen artifact supplying debug metadata
     * @param witness actual selected path and owned snapshots
     * @return public immutable trace */
    private static TraceRecord record(BytecodeProgram program,BytecodeExplorer.Witness witness) {
        var transitions=witness.schedule().stream().map(selection->transition(program,selection)).toList();
        var terminal=terminal(witness.outcome());var evidence=new BytecodeEvidence(1,identity(program),transitions,witness.snapshots(),terminal,null);
        var choices=witness.schedule().stream().map(BytecodeExplorer.Selection::actor).toList();
        var outcomes=witness.schedule().stream().map(BytecodeRuntime::stepOutcome).toList();
        return new TraceRecord(choices,outcomes,traceOutcome(terminal),program.artifactDigest(),
            witness.snapshots().stream().map(BytecodeSnapshots::display).toList(),null,evidence);
    }

    /** Converts real selected opcode/event positions using frozen optional debug metadata.
     * @param program frozen artifact
     * @param selection actual actor operation sequence
     * @return immutable descriptor/BCI transition */
    static BytecodeEvidence.Transition transition(BytecodeProgram program,BytecodeExplorer.Selection selection) {
        return new BytecodeEvidence.Transition(selection.actor(),selection.executed().stream().map(operation->
            new BytecodeEvidence.Operation(operation.location(),line(program,operation.location()),
                operation.opcode()==null ? null : operation.opcode().name(),operation.event())).toList());
    }

    /** Maps actual selected events into the legacy coarse step vocabulary without source continuations.
     * @param selection committed transition
     * @return coarse step outcome; precise cause remains in BYTECODE evidence */
    static StepOutcome stepOutcome(BytecodeExplorer.Selection selection) {
        String event=selection.executed().getLast().event();
        return event.equals("TERMINATION") ? StepOutcome.TERMINATED : event.equals("FAILURE") ? StepOutcome.ASSERTION_FAILED : StepOutcome.ADVANCED;
    }

    /** Maps precise terminal meaning to the existing four public outcome categories.
     * @param terminal actual bytecode observation
     * @return public terminal category */
    static TraceOutcome traceOutcome(BytecodeEvidence.Terminal terminal) {
        return switch(terminal.kind()) {
            case "COMPLETED"->TraceOutcome.COMPLETED;case "DEADLOCK"->TraceOutcome.DEADLOCK;
            case "CHECK_FALSE","UNCAUGHT_EXCEPTION"->TraceOutcome.VIOLATION;default->TraceOutcome.INCOMPLETE;
        };
    }

    /** Exports normalized actor/check/resource meaning, preserving the original cause and stack.
     * @param outcome internal observation, null for a runnable replay prefix
     * @return immutable public terminal/prefix */
    static BytecodeEvidence.Terminal terminal(BytecodeExplorer.Outcome outcome) {
        if(outcome==null)return new BytecodeEvidence.Terminal("PREFIX",-1,null,null,null,List.of(),null);
        var check=outcome.observer();var failure=check==null ? null : new BytecodeEvidence.CheckFailure(check.kind().name(),method(check.check()),check.location(),fault(check.fault()),cutoff(check.cutoff()));
        return new BytecodeEvidence.Terminal(outcome.kind().name(),outcome.actor(),fault(outcome.fault()),cutoff(outcome.cutoff()),failure,
            outcome.waits().stream().map(wait->new BytecodeEvidence.Wait(wait.actor(),wait.owner(),BytecodeSnapshots.identity(wait.monitor()),wait.depth())).toList(),outcome.interruption());
    }

    /** Retains descriptor-complete declaration identity.
     * @param method immutable internal declaration
     * @return public method identity */
    private static BytecodeEvidence.Method method(BytecodeMethod.Id method){return new BytecodeEvidence.Method(method.owner(),method.name(),method.descriptor());}

    /** Copies original modeled fault identity, excluding host exception objects.
     * @param fault optional modeled token
     * @return immutable fault, otherwise null */
    private static BytecodeEvidence.Fault fault(BytecodeState.Fault fault){return fault==null ? null : new BytecodeEvidence.Fault(fault.type(),fault.location(),fault.stack());}

    /** Copies exact omitted work and allowance.
     * @param cutoff optional modeled cutoff
     * @return public cutoff, otherwise null */
    private static BytecodeEvidence.Cutoff cutoff(BytecodeState.Cutoff cutoff){return cutoff==null ? null : new BytecodeEvidence.Cutoff(cutoff.reason(),cutoff.location(),cutoff.limit(),cutoff.required(),cutoff.remaining());}

    /** Reads optional compiler lines from already frozen metadata, with no filesystem or source-span inference.
     * @param program frozen artifact
     * @param location real interpreted position
     * @return positive compiler line, otherwise null */
    static Integer line(BytecodeProgram program,BytecodeLocation location) {
        var method=program.methods.get(new BytecodeMethod.Id(location.owner(),location.name(),location.descriptor()));
        var table=method.code.findAttribute(Attributes.lineNumberTable());int start=-1;Integer line=null;
        if(table.isPresent())for(var entry:table.get().lineNumbers())if(entry.startPc()<=location.bci() && entry.startPc()>=start) {
            start=entry.startPc();line=entry.lineNumber()>0 ? entry.lineNumber() : null;
        }
        return line;
    }

    /** Reads instantaneous JVM heap use, not a retained-memory benchmark or model heap budget.
     * @return current used JVM heap bytes */
    private static long usedHeap(){var runtime=Runtime.getRuntime();return runtime.totalMemory()-runtime.freeMemory();}
}
