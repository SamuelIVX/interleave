/** JVM differential regressions for unusual but valid selected method and sealed-class metadata. */
package dev.samhb.interleave.bytecode;

import java.nio.file.*;
import java.lang.classfile.*;
import java.lang.constant.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static java.lang.classfile.ClassFile.*;
import static org.junit.jupiter.api.Assertions.*;
import static dev.samhb.interleave.bytecode.BytecodeState.*;

class BytecodeLinkageSelectionTest {
    /** Special selection skips static shadows and does not repeat resolution access checks.
     * @param directory compiler workspace
     * @throws Exception for compiler/oracle failures */
    @Test void specialSelectionUsesInstanceDeclarations(@TempDir Path directory) throws Exception {
        for(String variant:List.of("static","instance","package","inaccessible")) {
            var work=directory.resolve(variant);
            var classes=compile(work,Map.of(
                "a/A.java","package a;public class A{public int m(){return 1;}}",
                "b/B.java","package b;public class B extends a.A{public int m(){return 2;}}",
                "c/C.java","package c;public class C extends b.B{public int call(){return super.m();}}",
                "Fixture.java","import dev.samhb.interleave.bytecode.*;@Model public class Fixture{public int result;public Fixture(){}@Actor(id=0)public void actor(){result=new c.C().call();}}"));
            var parser=ClassFile.of();Path child=classes.resolve("c/C.class");
            Files.write(child,parser.transformClass(parser.parse(Files.readAllBytes(child)),ClassTransform.transformingMethodBodies(
                m->m.methodName().equalsString("call"),(code,item)->{
                    if(item instanceof java.lang.classfile.instruction.InvokeInstruction call)code.invokespecial(ClassDesc.of("a.A"),"m",call.typeSymbol());else code.with(item);
                })));
            if(!variant.equals("instance")) {
                Path target=classes.resolve(variant.equals("inaccessible") ? "a/A.class" : "b/B.class");
                Files.write(target,parser.transformClass(parser.parse(Files.readAllBytes(target)),(builder,item)->{
                    if(item instanceof MethodModel method && method.methodName().equalsString("m")) {
                        int flags=variant.equals("static") ? ACC_PUBLIC|ACC_STATIC : 0;
                        if(variant.equals("static"))builder.withMethod(method.methodName(),method.methodType(),flags,out->out.withCode(code->code.iconst_2().ireturn()));
                        else builder.transformMethod(method,MethodTransform.endHandler(out->out.withFlags(flags)));
                    }else builder.with(item);
                }));
            }
            String jvm=BytecodeFixtures.oracle(work,classes,"try{Fixture f=new Fixture();f.actor();System.out.print(f.result);}catch(LinkageError e){System.out.print(e.getClass().getName());}");
            if(variant.equals("inaccessible")) {
                assertEquals("java.lang.IllegalAccessError",jvm);
                assertEquals("LINKAGE",assertThrows(BytecodeLoadException.class,()->BytecodeLoader.load(classes,"Fixture",BytecodeLimits.defaults())).category);
            } else {
                int expected=variant.equals("static") ? 1 : 2;assertEquals(Integer.toString(expected),jvm);
                var program=BytecodeLoader.load(classes,"Fixture",BytecodeLimits.defaults());var state=BytecodeFixtures.run(program,program.initialState(),0);
                assertEquals(Phase.TERMINATED,state.executions.get(0).phase);assertEquals(new Int(expected),state.fixtureField("result"));
            }
        }
    }

    /** Invokevirtual may select a nonpublic override of a default; invokeinterface may not.
     * @param directory compiler workspace
     * @throws Exception for compiler/oracle failures */
    @Test void selectedVisibilityDependsOnOpcode(@TempDir Path directory) throws Exception {
        for(boolean interfaceView:new boolean[]{false,true}) {
            var work=directory.resolve(interfaceView ? "interface" : "class");
            var classes=BytecodeFixtures.compile(work,26,"interface I{default int m(){return 1;}}class Base implements I{}class Child extends Base{public int m(){return 2;}}@Model public class Fixture{public int result;public Fixture(){}@Actor(id=0)public void actor(){"+(interfaceView ? "I" : "Base")+" value=new Child();result=value.m();}}");
            var parser=ClassFile.of();Path target=classes.resolve("Child.class");
            Files.write(target,parser.transformClass(parser.parse(Files.readAllBytes(target)),(builder,item)->{
                if(item instanceof MethodModel method && method.methodName().equalsString("m"))builder.transformMethod(method,MethodTransform.endHandler(out->out.withFlags(0)));
                else builder.with(item);
            }));
            String jvm=BytecodeFixtures.oracle(work,classes,"try{Fixture f=new Fixture();f.actor();System.out.print(f.result);}catch(LinkageError e){System.out.print(e.getClass().getName());}");
            if(interfaceView) {
                assertEquals("java.lang.IllegalAccessError",jvm);
                assertEquals("LINKAGE",assertThrows(BytecodeLoadException.class,()->BytecodeLoader.load(classes,"Fixture",BytecodeLimits.defaults())).category);
            } else {
                assertEquals("2",jvm);var program=BytecodeLoader.load(classes,"Fixture",BytecodeLimits.defaults());
                assertEquals(new Int(2),BytecodeFixtures.run(program,program.initialState(),0).fixtureField("result"));
            }
        }
    }

