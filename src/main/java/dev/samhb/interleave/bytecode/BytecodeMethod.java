/**
 * Frozen decoded method positions shared by verification and the opcode executor.
 */
package dev.samhb.interleave.bytecode;

import java.lang.classfile.*;
import java.lang.classfile.attribute.CodeAttribute;
import java.lang.classfile.instruction.*;
import java.util.*;

/**
 * Immutable interpreted method, keyed by a complete JVM descriptor.
 */
final class BytecodeMethod {
    /**
     * Overload identity; names are internal class names, never host classes.
     * @param owner declaring class
     * @param name method name
     * @param descriptor JVM descriptor
     */
    record Id(String owner, String name, String descriptor) {}
    /**
     * Decoded position.
     * @param bci actual byte index
     * @param instruction immutable Class-File API instruction
     * @param next following byte index
     */
    record Op(int bci, Instruction instruction, int next) {}
    /**
     * Declaring method identity.
     */
    final Id id;
    /**
     * Class-file access flags.
     */
    final int flags;
    /**
     * Original bounded code attribute.
     */
    final CodeAttribute code;
    /**
     * Real byte indices, in execution order.
     */
    final NavigableMap<Integer, Op> ops;

    /**
     * Decodes a validated application method without executing it.
     * @param owner internal class name
     * @param model method metadata
     */
    BytecodeMethod(String owner, MethodModel model) {
        id = new Id(owner, model.methodName().stringValue(), model.methodType().stringValue());
        flags = model.flags().flagsMask();
        code = (CodeAttribute) model.code().orElseThrow();
        var decoded = new TreeMap<Integer, Op>();
        int bci = 0;
        for (CodeElement element : code) {
            if (element instanceof Instruction instruction) {
                int next = bci + instruction.sizeInBytes();
                decoded.put(bci, new Op(bci, instruction, next));
                bci = next;
            }
        }
        if (bci != code.codeLength() || decoded.isEmpty()) throw new IllegalArgumentException("Invalid code length");
        ops = Collections.unmodifiableNavigableMap(decoded);
    }

    /**
     * Represents an actual method position without inventing source coordinates.
     * @param bci actual byte index
     * @return artifact-bound method location
     */
    BytecodeLocation location(int bci) { return new BytecodeLocation(id.owner(), id.name(), id.descriptor(), bci); }

    /**
     * Resolves a bound method label to its real byte index.
     * @param label bound method label
     * @return actual branch byte index
     */
    int target(Label label) { return code.labelToBci(label); }

    /**
     * Selects the first cleanup handler covering a throwing position.
     * @param bci throwing position
     * @return first compiler cleanup handler, or -1
     */
    int handler(int bci) {
        for (ExceptionCatch handler : code.exceptionHandlers()) {
            if (bci >= target(handler.tryStart()) && bci < target(handler.tryEnd())) return target(handler.handler());
        }
        return -1;
    }
}
