/** Additional cross-package protected-receiver checks over JDK-verified bytecode. */
package dev.samhb.interleave.bytecode;

import java.lang.classfile.*;
import java.lang.classfile.attribute.*;
import java.lang.classfile.instruction.*;
import java.util.*;
import static java.lang.classfile.ClassFile.*;
import static dev.samhb.interleave.bytecode.BytecodeLoader.problem;

/** Uses verified expanded stack-map frames; no target reflection or alternate runtime executor. */
final class BytecodeAccessVerifier {
    /** Explicit verifier null type, assignable to every reference class. */
    private static final String NULL="<null>";
    /** Prevents construction of this stateless access check. */
    private BytecodeAccessVerifier() {}

    /** Checks protected receiver types after the full JDK operand/control-flow verifier succeeds.
     * Branch/handler entries use the verified expanded stack maps; straight-line transport tracks
     * static reference types only. This supplements member visibility, which the hierarchy-only
     * Class-File verifier cannot derive from field/method declarations.
     * @param method decoded admitted method
     * @param hierarchy frozen declarations
     * @param implicitExposed whether general handlers can expose unknown implicit VM messages
     * @param fields resolved field declarations
     * @throws BytecodeLoadException with LINKAGE for a non-null cross-package protected instance
     * receiver whose verified type is not assignable to the caller class */
    static void validate(BytecodeMethod method,BytecodeHierarchy hierarchy,Map<BytecodeProgram.FieldId,BytecodeProgram.Field> fields,boolean implicitExposed) {
        var frames=new HashMap<Integer,StackMapFrameInfo>();
        method.code.findAttribute(Attributes.stackMapTable()).ifPresent(table->table.entries().forEach(frame->frames.put(method.target(frame.target()),frame)));
        var locals=new HashMap<Integer,String>();var stack=new ArrayList<String>();int slot=0;
        if((method.flags & ACC_STATIC)==0)locals.put(slot++,method.id.owner());
        for(var parameter:java.lang.constant.MethodTypeDesc.ofDescriptor(method.id.descriptor()).parameterList()) {
            String token=type(parameter.descriptorString());locals.put(slot,token);slot+=width(token);
        }
        boolean active=true;
        for(var op:method.ops.values()) {
            var seed=frames.get(op.bci());
            if(seed!=null) {
                locals.clear();stack.clear();int index=0;
                for(var local:seed.locals()){String token=verificationType(local,method);locals.put(index,token);index+=width(token);}
                for(var operand:seed.stack())stack.add(verificationType(operand,method));active=true;
            }
            if(!active)continue;
            Instruction instruction=op.instruction();Opcode code=instruction.opcode();
            if(instruction instanceof FieldInstruction field) {
                var id=hierarchy.field(new BytecodeProgram.FieldId(field.owner().asInternalName(),field.name().stringValue(),field.type().stringValue()));
                boolean write=code==Opcode.PUTFIELD || code==Opcode.PUTSTATIC;
                if(code==Opcode.GETFIELD || code==Opcode.PUTFIELD)receiver(method,op,hierarchy,id.owner(),fields.get(id).flags(),stack.get(stack.size()-(write ? 2 : 1)));
                if(write)pop(stack);if(code==Opcode.GETFIELD || code==Opcode.PUTFIELD)pop(stack);
                if(!write)stack.add(type(field.type().stringValue()));continue;
            }
            if(instruction instanceof InvokeInstruction call) {
                int parameters=call.typeSymbol().parameterCount();boolean isStatic=code==Opcode.INVOKESTATIC;
                if(!isStatic && !call.owner().asInternalName().equals("java/lang/Object")) {
                    var id=hierarchy.method(call.owner().asInternalName(),call.name().stringValue(),call.type().stringValue());
                    String actual=stack.get(stack.size()-parameters-1);
                    receiver(method,op,hierarchy,id.owner(),hierarchy.flags(id),actual);
                    if(implicitExposed && BytecodePlatform.intrinsic(id) && id.name().equals("getMessage") && !knownMessage(actual,hierarchy))
                        throw problem("UNSUPPORTED","Cannot prove implicit VM message unavailable to getMessage",method.location(op.bci()));
                }
                for(int i=0;i<parameters+(isStatic ? 0 : 1);i++)pop(stack);
                String result=call.typeSymbol().returnType().descriptorString();if(!result.equals("V"))stack.add(type(result));continue;
            }
            if(instruction instanceof LoadInstruction load){stack.add(locals.get(load.slot()));continue;}
            if(instruction instanceof StoreInstruction store){locals.put(store.slot(),pop(stack));continue;}
            if(instruction instanceof IncrementInstruction)continue;
            if(instruction instanceof ConstantInstruction constant){stack.add(code==Opcode.ACONST_NULL ? NULL : constant.constantValue() instanceof String ? "java/lang/String" : constant.constantValue() instanceof Long ? "<long>" : constant.constantValue() instanceof Float ? "<float>" : constant.constantValue() instanceof Double ? "<double>" : "<int>");continue;}
            if(instruction instanceof NewObjectInstruction allocation){stack.add("!"+allocation.className().asInternalName());continue;}
            if(instruction instanceof NewPrimitiveArrayInstruction){pop(stack);stack.add("[I");continue;}
            if(instruction instanceof NewReferenceArrayInstruction array){pop(stack);stack.add("["+array.componentType().asSymbol().descriptorString());continue;}
            if(instruction instanceof NewMultiArrayInstruction array){for(int i=0;i<array.dimensions();i++)pop(stack);stack.add(array.arrayType().asInternalName());continue;}
            if(instruction instanceof TypeCheckInstruction check){pop(stack);stack.add(code==Opcode.INSTANCEOF ? "<int>" : check.type().asInternalName());continue;}
            if(instruction instanceof ArrayLoadInstruction){pop(stack);String array=pop(stack);stack.add(code==Opcode.AALOAD ? array.equals(NULL) ? NULL : type(array.substring(1)) : code==Opcode.LALOAD ? "<long>" : code==Opcode.FALOAD ? "<float>" : code==Opcode.DALOAD ? "<double>" : "<int>");continue;}
            if(instruction instanceof ArrayStoreInstruction){pop(stack);pop(stack);pop(stack);continue;}
            if(instruction instanceof ReturnInstruction || code==Opcode.ATHROW){active=false;continue;}
            if(instruction instanceof BranchInstruction) {
                if(code==Opcode.GOTO || code==Opcode.GOTO_W){active=false;continue;}
                pop(stack);if(code.name().startsWith("IF_ICMP") || code.name().startsWith("IF_ACMP"))pop(stack);continue;
            }
            if(instruction instanceof LookupSwitchInstruction || instruction instanceof TableSwitchInstruction){active=false;continue;}
            switch(code) {
                case NOP -> { }
                case ARRAYLENGTH -> {pop(stack);stack.add("<int>");}
                case MONITORENTER,MONITOREXIT,POP -> pop(stack);
                case POP2,DUP,DUP_X1,DUP_X2,DUP2,DUP2_X1,DUP2_X2,SWAP -> BytecodeStack.apply(stack,code,BytecodeAccessVerifier::width);
                case INEG,LNEG,FNEG,DNEG,I2B,I2C,I2S -> { }
                case I2L,F2L,D2L -> {pop(stack);stack.add("<long>");}
                case I2F,L2F,D2F -> {pop(stack);stack.add("<float>");}
                case I2D,L2D,F2D -> {pop(stack);stack.add("<double>");}
                case L2I,F2I,D2I -> {pop(stack);stack.add("<int>");}
                case LCMP,FCMPL,FCMPG,DCMPL,DCMPG -> {pop(stack);pop(stack);stack.add("<int>");}
                default -> {pop(stack);String left=pop(stack);stack.add(left);}
            }
        }
    }

