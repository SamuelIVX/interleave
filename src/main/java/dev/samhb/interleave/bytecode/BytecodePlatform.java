/** Explicit trusted platform metadata for the exception profile; never loads target classes. */
package dev.samhb.interleave.bytecode;

import java.io.IOException;
import java.lang.classfile.*;
import java.util.*;
import static dev.samhb.interleave.bytecode.BytecodeLoader.problem;

/** Parses java.base resources only; metadata availability does not admit arbitrary JDK execution. */
final class BytecodePlatform {
    /** Types whose references are admitted by the current profile. */
    private static final Set<String> ADMITTED=Set.of("java/lang/Throwable","java/lang/Exception","java/lang/RuntimeException",
        "java/lang/NullPointerException","java/lang/ArithmeticException","java/lang/IllegalMonitorStateException",
        "java/lang/ClassCastException","java/lang/NegativeArraySizeException","java/lang/ArrayIndexOutOfBoundsException",
        "java/lang/ArrayStoreException","java/lang/IndexOutOfBoundsException","java/lang/IllegalArgumentException",
        "java/lang/IllegalStateException","java/lang/Error","java/lang/LinkageError","java/lang/IncompatibleClassChangeError",
        "java/lang/String","java/lang/AutoCloseable");
    /** Immutable bootstrap hierarchy cache; no target classpath bytes enter it. */
    private static volatile Map<String,ClassModel> bootstrap;
    /** Prevents construction of this metadata utility. */
    private BytecodePlatform() {}

    /** Tests explicit reference admission, independently of the broader metadata-only ancestor closure.
     * @param name JVM internal type
     * @return whether the selected profile admits this platform reference */
    static boolean admitted(String name){return ADMITTED.contains(name);}

    /** Resolves frozen platform metadata without application class loading or reflection.
     * @return immutable java.base hierarchy closure
     * @throws BytecodeLoadException for unavailable trusted bootstrap metadata */
    static Map<String,ClassModel> metadata() {
        var cached=bootstrap;if(cached!=null)return cached;
        var types=new TreeMap<String,ClassModel>();var pending=new ArrayDeque<>(ADMITTED);
        while(!pending.isEmpty()) {
            String name=pending.removeFirst();if(types.containsKey(name))continue;
            try(var input=Object.class.getModule().getResourceAsStream(name+".class")) {
                if(input==null)throw problem("METADATA","Missing trusted platform type "+name,null);
                var model=ClassFile.of().parse(input.readAllBytes());types.put(name,model);
                model.superclass().ifPresent(parent->pending.add(parent.asInternalName()));
                model.interfaces().forEach(face->pending.add(face.asInternalName()));
            }catch(IOException failure){throw problem("METADATA","Cannot read trusted platform type "+name,null);}
        }
        cached=Map.copyOf(types);bootstrap=cached;return cached;
    }
    /** Distinguishes supported construction from metadata-only ancestors and reference types.
     * @param name trusted internal type
     * @return whether this profile models allocation and declared constructors */
    static boolean constructible(String name) {
        return ADMITTED.contains(name) && !Set.of("java/lang/String","java/lang/AutoCloseable","java/lang/Error","java/lang/LinkageError").contains(name);
    }

    /** Tests exact declared trusted operations; metadata alone never permits execution.
     * @param id resolved declaration
     * @return whether the exception profile supplies this operation */
    static boolean intrinsic(BytecodeMethod.Id id) {
        if(id==null || !ADMITTED.contains(id.owner()) || id.owner().equals("java/lang/String") || id.owner().equals("java/lang/AutoCloseable"))return false;
        if(id.name().equals("<init>"))return constructible(id.owner()) && (Set.of("()V","(Ljava/lang/String;)V","(Ljava/lang/String;Ljava/lang/Throwable;)V").contains(id.descriptor())
            || id.owner().equals("java/lang/Throwable") && id.descriptor().equals("(Ljava/lang/String;Ljava/lang/Throwable;ZZ)V"));
        return id.owner().equals("java/lang/Throwable") && Set.of("getMessage()Ljava/lang/String;","getCause()Ljava/lang/Throwable;",
            "addSuppressed(Ljava/lang/Throwable;)V","getSuppressed()[Ljava/lang/Throwable;").contains(id.name()+id.descriptor())
            || id.owner().equals("java/lang/NullPointerException") && id.name().equals("getMessage") && id.descriptor().equals("()Ljava/lang/String;");
    }

}
