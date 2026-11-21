/** Closed hierarchy, dispatch alternatives, nestmates and fail-closed member linkage. */
package dev.samhb.interleave.bytecode;

import java.nio.file.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;
import static dev.samhb.interleave.bytecode.BytecodeState.*;

class BytecodeDispatchTest {
    /** Default/interface/abstract overrides, super calls and private nest access agree across compilers.
     * @param directory compiler workspace
     * @throws Exception for compiler/oracle failures */
    @Test void closedDispatchAndNestmatesMatchJvm(@TempDir Path directory) throws Exception {
        for(int release:new int[]{17,21,26}) {
            var work=directory.resolve("release"+release);
            var classes=BytecodeFixtures.compile(work,release,"""
                interface Read {default int read(){return 3;}}
                abstract class Base implements Read { abstract int value(); }
                class One extends Base {int value(){return read()+1;}}
                class Two extends Base {public int read(){return 7;}int value(){return read()+2;}}
                @Model public class Fixture {public int result;public Fixture(){}
                  private int secret(){return 5;}
                  static class Nested {private int own=6;private int read(Fixture f){return own+f.secret();}}
                  @Actor(id=0)public void actor(){Base a=new One();Read b=new Two();Nested n=new Nested();result=a.value()+b.read()+n.read(this);}
                }
                """);
            var program=BytecodeLoader.load(classes,"Fixture",BytecodeLimits.defaults());
            var result=BytecodeFixtures.run(program,program.initialState(),0);
            assertEquals(Phase.TERMINATED,result.executions.get(0).phase);assertEquals(new Int(22),result.fixtureField("result"));
            assertEquals("22",BytecodeFixtures.oracle(work,classes,"Fixture f=new Fixture();f.actor();System.out.print(f.result);"));
        }
    }

    /** Later reachable construction cannot conceal an unsupported override or impure check alternative.
     * @param directory compiler workspace
     * @throws Exception for fixture failures */
    @Test void validatesEveryDispatchAlternative(@TempDir Path directory) throws Exception {
        for(String bad:new String[]{"return (int)System.nanoTime();","value++;return value;"}) {
            boolean observer=bad.startsWith("value");
            var classes=BytecodeFixtures.compile(directory.resolve(observer ? "observer" : "actor"),26,
                "interface Read{int read();} class Good implements Read{public int read(){return 1;}}"
                +"class Bad implements Read{int value;public int read(){"+bad+"}}"
                +"@Model public class Fixture{Read item=new Good();public Fixture(){}"
                +"@Actor(id=0)public void actor(){"+(observer ? "Read later=new Bad();" : "item.read();Read later=new Bad();")+"}"
                +(observer ? "@Check public boolean check(){return item.read()==1;}" : "")+"}");
            var error=assertThrows(BytecodeLoadException.class,()->BytecodeLoader.load(classes,"Fixture",BytecodeLimits.defaults()));
            assertTrue(error.category.equals(observer ? "PURITY" : "UNSUPPORTED"),error.getMessage());
        }
    }