    /** Enforces JVMS protected instance receiver constraints outside the declaring package.
     * @param method caller
     * @param op actual reference instruction
     * @param hierarchy closed type relation
     * @param owner declaring class
     * @param flags member flags
     * @param receiver verified static receiver type
     * @throws BytecodeLoadException with LINKAGE for a non-null cross-package protected instance
     * receiver not assignable to the caller class; this is an admission failure, not stack analysis */
    private static void receiver(BytecodeMethod method,BytecodeMethod.Op op,BytecodeHierarchy hierarchy,String owner,int flags,String receiver) {
        String caller=method.id.owner();receiver=plain(receiver);
        if((flags & ACC_PROTECTED)!=0 && !packageName(caller).equals(packageName(owner)) && !NULL.equals(receiver)
            && !hierarchy.assignable(receiver,caller))throw problem("LINKAGE","Protected receiver must be current class or subtype",method.location(op.bci()));
    }

    /** Proves that an available message getter cannot observe an unknown implicit VM payload.
     * Exact straight-line NEW identities are explicit; joins intentionally retain only static types.
     * @param receiver verifier type/origin token
     * @param hierarchy complete modeled Throwable relation
     * @return whether every possible receiver has an explicit or exact supported message */
    private static boolean knownMessage(String receiver,BytecodeHierarchy hierarchy) {
        if(NULL.equals(receiver) || receiver.startsWith("!"))return true;
        for(String fault:List.of("java/lang/NullPointerException","java/lang/ArithmeticException","java/lang/IllegalMonitorStateException",
            "java/lang/ClassCastException","java/lang/NegativeArraySizeException","java/lang/ArrayIndexOutOfBoundsException",
            "java/lang/ArrayStoreException","java/lang/IncompatibleClassChangeError"))
            if(hierarchy.assignable(fault,receiver))return false;
        return true;
    }

