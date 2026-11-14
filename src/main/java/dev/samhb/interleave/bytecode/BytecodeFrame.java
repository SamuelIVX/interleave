/**
 * Owned invocation frame using real bytecode continuations, not source step ordinals.
 */
package dev.samhb.interleave.bytecode;

import java.util.*;

/**
 * Mutable only within an owned execution snapshot.
 */
final class BytecodeFrame {
    /**
     * Immutable interpreted method.
     */
    final BytecodeMethod method;
    /**
     * Local slots, with null denoting a verifier-uninitialized slot.
     */
    final NavigableMap<Integer,BytecodeState.Value> locals = new TreeMap<>();
    /**
     * Category-one operand values for the initial profile.
     */
    final ArrayList<BytecodeState.Value> stack = new ArrayList<>();
    /**
     * Real current byte index.
     */
    int bci;
    /**
     * Whether implicit synchronized entry has completed.
     */
    boolean entered;
    /**
     * Acquired method monitor, null when not synchronized/entered.
     */
    BytecodeState.Ref methodMonitor;

    /**
     * Starts an invocation.
     * @param method validated method
     * @param arguments receiver followed by declared arguments
     */
    BytecodeFrame(BytecodeMethod method, List<BytecodeState.Value> arguments) {
        this.method = method;
        for (int i = 0; i < arguments.size(); i++) locals.put(i,arguments.get(i));
        entered = (method.flags & java.lang.classfile.ClassFile.ACC_SYNCHRONIZED) == 0;
    }

    /**
     * Copies a frame without aliasing operand/local containers.
     * @param original owned frame to copy
     */
    private BytecodeFrame(BytecodeFrame original) {
        method = original.method; locals.putAll(original.locals); stack.addAll(original.stack);
        bci = original.bci; entered = original.entered; methodMonitor = original.methodMonitor;
    }

    /**
     * Copies mutable containers while retaining immutable metadata and value identities.
     * @return isolated mutable frame
     */
    BytecodeFrame copy() { return new BytecodeFrame(this); }

    /**
     * Removes and returns the top modeled operand.
     * @return top operand, removing it
     */
    BytecodeState.Value pop() { return stack.removeLast(); }

    /**
     * Appends operands in bottom-to-top order.
     * @param value next operand
     */
    void push(BytecodeState.Value value) { stack.add(value); }
}