    /** An absent closed-hierarchy class must reject without target reflection or search-time resolution.
     * @param directory compiler workspace
     * @throws Exception for fixture failures */
    @Test void missingDispatchMetadataRejectsBeforeSearch(@TempDir Path directory) throws Exception {
        var classes=BytecodeFixtures.compile(directory,26,"""
            interface Read{int read();}class Impl implements Read{public int read(){return 1;}}
            @Model public class Fixture{public Fixture(){} @Actor(id=0)public void actor(){Read r=new Impl();r.read();}}
            """);
        Files.delete(classes.resolve("Read.class"));
        assertEquals("METADATA",assertThrows(BytecodeLoadException.class,()->BytecodeLoader.load(classes,"Fixture",BytecodeLimits.defaults())).category);
    }
    /** Cross-package protected access validates the receiver type, beyond ordinary member visibility.
     * @param directory compiler workspace
     * @throws Exception for fixture/oracle failures */
    @Test void protectedReceiversAndInheritedPublicMembers(@TempDir Path directory) throws Exception {
        for(boolean illegal:new boolean[]{false,true}) {
            Path work=directory.resolve(illegal ? "invalid" : "valid"),sources=work.resolve("sources"),classes=work.resolve("classes");
            Files.createDirectories(sources.resolve("a"));Files.createDirectories(sources.resolve("b"));Files.createDirectories(classes);
            Path base=sources.resolve("a/Box.java"),child=sources.resolve("b/Child.java"),fixture=sources.resolve("b/Fixture.java");
            Files.writeString(base,"package a;public class Box{protected int secret=7;public int exposed=3;public Box(){}}");
            Files.writeString(child,"package b;public class Child extends a.Box{public static int read("+(illegal ? "a.Box" : "Child")+" c){return c."+(illegal ? "exposed" : "secret")+";}}");
            Files.writeString(fixture,"package b;import dev.samhb.interleave.bytecode.*;@Model public class Fixture extends Child{public int result;public Fixture(){}@Actor(id=0)public void actor(){result=read(this);}}");
            assertEquals(0,javax.tools.ToolProvider.getSystemJavaCompiler().run(null,null,null,"--release","26","-classpath",BytecodeFixtures.classpath(Model.class),"-d",classes.toString(),base.toString(),child.toString(),fixture.toString()));
            if(illegal) {
                var parser=java.lang.classfile.ClassFile.of();Path target=classes.resolve("b/Child.class");
                Files.write(target,parser.transformClass(parser.parse(Files.readAllBytes(target)),(builder,element)->{
                    if(element instanceof java.lang.classfile.MethodModel method && method.methodName().equalsString("read"))
                        builder.transformMethod(method,java.lang.classfile.MethodTransform.transformingCode((code,item)->{
                            if(item instanceof java.lang.classfile.instruction.FieldInstruction field)code.getfield(field.owner().asSymbol(),"secret",java.lang.constant.ConstantDescs.CD_int);
                            else code.with(item);
                        }));else builder.with(element);
                }));
                assertEquals("LINKAGE",assertThrows(BytecodeLoadException.class,()->BytecodeLoader.load(classes,"b.Fixture",BytecodeLimits.defaults())).category);
            } else {
                var program=BytecodeLoader.load(classes,"b.Fixture",BytecodeLimits.defaults());
                assertEquals(new Int(7),BytecodeFixtures.run(program,program.initialState(),0).fixtureField("result"));
                assertEquals("7",BytecodeFixtures.oracle(work,classes,"b.Fixture f=new b.Fixture();f.actor();System.out.print(f.result);"));
            }
        }
    }

    /** Symbolic type operations cannot bypass class visibility, even when the runtime value is null.
     * @param directory compiler workspace
     * @throws Exception for fixture failures */
    @Test void inaccessibleArrayAndCastTypesRejectBeforeSearch(@TempDir Path directory) throws Exception {
        var classes=BytecodeFixtures.compile(directory,26,"""
            class Hidden{}@Model public class Fixture{Object saved;public Fixture(){}@Actor(id=0)public void actor(){saved=new Hidden[1];}}
            """);
        var parser=java.lang.classfile.ClassFile.of();Path hidden=classes.resolve("Hidden.class"),moved=classes.resolve("other/Hidden.class");
        Files.createDirectories(moved.getParent());
        Files.write(moved,parser.transformClass(parser.parse(Files.readAllBytes(hidden)),java.lang.constant.ClassDesc.of("other.Hidden"),java.lang.classfile.ClassTransform.ACCEPT_ALL));
        Path fixture=classes.resolve("Fixture.class");byte[] original=Files.readAllBytes(fixture);
        for(boolean cast:new boolean[]{false,true}) {
        Files.write(fixture,parser.transformClass(parser.parse(original),(builder,element)->{
            if(element instanceof java.lang.classfile.MethodModel method && method.methodName().equalsString("actor"))
                builder.transformMethod(method,java.lang.classfile.MethodTransform.transformingCode((code,item)->{
                    if(item instanceof java.lang.classfile.instruction.NewReferenceArrayInstruction) {
                        if(cast)code.pop().aconst_null().checkcast(java.lang.constant.ClassDesc.of("other.Hidden"));
                        else code.anewarray(java.lang.constant.ClassDesc.of("other.Hidden"));
                    }
                    else code.with(item);
                }));else builder.with(element);
        }));
        assertEquals("LINKAGE",assertThrows(BytecodeLoadException.class,()->BytecodeLoader.load(classes,"Fixture",BytecodeLimits.defaults())).category);
        }
    }

