/** Minimal interned UTF-16 literals for exception messages, without host target callbacks. */
package dev.samhb.interleave.bytecode;

import java.lang.classfile.instruction.ConstantInstruction;
import java.util.*;
import static dev.samhb.interleave.bytecode.BytecodeState.*;

/** Owns deterministic constant identities and setup-only cell charging. */
final class BytecodeStrings {
    /** Prevents construction of this constant utility. */
    private BytecodeStrings() {}

    /** Collects admitted reachable literals and String ConstantValue fields.
     * @param methods interpreted application methods
     * @param intrinsics exact trusted targets admitted by the loader
     * @param fields application field declarations
     * @return immutable sorted-content identity mapping */
    static Map<String,Ref> identities(Map<BytecodeMethod.Id,BytecodeMethod> methods,Map<BytecodeProgram.FieldId,BytecodeProgram.Field> fields,Set<BytecodeMethod.Id> intrinsics) {
        var texts=new TreeSet<String>();
        methods.values().forEach(method->method.ops.values().forEach(op->{
            if(op.instruction() instanceof ConstantInstruction constant && constant.constantValue() instanceof String text)texts.add(text);
        }));
        fields.values().forEach(field->{if(field.initial() instanceof Literal literal)texts.add(literal.text());});
        boolean suppression=intrinsics.stream().anyMatch(method->method.name().equals("addSuppressed"));
        if(suppression){texts.add(BytecodeThrowables.SELF_MESSAGE);texts.add(BytecodeThrowables.NULL_MESSAGE);}
        var result=new TreeMap<String,Ref>();long sequence=1;
        for(String text:texts)result.put(text,new Ref(8,sequence++));return Collections.unmodifiableMap(result);
    }

    /** Installs immutable character-unit payloads, charging cells but no application allocations.
     * @param program frozen literals and limits
     * @param state setup state before its constructor
     * @throws BytecodeLoadException when literal storage exceeds String or setup cell bounds */
    static void install(BytecodeProgram program,BytecodeState state) {
        var setup=state.executions.get(SETUP);
        if(program.intrinsics.stream().anyMatch(method->method.name().equals("getSuppressed"))) {
            if(setup.cells==program.limits.setupHeapCells())throw BytecodeLoader.problem("LIMIT","Empty suppression constant exceeds setup cell allowance",null);
            var empty=new ObjectValue("[Ljava/lang/Throwable;");empty.initialized=true;empty.elements=new ArrayList<>();
            state.heap.put(BytecodeThrowables.EMPTY,empty);setup.cells++;
        }
        for(var entry:program.strings.entrySet()) {
            String text=entry.getKey();int cells=2+text.length();
            if(text.length()>program.limits.maxStringLength() || cells>program.limits.setupHeapCells()-setup.cells)
                throw BytecodeLoader.problem("LIMIT","String constants exceed String/setup cell allowance",null);
            var object=new ObjectValue("java/lang/String");object.initialized=true;
            object.fields.put(slot("length"),new Int(text.length()));
            for(int index=0;index<text.length();index++)object.fields.put(slot("unit"+index),new Int(text.charAt(index)));
            state.heap.put(entry.getValue(),object);setup.cells+=cells;
        }
    }

    /** Names an internal immutable String payload cell.
     * @param name length or UTF-16 unit member
     * @return stable internal identity */
    private static BytecodeProgram.FieldId slot(String name){return new BytecodeProgram.FieldId("@String",name,"I");}
}
