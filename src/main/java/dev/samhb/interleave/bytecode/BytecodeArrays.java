/** Array construction/access and reference type checks, sharing the single interpreted opcode executor. */
package dev.samhb.interleave.bytecode;

import java.lang.classfile.Instruction;
import java.lang.classfile.Opcode;
import java.lang.classfile.instruction.*;
import java.util.*;
import static dev.samhb.interleave.bytecode.BytecodeState.*;
import static dev.samhb.interleave.bytecode.OpcodeExecutor.*;

/** Keeps array semantics and permanent dimension accounting in one owned implementation. */
final class BytecodeArrays {
    /** Prevents construction of this stateless opcode family. */
    private BytecodeArrays() {}

    /** Identifies instructions whose complete semantics belong to this family.
     * @param instruction validated opcode
     * @return array operation or reference type check */
    static boolean handles(Instruction instruction) {
        return instruction instanceof NewPrimitiveArrayInstruction || instruction instanceof NewReferenceArrayInstruction
            || instruction instanceof NewMultiArrayInstruction || instruction instanceof ArrayLoadInstruction
            || instruction instanceof ArrayStoreInstruction || instruction instanceof TypeCheckInstruction || instruction.opcode()==Opcode.ARRAYLENGTH;
    }

    /** Executes one fueled opcode, preserving allocated dimensions when a later charge fails.
     * @param program validated program
     * @param state owned scratch
     * @param execution charged execution
     * @param frame selected operands and continuation
     * @param instruction validated operation
     * @param owner setup or actor allocator
     * @return whether the instruction completed normally */
    static boolean execute(BytecodeProgram program,BytecodeState state,Execution execution,BytecodeFrame frame,Instruction instruction,int owner) {
        if(instruction instanceof TypeCheckInstruction check) {
            Ref reference=(Ref)frame.pop();boolean matches=!reference.equals(NULL)
                && program.hierarchy.assignable(object(state,reference).type,check.type().asInternalName());
            if(check.opcode()==Opcode.INSTANCEOF)frame.push(new Int(matches ? 1 : 0));
            else {if(!reference.equals(NULL) && !matches)throw new Trap("java/lang/ClassCastException",null);frame.push(reference);}
            return true;
        }
        if(instruction instanceof NewPrimitiveArrayInstruction || instruction instanceof NewReferenceArrayInstruction || instruction instanceof NewMultiArrayInstruction) {
            String type=switch(instruction) {
                case NewPrimitiveArrayInstruction array -> "["+switch(array.typeKind()) {
                    case BOOLEAN->"Z";case BYTE->"B";case CHAR->"C";case SHORT->"S";case INT->"I";case LONG->"J";case FLOAT->"F";case DOUBLE->"D";
                    default->throw new IllegalStateException("Unvalidated primitive component");
                };
                case NewReferenceArrayInstruction array -> "["+array.componentType().asSymbol().descriptorString();
                case NewMultiArrayInstruction array -> array.arrayType().asSymbol().descriptorString();
                default -> throw new IllegalStateException("Unvalidated allocation");
            };
            int dimensions=instruction instanceof NewMultiArrayInstruction array ? array.dimensions() : 1;
            int[] lengths=new int[dimensions];for(int i=dimensions-1;i>=0;i--)lengths[i]=((Int)frame.pop()).value();
            for(int length:lengths)if(length<0)throw new Trap("java/lang/NegativeArraySizeException",null);
            Ref reference=allocate(program,state,execution,frame,owner,type,lengths,0);
            if(reference==null)return false;frame.push(reference);return true;
        }
        if(instruction.opcode()==Opcode.ARRAYLENGTH) {frame.push(new Int(object(state,(Ref)frame.pop()).elements.size()));return true;}
        Value value=instruction instanceof ArrayStoreInstruction ? frame.pop() : null;
        int index=((Int)frame.pop()).value();var array=object(state,(Ref)frame.pop());
        if(index<0 || index>=array.elements.size())throw new Trap("java/lang/ArrayIndexOutOfBoundsException",null);
        if(instruction instanceof ArrayLoadInstruction) {frame.push(array.elements.get(index));return true;}
        if(instruction.opcode()==Opcode.AASTORE) {
            Ref reference=(Ref)value;
            if(!reference.equals(NULL) && !program.hierarchy.assignable(object(state,reference).type,BytecodeHierarchy.referenceType(array.type.substring(1))))
                throw new Trap("java/lang/ArrayStoreException",null);
        } else {
            int integer=((Int)value).value();value=new Int(switch(array.type) {
                case "[Z"->integer & 1;case "[B"->(byte)integer;case "[C"->(char)integer;case "[S"->(short)integer;default->integer;
            });
        }
        array.elements.set(index,value);return true;
    }

    /** Allocates dimensions in deterministic depth-first JVM order; failed children never obtain identities.
     * @param program immutable limits
     * @param state owned heap
     * @param execution permanent allocator history
     * @param frame omitted operation location
     * @param owner allocation namespace
     * @param type exact current array descriptor
     * @param lengths validated nonnegative sizes
     * @param depth current dimension
     * @return allocated reference, or null after a prefix-ending resource cutoff */
    private static Ref allocate(BytecodeProgram program,BytecodeState state,Execution execution,BytecodeFrame frame,int owner,String type,int[] lengths,int depth) {
        int length=lengths[depth];
        if(length>program.limits.maxArrayLength()) {
            cutoff(execution,frame,"ARRAY_LENGTH",program.limits.maxArrayLength(),length,program.limits.maxArrayLength());return null;
        }
        if(!reserve(program,execution,frame,owner,1+length))return null;
        var reference=new Ref(owner,execution.nextSequence++);var array=new ObjectValue(type);array.initialized=true;array.shared=owner==SETUP;
        Value initial=switch(type.substring(1)) {
            case "J"->new WideZero("LONG");case "F"->new WideZero("FLOAT");case "D"->new WideZero("DOUBLE");
            case "Z","B","C","S","I"->new Int(0);default->NULL;
        };
        array.elements=new ArrayList<>(Collections.nCopies(length,initial));state.heap.put(reference,array);
        execution.allocations++;execution.cells+=1+length;
        if(depth+1<lengths.length)for(int i=0;i<length;i++) {
            Ref child=allocate(program,state,execution,frame,owner,type.substring(1),lengths,depth+1);
            if(child==null)return null;array.elements.set(i,child);
        }
        return reference;
    }
}