    /** Trusted Object final methods cannot be overridden by forged application metadata.
     * @param directory compiler workspace
     * @throws Exception for fixture failures */
    @Test void finalObjectMembersRejectBeforeExecution(@TempDir Path directory) throws Exception {
        var classes=BytecodeFixtures.compile(directory,26,"@Model public class Fixture{public Fixture(){}public void helper(){}@Actor(id=0)public void actor(){notify();}}");
        var parser=java.lang.classfile.ClassFile.of();Path target=classes.resolve("Fixture.class");
        Files.write(target,parser.transformClass(parser.parse(Files.readAllBytes(target)),(builder,element)->{
            if(element instanceof java.lang.classfile.MethodModel method && method.methodName().equalsString("helper"))
                builder.withMethodBody("notify",java.lang.constant.MethodTypeDesc.of(java.lang.constant.ConstantDescs.CD_void),java.lang.classfile.ClassFile.ACC_PUBLIC,code->code.return_());
            else builder.with(element);
        }));
        assertEquals("LINKAGE",assertThrows(BytecodeLoadException.class,()->BytecodeLoader.load(classes,"Fixture",BytecodeLimits.defaults())).category);
    }

    /** Static interface members are not inherited by implementing classes.
     * @param directory compiler workspace
     * @throws Exception for fixture failures */
    @Test void interfaceStaticMemberDoesNotConflictWithInstanceMethod(@TempDir Path directory) throws Exception {
        var classes=BytecodeFixtures.compile(directory,26,"""
            interface I{static int value(){return 1;}}class C implements I{public int value(){return 2;}}
            @Model public class Fixture{public int result;public Fixture(){}@Actor(id=0)public void actor(){result=new C().value();}}
            """);
        var program=BytecodeLoader.load(classes,"Fixture",BytecodeLimits.defaults());
        assertEquals(new Int(2),BytecodeFixtures.run(program,program.initialState(),0).fixtureField("result"));
        assertEquals("2",BytecodeFixtures.oracle(directory,classes,"Fixture f=new Fixture();f.actor();System.out.print(f.result);"));
    }

    /** CHECK validation is role-specific even if the mutating override was already admitted for an actor.
     * @param directory compiler workspace
     * @throws Exception for fixture failures */
    @Test void actorAdmissionCannotBypassObserverDispatchPurity(@TempDir Path directory) throws Exception {
        var classes=BytecodeFixtures.compile(directory,26,"""
            class Base{boolean test(){return true;}}class Sub extends Base{int x;boolean test(){x++;return true;}}
            @Model public class Fixture{Base b=new Sub();public Fixture(){}@Actor(id=0)public void actor(){b.test();}
              @Check public boolean valid(){return b.test();}}
            """);
        assertEquals("PURITY",assertThrows(BytecodeLoadException.class,()->BytecodeLoader.load(classes,"Fixture",BytecodeLimits.defaults())).category);
    }

    /** Cross-package nonpublic superclasses/interfaces reject, with public controls retained.
     * @param directory compiler workspace
     * @throws Exception for fixture failures */
    @Test void directSupertypesMustBeAccessible(@TempDir Path directory) throws Exception {
        for(boolean face:new boolean[]{false,true}) {
            var work=directory.resolve(face ? "interface" : "superclass");Files.createDirectories(work.resolve("sources/a"));Files.createDirectories(work.resolve("sources/b"));
            var parent=work.resolve("sources/a/Parent.java");var child=work.resolve("sources/b/Fixture.java");var classes=work.resolve("classes");Files.createDirectories(classes);
            Files.writeString(parent,face ? "package a;public interface Parent{}" : "package a;public class Parent{public Parent(){}}");
            Files.writeString(child,"package b;import dev.samhb.interleave.bytecode.*;@Model public class Fixture "+(face ? "implements" : "extends")+" a.Parent{public Fixture(){}@Actor(id=0)public void actor(){}}");
            assertEquals(0,javax.tools.ToolProvider.getSystemJavaCompiler().run(null,null,null,"--release","26","-classpath",BytecodeFixtures.classpath(Model.class),"-d",classes.toString(),parent.toString(),child.toString()));
            assertNotNull(BytecodeLoader.load(classes,"b.Fixture",BytecodeLimits.defaults()));
            var parser=java.lang.classfile.ClassFile.of();var path=classes.resolve("a/Parent.class");var type=parser.parse(Files.readAllBytes(path));
            Files.write(path,parser.transformClass(type,java.lang.classfile.ClassTransform.endHandler(builder->builder.withFlags(type.flags().flagsMask() & ~java.lang.classfile.ClassFile.ACC_PUBLIC))));
            assertEquals("LINKAGE",assertThrows(BytecodeLoadException.class,()->BytecodeLoader.load(classes,"b.Fixture",BytecodeLimits.defaults())).category);
        }
    }

