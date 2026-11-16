/** Internal exact DFS over complete bytecode states; source Configuration and its forward PCs stay unchanged. */
package dev.samhb.interleave.bytecode;

import java.util.*;
import static dev.samhb.interleave.bytecode.BytecodeState.*;

/** Exact full-payload enumeration with bounded checker storage and concrete internal witnesses. */
final class BytecodeExplorer {
    /** Internal terminal classifications; public verdict/evidence adapters belong to94C. */
    enum Kind {
        /** Every actor returned normally. */ COMPLETED,
        /** All remaining actors wait on owned monitors, with no pending event. */ DEADLOCK,
        /** A selected uncaught actor fault ended the prefix. */ UNCAUGHT_EXCEPTION,
        /** A modeled resource cutoff ended the prefix. */ CUTOFF,
        /** An external checker budget or cancellation stopped traversal. */ INTERRUPTED
    }

    /** Explicit checker budgets, separate from modeled instruction/allocation limits.
     * @param maxStates maximum admitted exact states
     * @param maxKeyBytes maximum retained canonical payload bytes, excluding JVM overhead
     */
    record Budget(long maxStates,long maxKeyBytes) {
        /** Rejects negative budgets; zero requests an immediate honest interruption. */
        Budget {if(maxStates<0 || maxKeyBytes<0)throw new IllegalArgumentException("Negative search budget");}
    }

    /** A typed model field cell; NULL as container identifies a static slot.
     * @param object owning object or static-table sentinel
     * @param field declared field identity
     */
    record Cell(Ref object,BytecodeProgram.FieldId field) implements Comparable<Cell> {
        /** Orders field identities without relying on diagnostic text.
         * @param other another shared cell
         * @return deterministic order */
        @Override public int compareTo(Cell other) {
            int order=object.compareTo(other.object);return order==0 ? field.compareTo(other.field) : order;
        }
    }

    /** Concrete monitor wait relationship at a deadlock.
     * @param actor waiting actor
     * @param owner current owning actor
     * @param monitor permanent monitor identity
     * @param depth owning reentrant depth
     */
    record Wait(int actor,int owner,Ref monitor,int depth) {}

    /** Normalized relevant outcome, excluding schedules and intermediate local snapshots.
     * @param kind terminal classification
     * @param actor failing/cutoff actor, or -1
     * @param fault original interpreted failure, if present
     * @param cutoff omitted modeled operation, if present
     * @param interruption external checker reason, otherwise null
     * @param waits concrete deadlock relationships
     * @param shared relevant shared field observations
     */
    record Outcome(Kind kind,int actor,Fault fault,Cutoff cutoff,String interruption,List<Wait> waits,Map<Cell,Value> shared) {
        /** Freezes observable payload containers. */
        Outcome {waits=List.copyOf(waits);shared=Collections.unmodifiableMap(new TreeMap<>(shared));}
    }

    /** A selected scheduling transition with actual opcode/event positions.
     * @param actor original actor identity
     * @param executed committed opcode/event locations
     */
    record Selection(int actor,List<BytecodeScheduler.Executed> executed) {
        /** Freezes transition metadata. */
        Selection {executed=List.copyOf(executed);}
    }

    /** A concrete internal witness retained once per normalized outcome.
     * @param outcome normalized terminal observation
     * @param state isolated final/prefix state
     * @param schedule concrete selected transition sequence
     */
    record Witness(Outcome outcome,BytecodeState state,List<Selection> schedule) {
        /** Owns state and freezes the schedule, independently of the search stack. */
        Witness {state=state.copy();schedule=List.copyOf(schedule);}
    }

    /** Actual search and canonical-encoding work; retained payload bytes are not JVM retained memory.
     * @param states admitted unique states
     * @param transitions selected scheduling transitions
     * @param opcodes executed opcodes
     * @param instructions charged instruction/intrinsic work
     * @param encodingCalls canonical encoding invocations
     * @param encodingBytes generated canonical bytes, including duplicates
     * @param encodingNanos measured time in canonical encoding
     * @param retainedKeyBytes live stored canonical payload bytes
     */
    record Metrics(long states,long transitions,long opcodes,long instructions,long encodingCalls,
                   long encodingBytes,long encodingNanos,long retainedKeyBytes) {}