    /** Removes exact-allocation provenance when applying ordinary JVM type relationships.
     * @param token static type with optional exact NEW marker
     * @return ordinary type */
    private static String plain(String token){return token.startsWith("!") ? token.substring(1) : token;}

    /** Resolves a verified expanded stack-map item, including uninitialized allocation references.
     * @param info verified type item
     * @param method owning method
     * @return static type token */
    private static String verificationType(StackMapFrameInfo.VerificationTypeInfo info,BytecodeMethod method) {
        if(info instanceof StackMapFrameInfo.ObjectVerificationTypeInfo object)return object.className().asInternalName();
        if(info instanceof StackMapFrameInfo.UninitializedVerificationTypeInfo uninitialized)
            return ((NewObjectInstruction)method.ops.get(method.target(uninitialized.newTarget())).instruction()).className().asInternalName();
        return switch(info.tag()) {case 0->"<top>";case 2->"<float>";case 3->"<double>";case 4->"<long>";case 5->NULL;case 6->method.id.owner();default->"<int>";};
    }

    /** Converts one declared JVM type to a static access-analysis token.
     * @param descriptor field/argument/result descriptor
     * @return distinct computational primitive token or reference type */
    private static String type(String descriptor) {
        if(descriptor.startsWith("["))return descriptor;
        if(descriptor.startsWith("L"))return BytecodeHierarchy.referenceType(descriptor);
        return switch(descriptor){case "J"->"<long>";case "F"->"<float>";case "D"->"<double>";default->"<int>";};
    }
    /** Returns the verifier type's JVM local/operand width.
     * @param token static primitive/reference token
     * @return one or two slots */
    private static int width(String token){return "<long>".equals(token) || "<double>".equals(token) ? 2 : 1;}
    /** Pops a verifier-proven logical operand type, irrespective of slot width.
     * @param stack type stack
     * @return top type */
    private static String pop(ArrayList<String> stack){return stack.removeLast();}
    /** Extracts the internal package of an application class.
     * @param name internal name
     * @return package prefix */
    private static String packageName(String name){int slash=name.lastIndexOf('/');return slash<0 ? "" : name.substring(0,slash);}
}