    /** Repeated false override relationships have bounded work and cannot redirect a package-private base call.
     * @param directory compiler workspace
     * @throws Exception for fixture failures */
    @Test void deepPackagePrivateOverrideSelectionIsBounded(@TempDir Path directory) throws Exception {
        var sources=directory.resolve("sources");var classes=directory.resolve("classes");Files.createDirectories(sources.resolve("a"));Files.createDirectories(sources.resolve("b"));Files.createDirectories(classes);
        var arguments=new java.util.ArrayList<String>(java.util.List.of("--release","26","-classpath",BytecodeFixtures.classpath(Model.class),"-d",classes.toString()));
        var base=sources.resolve("a/Base.java");Files.writeString(base,"package a;public class Base{public Base(){}int value(){return 1;}public int read(){return value();}}");arguments.add(base.toString());
        for(int depth=1;depth<=30;depth++) {
            var source=sources.resolve("b/C"+depth+".java");
            Files.writeString(source,"package b;public class C"+depth+" extends "+(depth==1 ? "a.Base" : "C"+(depth-1))+"{public int value(){return 2;}}");arguments.add(source.toString());
        }
        var source=sources.resolve("Fixture.java");Files.writeString(source,"import dev.samhb.interleave.bytecode.*;@Model public class Fixture{public int result;public Fixture(){}@Actor(id=0)public void actor(){a.Base b=new b.C30();result=b.read();}}");arguments.add(source.toString());
        assertEquals(0,javax.tools.ToolProvider.getSystemJavaCompiler().run(null,null,null,arguments.toArray(String[]::new)));
        var program=assertTimeoutPreemptively(java.time.Duration.ofSeconds(20),()->BytecodeLoader.load(classes,"Fixture",BytecodeLimits.defaults()));
        var state=BytecodeFixtures.run(program,program.initialState(),0);assertEquals(Phase.TERMINATED,state.executions.get(0).phase);assertEquals(new Int(1),state.fixtureField("result"));
        assertEquals("1",BytecodeFixtures.oracle(directory,classes,"Fixture f=new Fixture();f.actor();System.out.print(f.result);"));
        var middle=sources.resolve("a/Middle.java");Files.writeString(middle,"package a;public class Middle extends Base{public int value(){return 3;}}");arguments.add(middle.toString());
        Files.writeString(sources.resolve("b/C1.java"),"package b;public class C1 extends a.Middle{public int value(){return 2;}}");
        assertEquals(0,javax.tools.ToolProvider.getSystemJavaCompiler().run(null,null,null,arguments.toArray(String[]::new)));
        var limits=new BytecodeLimits(256,128,8192,64,64,256,4096,128,8192,1024);
        var widened=assertTimeoutPreemptively(java.time.Duration.ofSeconds(20),()->BytecodeLoader.load(classes,"Fixture",limits));
        var widenedState=BytecodeFixtures.run(widened,widened.initialState(),0);assertEquals(Phase.TERMINATED,widenedState.executions.get(0).phase);
        assertEquals(new Int(2),widenedState.fixtureField("result"));
        assertEquals("2",BytecodeFixtures.oracle(directory,classes,"Fixture f=new Fixture();f.actor();System.out.print(f.result);"));
    }