    /** Run result with original incompleteness preserved alongside positive witnesses.
     * @param witnesses one concrete schedule per normalized outcome
     * @param traversalComplete whether all bounded scheduling alternatives were enumerated
     * @param metrics actual run counters
     */
    record Result(List<Witness> witnesses,boolean traversalComplete,Metrics metrics) {
        /** Freezes the result list. */
        Result {witnesses=List.copyOf(witnesses);}
        /** Distinguishes bounded model/observer limits from exhaustive absence within the profile.
         * @return whether traversal finished without any incomplete prefix */
        boolean exhaustive() {
            return traversalComplete && witnesses.stream().noneMatch(w->w.outcome.kind==Kind.CUTOFF || w.outcome.kind==Kind.INTERRUPTED);
        }
    }

    /** Persistent path node avoids copying complete schedules at every search state.
     * @param previous selected prefix
     * @param selection last selected transition
     */
    private record Path(Path previous,Selection selection) {}

    /** An iterative DFS stack frame; its target snapshot is never mutated by successors. */
    private static final class Frame {
        /** Owned committed state. */ final BytecodeState state;
        /** Concrete path to this state. */ final Path path;
        /** Pure enabled original actor identities. */ final List<Integer> choices;
        /** Next untried actor choice. */ int next;
        /** Creates one stack frame.
         * @param state committed state
         * @param path concrete prefix
         * @param choices enabled choices */
        Frame(BytecodeState state,Path path,List<Integer> choices){this.state=state;this.path=path;this.choices=choices;}
    }

    /** Immutable validated artifact. */ private final BytecodeProgram program;
    /** Mode-aware selected transitions. */ private final BytecodeScheduler scheduler;
    /** Hash prefilter buckets retain complete canonical payloads, matching HashingStateStore's exact policy. */
    private final Map<Integer,List<byte[]>> visited=new HashMap<>();
    /** Concrete outcomes found by this run. */ private final Map<Outcome,Witness> witnesses=new LinkedHashMap<>();
    /** Current admitted state count. */ private long states;
    /** Selected transitions. */ private long transitions;
    /** Executed opcodes. */ private long opcodes;
    /** Executed instruction/intrinsic work. */ private long instructions;
    /** Encoding calls. */ private long encodingCalls;
    /** Encoded bytes. */ private long encodingBytes;
    /** Time spent in encoding. */ private long encodingNanos;
    /** Retained canonical payload bytes. */ private long keyBytes;
    /** False only for an external checker stop. */ private boolean traversalComplete;

    /** Creates an internal search owner; independent instances may share the immutable program.
     * @param program validated model
     * @param mode scheduling granularity
     */
    BytecodeExplorer(BytecodeProgram program,BytecodeScheduler.Mode mode) {
        this.program=Objects.requireNonNull(program);scheduler=new BytecodeScheduler(program,mode);
    }

    /** Runs exact DFS with a64MiB canonical-payload safety budget.
     * @param maxStates admitted state budget
     * @return complete or honestly interrupted run */
    Result explore(long maxStates){return explore(new Budget(maxStates,64L*1024*1024));}

    /** Enumerates bounded executions without using the host call stack for DFS.
     * Sequential reuse resets search storage; concurrent runs use independent explorer instances.
     * @param budget checker budgets, distinct from modeled limits
     * @return normalized outcomes and real witnesses */
    Result explore(Budget budget) {
        Objects.requireNonNull(budget);visited.clear();witnesses.clear();states=transitions=opcodes=instructions=0;
        encodingCalls=encodingBytes=encodingNanos=keyBytes=0;traversalComplete=true;
        var stack=new ArrayDeque<Frame>();var root=visit(program.initialState(),null,budget);
        if(root!=null)stack.push(root);
        while(traversalComplete && !stack.isEmpty()) {
            var frame=stack.peek();
            if(frame.next==frame.choices.size()){stack.pop();continue;}
            if(Thread.currentThread().isInterrupted()){interrupt(frame.state,frame.path,"CANCELLED");break;}
            if(states>=budget.maxStates()){interrupt(frame.state,frame.path,"MAX_STATES");break;}
            int actor=frame.choices.get(frame.next++);var transition=scheduler.advance(frame.state,actor);
            if(transition.kind()==OpcodeExecutor.Kind.BLOCKED)throw new IllegalStateException("Pure choice became disabled");
            transitions++;instructions+=transition.instructions();
            opcodes+=transition.executed().stream().filter(e->e.opcode()!=null).count();
            var path=new Path(frame.path,new Selection(actor,transition.executed()));
            var next=visit(transition.state(),path,budget);if(next!=null)stack.push(next);
        }
        return new Result(new ArrayList<>(witnesses.values()),traversalComplete,
            new Metrics(states,transitions,opcodes,instructions,encodingCalls,encodingBytes,encodingNanos,keyBytes));
    }

