/** Slot-group semantics for verifier-proven JVM stack manipulation. */
package dev.samhb.interleave.bytecode;

import java.lang.classfile.Opcode;
import java.util.ArrayList;
import java.util.List;
import java.util.function.ToIntFunction;

/** Shares all legal category-one/two forms between execution and reference-type transport. */
final class BytecodeStack {
    /** Prevents construction of this stateless stack operation. */
    private BytecodeStack() {}

    /** Applies one JVM stack operation to logical operands rather than individual slots.
     * The JDK verifier establishes the legal forms before either caller uses this operation.
     * @param <T> immutable operand or static-type representation
     * @param stack owned bottom-to-top operand list
     * @param opcode pop, dup or swap operation
     * @param width JVM slot width of one logical operand
     * @throws IllegalStateException if an internal caller violates the verified slot form */
    static <T> void apply(ArrayList<T> stack,Opcode opcode,ToIntFunction<T> width) {
        int top=opcode==Opcode.POP2 || opcode==Opcode.DUP2 || opcode==Opcode.DUP2_X1 || opcode==Opcode.DUP2_X2 ? 2 : 1;
        List<T> values=take(stack,top,width);
        if(opcode==Opcode.POP || opcode==Opcode.POP2)return;
        int under=switch(opcode) {
            case DUP,DUP2->0;case DUP_X1,DUP2_X1,SWAP->1;case DUP_X2,DUP2_X2->2;
            default->throw new IllegalStateException("Unvalidated stack opcode "+opcode);
        };
        List<T> beneath=take(stack,under,width);
        stack.addAll(values);stack.addAll(beneath);
        if(opcode!=Opcode.SWAP)stack.addAll(values);
    }

    /** Removes exactly a slot group without splitting a category-two value.
     * @param <T> operand representation
     * @param stack owned stack
     * @param slots required slot count
     * @param width logical operand width
     * @return removed values in original bottom-to-top order */
    private static <T> List<T> take(ArrayList<T> stack,int slots,ToIntFunction<T> width) {
        int first=stack.size(),remaining=slots;
        while(remaining>0)remaining-=width.applyAsInt(stack.get(--first));
        if(remaining!=0)throw new IllegalStateException("Split category-two operand");
        List<T> result=List.copyOf(stack.subList(first,stack.size()));stack.subList(first,stack.size()).clear();return result;
    }
}