    /** JVM-verified interface bytecode still performs a dynamic receiver check; no host failure may escape.
     * @param directory compiler workspace
     * @throws Exception for fixture/oracle failures */
    @Test void incompatibleInterfaceReceiverIsModeled(@TempDir Path directory) throws Exception {
        var classes=BytecodeFixtures.compile(directory,26,"interface I{void read();}@Model public class Fixture{public Fixture(){}@Actor(id=0)public void actor(){I value=null;value.read();}}");
        var parser=java.lang.classfile.ClassFile.of();Path target=classes.resolve("Fixture.class");
        Files.write(target,parser.transformClass(parser.parse(Files.readAllBytes(target)),(builder,element)->{
            if(element instanceof java.lang.classfile.MethodModel method && method.methodName().equalsString("actor"))
                builder.transformMethod(method,java.lang.classfile.MethodTransform.transformingCode((code,item)->{
                    if(item instanceof java.lang.classfile.Instruction instruction && instruction.opcode()==java.lang.classfile.Opcode.ACONST_NULL)
                        code.new_(java.lang.constant.ConstantDescs.CD_Object).dup().invokespecial(java.lang.constant.ConstantDescs.CD_Object,"<init>",java.lang.constant.MethodTypeDesc.of(java.lang.constant.ConstantDescs.CD_void));
                    else code.with(item);
                }));else builder.with(element);
        }));
        var program=BytecodeLoader.load(classes,"Fixture",BytecodeLimits.defaults());
        var state=BytecodeFixtures.run(program,program.initialState(),0);
        assertEquals(Phase.FAILED,state.executions.get(0).phase);assertEquals("java/lang/IncompatibleClassChangeError",state.executions.get(0).fault.type());
        assertEquals("java.lang.IncompatibleClassChangeError",BytecodeFixtures.oracle(directory,classes,"try{new Fixture().actor();}catch(LinkageError e){System.out.print(e.getClass().getName());}"));
    }

    /** Private base declarations cannot capture interface defaults; inherited public implementations win.
     * @param directory compiler workspace
     * @throws Exception for fixture/oracle failures */
    @Test void interfaceDefaultsIgnorePrivateBaseMethods(@TempDir Path directory) throws Exception {
        for(boolean privateMember:new boolean[]{true,false})for(boolean interfaceView:new boolean[]{true,false}) {
            var work=directory.resolve((privateMember ? "private" : "public")+(interfaceView ? "-interface" : "-class"));
            var classes=BytecodeFixtures.compile(work,26,"interface I{default int read(){return 1;}}class Base{"+(privateMember ? "private" : "public")
                +" int read(){return 2;}}class Child extends Base implements I{}@Model public class Fixture{public int result;public Fixture(){}@Actor(id=0)public void actor(){"+(interfaceView ? "I" : "Child")+" value=new Child();result=value.read();}}");
            String jvm=BytecodeFixtures.oracle(work,classes,"try{Fixture f=new Fixture();f.actor();System.out.print(f.result);}catch(LinkageError e){System.out.print(e.getClass().getName());}");
            if(privateMember && !interfaceView) {
                assertEquals("java.lang.IllegalAccessError",jvm);
                assertEquals("LINKAGE",assertThrows(BytecodeLoadException.class,()->BytecodeLoader.load(classes,"Fixture",BytecodeLimits.defaults())).category);
            } else {
                assertEquals(privateMember ? "1" : "2",jvm);
                var program=BytecodeLoader.load(classes,"Fixture",BytecodeLimits.defaults());var state=BytecodeFixtures.run(program,program.initialState(),0);
                assertEquals(new Int(privateMember ? 1 : 2),state.fixtureField("result"));
            }
        }
    }

