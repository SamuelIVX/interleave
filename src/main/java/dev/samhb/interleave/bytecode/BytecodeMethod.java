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
    /** Numeric constants frozen by BCI, preserving raw floating constant-pool bits. */
    final Map<Integer,BytecodeState.Value> constants;

    /**
     * Decodes a validated application method without executing it.
     * @param owner internal class name
     * @param model method metadata
     * @param raw raw numeric constant lookup from validated frozen owner bytes
     */
    BytecodeMethod(String owner, MethodModel model,BytecodeInput.RawConstants raw) {
        id = new Id(owner, model.methodName().stringValue(), model.methodType().stringValue());
        flags = model.flags().flagsMask();
        code = (CodeAttribute) model.code().orElseThrow();
        var decoded = new TreeMap<Integer, Op>();var numeric=new HashMap<Integer,BytecodeState.Value>();
        int bci = 0;
        for (CodeElement element : code) {
            if (element instanceof Instruction instruction) {
                int next = bci + instruction.sizeInBytes();
                decoded.put(bci, new Op(bci, instruction, next));
                if(instruction instanceof ConstantInstruction constant && constant.constantValue() instanceof Number)
                    numeric.put(bci,constant instanceof ConstantInstruction.LoadConstantInstruction loaded
                        ? raw.numeric(loaded.constantEntry().index()) : BytecodePrimitives.constant(constant.constantValue()));
                bci = next;
            }
        }
        if (bci != code.codeLength() || decoded.isEmpty()) throw new IllegalArgumentException("Invalid code length");
        ops = Collections.unmodifiableNavigableMap(decoded);constants=Map.copyOf(numeric);
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

    /** Selects the first table entry matching the real position and modeled exception type.
     * @param bci throwing or invoking position
     * @param type actual modeled Throwable type
     * @param hierarchy closed metadata
     * @return matching handler target, or -1 */
    int handler(int bci,String type,BytecodeHierarchy hierarchy) {return handler(code.exceptionHandlers(),bci,type,hierarchy);}

    /** Selects the first matching entry from an ordered candidate subset.
     * Runtime delivery supplies the table; verification can supply only entries covering the BCI.
     * @param candidates table-order handler candidates
     * @param bci real throwing position
     * @param type modeled Throwable type
     * @param hierarchy closed type metadata
     * @return selected handler BCI, or -1 */
    int handler(List<ExceptionCatch> candidates,int bci,String type,BytecodeHierarchy hierarchy) {
        for(ExceptionCatch handler:candidates)if(bci>=target(handler.tryStart()) && bci<target(handler.tryEnd())
            && (handler.catchType().isEmpty() || hierarchy.assignable(type,handler.catchType().orElseThrow().asInternalName())))return target(handler.handler());
        return -1;
    }

    /** Filters one position once, preserving table order and the universal-handler shortcut.
     * Entries after a covering catch-all/Throwable cannot select any modeled Throwable type.
     * @param bci protected instruction position
     * @return immutable covering candidates through the first universal match */
    List<ExceptionCatch> coveringHandlers(int bci) {
        var result=new ArrayList<ExceptionCatch>();
        for(var handler:code.exceptionHandlers())if(bci>=target(handler.tryStart()) && bci<target(handler.tryEnd())) {
            result.add(handler);
            if(handler.catchType().isEmpty() || handler.catchType().orElseThrow().asInternalName().equals("java/lang/Throwable"))break;
        }
        return List.copyOf(result);
    }

    /** Recognizes only metadata-safe five-op compiler monitor cleanup transport.
     * @param target validated handler entry
     * @return whether no target operation can inspect or publish the exception token */
    boolean metadataCleanup(int target) {
        if(code.exceptionHandlers().stream().noneMatch(h->target(h.handler())==target && h.catchType().isEmpty()))return false;
        var sequence=new ArrayList<Instruction>();int position=target;
        for(int i=0;i<5;i++){var op=ops.get(position);if(op==null)return false;sequence.add(op.instruction());position=op.next();}
        return sequence.get(0) instanceof StoreInstruction saved && saved.typeKind()==TypeKind.REFERENCE
            && sequence.get(1) instanceof LoadInstruction lock && lock.typeKind()==TypeKind.REFERENCE
            && sequence.get(2).opcode()==Opcode.MONITOREXIT
            && sequence.get(3) instanceof LoadInstruction restored && restored.typeKind()==TypeKind.REFERENCE
            && saved.slot()==restored.slot() && saved.slot()!=lock.slot() && sequence.get(4).opcode()==Opcode.ATHROW;
    }
}
