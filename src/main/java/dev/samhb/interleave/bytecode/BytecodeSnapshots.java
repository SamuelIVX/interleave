/** Pure immutable bytecode snapshots and lossless display projections, without target callbacks. */
package dev.samhb.interleave.bytecode;

import dev.samhb.interleave.core.BytecodeEvidence;
import dev.samhb.interleave.core.StateSnapshot;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.*;
import static dev.samhb.interleave.bytecode.BytecodeState.*;

/** Creates owned evidence at search boundaries so cancelled runs never need to reexecute their prefixes. */
final class BytecodeSnapshots {
    /** Prevents construction of this stateless projection module. */
    private BytecodeSnapshots() {}

    /** Captures full-state authentication and immutable typed heap/frame/resource diagnostics.
     * @param state committed state, never modified
     * @param canonical complete canonical payload already encoded by membership
     * @return immutable diagnostic snapshot */
    static BytecodeEvidence.Snapshot capture(BytecodeState state,byte[] canonical) {
        var fields=new TreeMap<String,BytecodeEvidence.Value>();var locals=new TreeMap<String,Map<String,BytecodeEvidence.Value>>();
        var heap=new ArrayList<BytecodeEvidence.ObjectState>();
        state.statics.forEach((field,slot)->fields.put("static."+field(field),value(slot)));
        state.heap.forEach((reference,object)->{
            String id=identity(reference);heap.add(new BytecodeEvidence.ObjectState(id,object.type,object.shared,object.initialized,object.monitorOwner,object.monitorDepth));
            object.fields.forEach((field,slot)->fields.put(id+"."+field(field),value(slot)));
            if(object.elements!=null) {
                fields.put(id+".length",number(object.elements.size()));
                for(int i=0;i<object.elements.size();i++)fields.put(id+"["+i+"]",value(object.elements.get(i)));
            }
        });
        state.executions.forEach((actor,execution)->{
            var slots=new TreeMap<String,BytecodeEvidence.Value>();
            slots.put("phase",text(execution.phase.name()));slots.put("instructions",number(execution.instructions));
            slots.put("allocations",number(execution.allocations));slots.put("heapCells",number(execution.cells));
            slots.put("allocationSequence",new BytecodeEvidence.Value("LONG",Long.toString(execution.nextSequence)));
            if(execution.eventLocation!=null)slots.put("pending.location",text(execution.eventLocation.toString()));
            if(execution.returned!=null)slots.put("pending.return",value(execution.returned));
            if(execution.intrinsic!=null) {
                var call=execution.intrinsic;slots.put("intrinsic.method",text(call.method().toString()));
                slots.put("intrinsic.receiver",value(call.receiver()));slots.put("intrinsic.monitor",value(call.monitor()));
                slots.put("intrinsic.cost",number(call.cost()));slots.put("intrinsic.count",number(call.suppressionCount()));slots.put("intrinsic.result",value(call.result()));
                for(int index=0;index<call.arguments().size();index++)slots.put("intrinsic.argument"+index,value(call.arguments().get(index)));
            }
            if(execution.faultPayload!=null) {
                slots.put("pending.message",value(execution.faultPayload.message()));slots.put("pending.cause",value(execution.faultPayload.cause()));
                slots.put("pending.causeInitialized",new BytecodeEvidence.Value("BOOLEAN",Boolean.toString(execution.faultPayload.causeInitialized())));
            }
            if(execution.throwable!=null)slots.put("pending.throwable",value(execution.throwable));
            if(execution.fault!=null)slots.put("pending.fault",text(execution.fault.toString()));
            if(execution.cutoff!=null)slots.put("pending.cutoff",text(execution.cutoff.toString()));
            for(int index=0;index<execution.frames.size();index++) {
                var frame=execution.frames.get(index);String prefix="frame"+index+".";
                slots.put(prefix+"method",text(frame.method.id.toString()));slots.put(prefix+"bci",number(frame.bci));
                slots.put(prefix+"entered",new BytecodeEvidence.Value("BOOLEAN",Boolean.toString(frame.entered)));
                slots.put(prefix+"monitor",value(frame.methodMonitor));slots.put(prefix+"constructor",value(frame.constructorReceiver));
                frame.locals.forEach((slot,entry)->slots.put(prefix+"local"+slot,value(entry)));
                for(int operand=0;operand<frame.stack.size();operand++)slots.put(prefix+"stack"+operand,value(frame.stack.get(operand)));
            }
            if(actor==SETUP)slots.forEach((name,entry)->fields.put("@setup."+name,entry));
            else locals.put(Integer.toString(actor),slots);
        });
        try {return new BytecodeEvidence.Snapshot(HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(canonical)),fields,locals,heap);}
        catch(NoSuchAlgorithmException impossible){throw new IllegalStateException("Required SHA-256 unavailable",impossible);}
    }

    /** Projects typed values into the existing display-only snapshot interface without numeric narrowing.
     * @param snapshot immutable authenticated bytecode view
     * @return display snapshot with only immutable tagged string leaves */
    static StateSnapshot display(BytecodeEvidence.Snapshot snapshot) {
        var fields=new TreeMap<String,Object>();var locals=new TreeMap<String,Map<String,Object>>();
        snapshot.fields().forEach((name,entry)->fields.put(name,entry.type().toLowerCase(Locale.ROOT)+":"+entry.raw()));
        snapshot.locals().forEach((actor,slots)->{
            var row=new TreeMap<String,Object>();slots.forEach((name,entry)->row.put(name,entry.type().toLowerCase(Locale.ROOT)+":"+entry.raw()));locals.put(actor,row);
        });
        return new StateSnapshot(fields,locals);
    }

    /** Names permanent setup/actor/class-monitor identities without heap renumbering.
     * @param reference nonnull modeled identity
     * @return stable tagged identity */
    static String identity(Ref reference) {
        return (reference.owner()==SETUP ? "setup:" : reference.owner()== -2 ? "class:" : reference.owner()==8 ? "constant:" : "actor:"+reference.owner()+":")+reference.sequence();
    }

    /** Converts category-one slots and default-only wide array cells without numeric coercion.
     * @param slot immutable VM slot, or null for an uninitialized diagnostic slot
     * @return immutable lossless tagged value */
    private static BytecodeEvidence.Value value(Value slot) {
        return switch(slot) {
            case null -> new BytecodeEvidence.Value("NULL","");
            case Int integer -> number(integer.value());
            case Literal literal -> new BytecodeEvidence.Value("TEXT",literal.text());
            case Unavailable ignored -> new BytecodeEvidence.Value("TEXT","unavailable VM message");
            case WideZero wide -> new BytecodeEvidence.Value(wide.type().equals("LONG") ? "LONG" : wide.type()+"_BITS",wide.type().equals("LONG") ? "0" : wide.type().equals("FLOAT") ? "00000000" : "0000000000000000");
            case Ref reference -> reference.equals(NULL) ? new BytecodeEvidence.Value("NULL","") : new BytecodeEvidence.Value("REFERENCE",identity(reference));
            case Thrown thrown -> new BytecodeEvidence.Value("FAULT",thrown.fault().toString());
        };
    }

    /** Formats declared field identities rather than relying on implementation diagnostic text.
     * @param field immutable declared slot
     * @return exact owner/name/descriptor display key */
    private static String field(BytecodeProgram.FieldId field){return field.owner()+"."+field.name()+":"+field.descriptor();}

    /** Constructs one exact category-one integer.
     * @param value modeled integer/counter
     * @return decimal tagged integer */
    private static BytecodeEvidence.Value number(int value){return new BytecodeEvidence.Value("INT",Integer.toString(value));}

    /** Constructs immutable frame/phase diagnostic text.
     * @param value complete diagnostic text
     * @return tagged text */
    private static BytecodeEvidence.Value text(String value){return new BytecodeEvidence.Value("TEXT",value);}
}