    /** Sealed hierarchy admission checks permitted edges without linking target classes on the host.
     * @param directory compiler workspace
     * @throws Exception for fixture/oracle failures */
    @Test void sealedParentsValidatePermittedSubtypes(@TempDir Path directory) throws Exception {
        var classes=BytecodeFixtures.compile(directory,17,"""
            sealed class Parent permits Allowed,Other{int read(){return 1;}}final class Allowed extends Parent{}final class Other extends Parent{}
            @Model public class Fixture{public int result;public Fixture(){}@Actor(id=0)public void actor(){Parent p=new Allowed();result=p.read();}}
            """);
        var program=BytecodeLoader.load(classes,"Fixture",BytecodeLimits.defaults());assertEquals(new Int(1),BytecodeFixtures.run(program,program.initialState(),0).fixtureField("result"));
        assertEquals("1",BytecodeFixtures.oracle(directory,classes,"Fixture f=new Fixture();f.actor();System.out.print(f.result);"));
        var parser=java.lang.classfile.ClassFile.of();Path target=classes.resolve("Parent.class");
        Files.write(target,parser.transformClass(parser.parse(Files.readAllBytes(target)),(builder,element)->{
            if(element instanceof java.lang.classfile.attribute.PermittedSubclassesAttribute)
                builder.with(java.lang.classfile.attribute.PermittedSubclassesAttribute.ofSymbols(java.lang.constant.ClassDesc.of("Other")));
            else builder.with(element);
        }));
        assertEquals("LINKAGE",assertThrows(BytecodeLoadException.class,()->BytecodeLoader.load(classes,"Fixture",BytecodeLimits.defaults())).category);
        assertEquals("java.lang.IncompatibleClassChangeError",BytecodeFixtures.oracle(directory,classes,
            "try{new Fixture().actor();}catch(LinkageError e){System.out.print(e.getClass().getName());}"));
    }

    /** A widened ancestor remains relevant after a later narrower raw declaration, as required by JVMS overriding.
     * @param directory compiler workspace
     * @throws Exception for fixture/oracle failures */
    @Test void transitiveOverrideRetainsEarlierWidening(@TempDir Path directory) throws Exception {
        var sources=directory.resolve("sources");var classes=directory.resolve("classes");Files.createDirectories(sources.resolve("p"));Files.createDirectories(sources.resolve("q"));Files.createDirectories(classes);
        var files=java.util.Map.of(
            "p/A.java","package p;public class A{int m(){return 1;}public int call(){return m();}}",
            "p/B.java","package p;public class B extends A{public int m(){return 2;}}",
            "p/C.java","package p;public class C extends B{public int m(){return 3;}}",
            "q/D.java","package q;public class D extends p.C{public int m(){return 4;}}",
            "Fixture.java","import dev.samhb.interleave.bytecode.*;@Model public class Fixture{public int result;public Fixture(){}@Actor(id=0)public void actor(){p.A a=new q.D();result=a.call();}}");
        var arguments=new java.util.ArrayList<String>(java.util.List.of("--release","26","-classpath",BytecodeFixtures.classpath(Model.class),"-d",classes.toString()));
        for(var entry:files.entrySet()){var source=sources.resolve(entry.getKey());Files.writeString(source,entry.getValue());arguments.add(source.toString());}
        assertEquals(0,javax.tools.ToolProvider.getSystemJavaCompiler().run(null,null,null,arguments.toArray(String[]::new)));
        var parser=java.lang.classfile.ClassFile.of();
        for(String name:java.util.List.of("p/C","q/D")) {
            Path target=classes.resolve(name+".class");
            Files.write(target,parser.transformClass(parser.parse(Files.readAllBytes(target)),(builder,element)->{
                if(element instanceof java.lang.classfile.MethodModel method && method.methodName().equalsString("m"))
                    builder.transformMethod(method,java.lang.classfile.MethodTransform.endHandler(output->output.withFlags(0)));
                else builder.with(element);
            }));
        }
        assertEquals("4",BytecodeFixtures.oracle(directory,classes,"Fixture f=new Fixture();f.actor();System.out.print(f.result);"));
        var program=BytecodeLoader.load(classes,"Fixture",BytecodeLimits.defaults());
        assertEquals(new Int(4),BytecodeFixtures.run(program,program.initialState(),0).fixtureField("result"));
    }

    /** Private invocations retain their resolved base declaration instead of selecting a child shadow.
     * @param directory compiler workspace
     * @throws Exception for fixture/oracle failures */
    @Test void privateResolvedMethodCannotBeOverridden(@TempDir Path directory) throws Exception {
        var classes=BytecodeFixtures.compile(directory,26,"class Base{private int read(){return 1;}int call(){return read();}}class Child extends Base{int read(){return 2;}}@Model public class Fixture{public int result;public Fixture(){}@Actor(id=0)public void actor(){Base b=new Child();result=b.call();}}");
        var program=BytecodeLoader.load(classes,"Fixture",BytecodeLimits.defaults());
        assertEquals(new Int(1),BytecodeFixtures.run(program,program.initialState(),0).fixtureField("result"));
        assertEquals("1",BytecodeFixtures.oracle(directory,classes,"Fixture f=new Fixture();f.actor();System.out.print(f.result);"));
    }