    /** Admits one exact state, then classifies its terminal result or enabled choices.
     * @param state owned successor
     * @param path selected prefix
     * @param budget checker budgets
     * @return an expandable frame, otherwise null */
    private Frame visit(BytecodeState state,Path path,Budget budget) {
        if(Thread.currentThread().isInterrupted()){interrupt(state,path,"CANCELLED");return null;}
        long start=System.nanoTime();byte[] key=state.canonicalBytes();encodingNanos+=System.nanoTime()-start;
        encodingCalls++;encodingBytes+=key.length;
        int hash=Arrays.hashCode(key);var bucket=visited.get(hash);
        if(bucket!=null && bucket.stream().anyMatch(previous->Arrays.equals(previous,key)))return null;
        if(states>=budget.maxStates()){interrupt(state,path,"MAX_STATES");return null;}
        if(key.length>budget.maxKeyBytes()-keyBytes){interrupt(state,path,"KEY_BYTES");return null;}
        visited.computeIfAbsent(hash,ignored->new ArrayList<>()).add(key);states++;keyBytes+=key.length;
        for(int actor=0;actor<program.actors.size();actor++) {
            var execution=state.executions.get(actor);
            if(execution.phase==Phase.FAILED || execution.phase==Phase.CUTOFF) {
                record(state,path,execution.phase==Phase.FAILED ? Kind.UNCAUGHT_EXCEPTION : Kind.CUTOFF,
                    actor,execution.fault,execution.cutoff,null,List.of());return null;
            }
        }
        if(program.actors.stream().allMatch(id->state.executions.get(program.actors.indexOf(id)).phase==Phase.TERMINATED)) {
            record(state,path,Kind.COMPLETED,-1,null,null,null,List.of());return null;
        }
        var choices=scheduler.choices(state);
        if(choices.isEmpty()) {
            var waits=new ArrayList<Wait>();
            for(int actor=0;actor<program.actors.size();actor++) {
                var probe=OpcodeExecutor.probe(program,state,actor);
                if(probe.availability()==OpcodeExecutor.Availability.BLOCKED) {
                    var monitor=state.heap.get(probe.monitor());waits.add(new Wait(actor,monitor.monitorOwner,probe.monitor(),monitor.monitorDepth));
                }
            }
            record(state,path,Kind.DEADLOCK,-1,null,null,null,waits);return null;
        }
        return new Frame(state,path,choices);
    }

    /** Records a checker stop without inventing a modeled resource cutoff.
     * @param state last committed state
     * @param path real prefix
     * @param reason external checker cause */
    private void interrupt(BytecodeState state,Path path,String reason) {
        traversalComplete=false;record(state,path,Kind.INTERRUPTED,-1,null,null,reason,List.of());
    }

    /** Retains one concrete witness per normalized observable outcome.
     * @param state committed terminal state
     * @param path selected prefix
     * @param kind terminal classification
     * @param actor terminal actor or -1
     * @param fault original interpreted fault
     * @param cutoff omitted modeled operation
     * @param interruption external reason
     * @param waits concrete monitor relationships */
    private void record(BytecodeState state,Path path,Kind kind,int actor,Fault fault,Cutoff cutoff,String interruption,List<Wait> waits) {
        var shared=new TreeMap<Cell,Value>();state.statics.forEach((field,value)->shared.put(new Cell(NULL,field),value));
        state.heap.forEach((reference,object)->{if(object.shared)object.fields.forEach((field,value)->shared.put(new Cell(reference,field),value));});
        var outcome=new Outcome(kind,actor,fault,cutoff,interruption,waits,shared);
        if(witnesses.containsKey(outcome))return;
        var schedule=new ArrayList<Selection>();for(var node=path;node!=null;node=node.previous())schedule.add(node.selection());
        Collections.reverse(schedule);witnesses.put(outcome,new Witness(outcome,state,schedule));
    }
}
