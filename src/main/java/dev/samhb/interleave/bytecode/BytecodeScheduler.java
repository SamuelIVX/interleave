/** Internal actor selection over real bytecode continuations, independent of source forward PCs. */
package dev.samhb.interleave.bytecode;

import java.lang.classfile.Opcode;
import java.lang.classfile.instruction.FieldInstruction;
import java.util.*;

/** Pure choices and isolated selected transitions using the single opcode executor. */
final class BytecodeScheduler {
    /** Supported internal scheduling granularities. */
    enum Mode {
        /** One opcode or explicit zero-work event per selection. */ REFERENCE,
        /** At most one observable operation plus justified local work. */ BATCHED
    }

    /** One executed opcode or implicit event at a real method/BCI position.
     * @param location real interpreted position
     * @param opcode executed opcode, null for an implicit event or omitted fuel operation
     */
    record Executed(BytecodeLocation location,Opcode opcode) {}

    /** An owned scheduling result; no source step ordinals are manufactured.
     * @param state isolated successor
     * @param kind executed/pending/blocked/terminal result
     * @param executed committed opcode/event locations
     * @param instructions charged actor work, distinct from scheduling selections
     * @param sharing actual reference-publication traversal work
     */
    record Transition(BytecodeState state,OpcodeExecutor.Kind kind,List<Executed> executed,int instructions,BytecodeSharing.Work sharing) {
        /** Freezes the transition's location list. */
        Transition {executed=List.copyOf(executed);}
    }

    /** Immutable validated model. */
    private final BytecodeProgram program;
    /** Selected production granularity. */
    private final Mode mode;

    /** Creates an internal scheduler with no mutable target state.
     * @param program validated model
     * @param mode scheduling granularity
     */
    BytecodeScheduler(BytecodeProgram program,Mode mode) {
        this.program=Objects.requireNonNull(program);this.mode=Objects.requireNonNull(mode);
    }

    /** Enumerates enabled original actor identities without committing probes.
     * @param state committed state
     * @return deterministic enabled choices, including selectable pending terminal events
     */
    List<Integer> choices(BytecodeState state) {
        var choices=new ArrayList<Integer>();
        for(int actor=0;actor<program.actors.size();actor++)
            if(OpcodeExecutor.probe(program,state,actor).availability()==OpcodeExecutor.Availability.READY)choices.add(actor);
        return List.copyOf(choices);
    }

    /** Executes one selected actor on an isolated state.
     * @param parent committed state, never modified
     * @param actor original actor identity
     * @return isolated opcode/event result
     */
    Transition advance(BytecodeState parent,int actor) {
        if(actor<0 || actor>=program.actors.size())throw new IllegalArgumentException("Unknown actor");
        var initial=OpcodeExecutor.probe(program,parent,actor);
        if(initial.availability()!=OpcodeExecutor.Availability.READY) {
            var result=OpcodeExecutor.step(program,parent,actor);
            return new Transition(result.state(),result.kind(),List.of(),0,BytecodeSharing.Work.NONE);
        }
        var state=parent;var executed=new ArrayList<Executed>();boolean observable=false;
        int instructions=0;var sharing=BytecodeSharing.Work.NONE;var kind=OpcodeExecutor.Kind.ADVANCED;
        while(true) {
            var probe=OpcodeExecutor.probe(program,state,actor);
            if(probe.availability()!=OpcodeExecutor.Availability.READY || (observable && probe.observable()))break;
            var before=state.executions.get(actor);boolean publication=false;
            if(probe.opcode()==Opcode.PUTFIELD || probe.opcode()==Opcode.PUTSTATIC) {
                var frame=before.frames.getLast();var field=(FieldInstruction)frame.method.ops.get(frame.bci).instruction();
                publication=field.type().stringValue().startsWith("L");
            }
            var result=OpcodeExecutor.step(program,state,actor);var next=result.state();
            int charged=next.executions.get(actor).instructions-before.instructions;
            executed.add(new Executed(probe.location(),charged==0 ? null : probe.opcode()));
            instructions+=charged;kind=result.kind();state=next;observable|=probe.observable();
            if(publication)sharing=sharing.plus(BytecodeSharing.refresh(state));
            // Every discovered return/fault/cutoff yields before terminal delivery, including local failures.
            if(mode==Mode.REFERENCE || kind!=OpcodeExecutor.Kind.ADVANCED
                || state.executions.get(actor).phase!=BytecodeState.Phase.RUNNING)break;
        }
        return new Transition(state,kind,executed,instructions,sharing);
    }
}