    /** Cyclic interface metadata terminates in admission instead of recursively expanding ancestors.
     * @param directory compiler workspace
     * @throws Exception for fixture failures */
    @Test void cyclicInterfacesRejectBeforeSearch(@TempDir Path directory) throws Exception {
        var classes=BytecodeFixtures.compile(directory,26,"interface I{}interface J extends I{}@Model public class Fixture implements J{public Fixture(){}@Actor(id=0)public void actor(){}}");
        var parser=java.lang.classfile.ClassFile.of();Path target=classes.resolve("I.class");
        Files.write(target,parser.transformClass(parser.parse(Files.readAllBytes(target)),java.lang.classfile.ClassTransform.endHandler(builder->builder.withInterfaceSymbols(java.lang.constant.ClassDesc.of("J")))));
        assertEquals("LINKAGE",assertThrows(BytecodeLoadException.class,()->BytecodeLoader.load(classes,"Fixture",BytecodeLimits.defaults())).category);
    }

    /** Static and instance references through a model or its base share the declaration's real cell.
     * @param directory compiler workspace
     * @throws Exception for fixture/oracle failures */
    @Test void inheritedSymbolicOwnersResolveOneFieldCell(@TempDir Path directory) throws Exception {
        var classes=BytecodeFixtures.compile(directory,26,"""
            class Base{static int n;int v;}@Model public class Fixture extends Base{public Fixture(){}
              @Actor(id=0)public void actor(){Fixture.n=1;Base.n++;Fixture f=this;f.v=3;((Base)f).v++;}
              @Check public boolean valid(){return Base.n==2 && Fixture.n==2 && v==4;}}
            """);
        var program=BytecodeLoader.load(classes,"Fixture",BytecodeLimits.defaults());var state=BytecodeFixtures.run(program,program.initialState(),0);
        assertEquals(new Int(2),state.statics.get(new BytecodeProgram.FieldId("Base","n","I")));assertEquals(1,state.statics.size());
        assertEquals(new Int(4),state.fixtureField("v"));assertEquals("2:4",BytecodeFixtures.oracle(directory,classes,"Fixture f=new Fixture();f.actor();System.out.print(Fixture.n+\":\"+((Base)f).v);"));
        var result=new BytecodeExplorer(program,BytecodeScheduler.Mode.BATCHED).explore(10000);assertTrue(result.exhaustive());
        assertEquals(java.util.Set.of(BytecodeExplorer.Kind.COMPLETED),result.witnesses().stream().map(w->w.outcome().kind()).collect(java.util.stream.Collectors.toSet()));
    }

    /** Both production granularities retain the concrete array lost-update violation and successful result.
     * @param directory compiler workspace
     * @throws Exception for fixture failures */
    @Test void arrayIncrementChecksRetainSeparateReadWriteChoices(@TempDir Path directory) throws Exception {
        var classes=BytecodeFixtures.compile(directory,26,"@Model public class Fixture{int[] x=new int[1];public Fixture(){}@Actor(id=0)public void a(){x[0]++;}@Actor(id=1)public void b(){x[0]++;}@Check public boolean valid(){return x[0]==2;}}");
        var program=BytecodeLoader.load(classes,"Fixture",BytecodeLimits.defaults());var expected=BytecodeInstructionOracle.enumerate(program);
        for(var mode:BytecodeScheduler.Mode.values()) {
            var result=new BytecodeExplorer(program,mode).explore(100000);assertTrue(result.exhaustive());
            assertEquals(java.util.Set.of(BytecodeExplorer.Kind.COMPLETED,BytecodeExplorer.Kind.CHECK_FALSE),result.witnesses().stream().map(w->w.outcome().kind()).collect(java.util.stream.Collectors.toSet()));
            assertEquals(expected,BytecodeBatchingOracleTest.independentProjection(result));
        }
    }

}
