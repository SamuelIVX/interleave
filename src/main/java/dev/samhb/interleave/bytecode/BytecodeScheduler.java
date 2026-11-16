/** Internal actor selection over real bytecode continuations, independent of source forward PCs. */
package dev.samhb.interleave.bytecode;

import java.lang.classfile.Opcode;
import java.util.*;

/** Pure choices and isolated selected transitions using the single opcode executor. */
final class BytecodeScheduler {
    /** Supported internal scheduling granularities. */
    enum Mode {
        /** One opcode or explicit zero-work event per selection. */ REFERENCE
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
     */
    record Transition(BytecodeState state,OpcodeExecutor.Kind kind,List<Executed> executed,int instructions) {
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
        var probe=OpcodeExecutor.probe(program,parent,actor);
        var result=OpcodeExecutor.step(program,parent,actor);
        int instructions=result.state().executions.get(actor).instructions-parent.executions.get(actor).instructions;
        var executed=result.kind()==OpcodeExecutor.Kind.BLOCKED || probe.availability()==OpcodeExecutor.Availability.DONE
            ? List.<Executed>of() : List.of(new Executed(probe.location(),instructions==0 ? null : probe.opcode()));
        return new Transition(result.state(),result.kind(),executed,instructions);
    }
}