    /** Public permitted children may cross packages in the same unnamed module; nonpublic children may not.
     * @param directory compiler workspace
     * @throws Exception for compiler/oracle failures */
    @Test void sealedPublicCrossPackageSubtype(@TempDir Path directory) throws Exception {
        var classes=compile(directory,Map.of("a/Parent.java","package a;public class Parent{public int m(){return 7;}}",
            "b/Child.java","package b;public final class Child extends a.Parent{}",
            "b/Fixture.java","package b;import dev.samhb.interleave.bytecode.*;@Model public class Fixture{public int result;public Fixture(){}@Actor(id=0)public void actor(){result=new Child().m();}}"));
        var parser=ClassFile.of();Path parent=classes.resolve("a/Parent.class"),child=classes.resolve("b/Child.class");
        Files.write(parent,parser.transformClass(parser.parse(Files.readAllBytes(parent)),ClassTransform.endHandler(out->out.with(java.lang.classfile.attribute.PermittedSubclassesAttribute.ofSymbols(ClassDesc.of("b.Child"))))));
        assertEquals("7",BytecodeFixtures.oracle(directory,classes,"b.Fixture f=new b.Fixture();f.actor();System.out.print(f.result);"));
        var program=BytecodeLoader.load(classes,"b.Fixture",BytecodeLimits.defaults());assertEquals(new Int(7),BytecodeFixtures.run(program,program.initialState(),0).fixtureField("result"));
        Files.write(child,parser.transformClass(parser.parse(Files.readAllBytes(child)),ClassTransform.endHandler(out->out.withFlags(ACC_FINAL|ACC_SUPER))));
        assertEquals("java.lang.IncompatibleClassChangeError",BytecodeFixtures.oracle(directory,classes,"try{new b.Fixture().actor();}catch(LinkageError e){System.out.print(e.getClass().getName());}"));
        assertEquals("LINKAGE",assertThrows(BytecodeLoadException.class,()->BytecodeLoader.load(classes,"b.Fixture",BytecodeLimits.defaults())).category);
    }

    /** Many repeated calls must not multiply whole-hierarchy validation cost.
     * @param directory compiler workspace
     * @throws Exception for fixture failures */
    @Test void repeatedCallsDoNotRepeatHierarchyValidation(@TempDir Path directory) throws Exception {
        var methods=new StringBuilder();for(int i=0;i<1500;i++)methods.append("public int m").append(i).append("(){return 0;}");
        var classes=BytecodeFixtures.compile(directory,26,"class Base{"+methods+"}class Child extends Base{"+methods.toString().replace("m","n")+"}@Model public class Fixture{Child item=new Child();public Fixture(){}static void helper(){}@Actor(id=0)public void actor(){"+"helper();".repeat(2000)+"}}");
        var program=assertTimeoutPreemptively(java.time.Duration.ofSeconds(20),()->BytecodeLoader.load(classes,"Fixture",BytecodeLimits.defaults()));
        assertEquals(3,program.classes.size());
    }

    /** Compiles named application files without executing the target classes.
     * @param directory isolated workspace
     * @param sources relative filenames and source texts
     * @return class directory
     * @throws Exception for compiler/filesystem failures */
    private static Path compile(Path directory,Map<String,String> sources) throws Exception {
        Path classes=directory.resolve("classes");Files.createDirectories(classes);
        var arguments=new ArrayList<String>(List.of("--release","26","-classpath",BytecodeFixtures.classpath(Model.class),"-d",classes.toString()));
        for(var entry:sources.entrySet()){Path source=directory.resolve("sources").resolve(entry.getKey());Files.createDirectories(source.getParent());Files.writeString(source,entry.getValue());arguments.add(source.toString());}
        assertEquals(0,javax.tools.ToolProvider.getSystemJavaCompiler().run(null,null,null,arguments.toArray(String[]::new)));return classes;
    }
}
