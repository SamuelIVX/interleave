/** Adversarial input, annotation, link and purity cases through the complete internal load seam. */
package dev.samhb.interleave.bytecode;

import java.nio.file.*;
import java.lang.classfile.*;
import java.lang.classfile.attribute.*;
import java.lang.classfile.instruction.*;
import java.lang.constant.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

class BytecodeAdmissionTest {
    /** Unsupported execution cannot slip through alternate branches, helpers or annotations.
     *
     * @param directory compiler workspace
     * @throws Exception for fixture failures */
    @Test void rejectsUnsupportedReachableProfiles(@TempDir Path directory) throws Exception {
        List<String> bodies=List.of(
            "@Actor(id=0) public void actor() { Object x=new Object(); }",
            "@Actor(id=0) public void actor() { int[] x=new int[1]; }",
            "@Actor(id=0) public void actor() { if(value==0) System.nanoTime(); }",
            "@Actor(id=0) public void actor() { try { value=1/value; } catch(ArithmeticException e) { value=1; } }",
            "@Actor(id=0) public void actor() { try { value=1/value; } finally { value++; } }",
            "@Actor(id=0) public void actor() { throw new RuntimeException(); }",
            "@Actor(id=0) public void actor() { helper(); } static void helper() { System.nanoTime(); }",
            "@Actor(id=0) public void actor() {} @Check public boolean check() { return ++value==0; }",
            "@Actor(id=0) public void actor() {} @Check public boolean check() { helper(); return true; } static void helper() { value=2; }",
            "@Actor(id=0) public void actor() {} @Check public synchronized boolean check() { return true; }",
            "@Actor(id=0) public void actor() {} @Check public boolean check(){return helper();} static synchronized boolean helper(){return true;}",
            "@Actor(id=0) public void actor() { double x=value; value=(int)x; }"
        );
        for(int i=0;i<bodies.size();i++) {
            Path classes=BytecodeFixtures.compile(directory.resolve("case"+i),26,
                "@Model public class Fixture { static int value; public Fixture() {} "+bodies.get(i)+" }");
            var error=assertThrows(BytecodeLoadException.class,()->BytecodeLoader.load(classes,"Fixture",BytecodeLimits.defaults()),"case"+i);
            assertTrue(Set.of("UNSUPPORTED","PURITY").contains(error.category),error.getMessage());
        }
    }

    /** Dense actors and exact annotation shapes are validated independently of target method bodies.
     *
     * @param directory compiler workspace
     * @throws Exception for fixture failures */
    @Test void rejectsWrongActorAndCheckDeclarations(@TempDir Path directory) throws Exception {
        List<String> bodies=List.of("public void actor() {}", "@Actor(id=1) public void actor() {}",
            "@Actor(id=-1) public void actor() {}", "@Actor(id=8) public void actor() {}",
            "@Actor(id=0) public static void actor() {}", "@Actor(id=0) void actor() {}",
            "@Actor(id=0) public int actor() {return 0;}", "@Actor(id=0) public void actor(int x) {}",
            "@Actor(id=0) @Check public void actor() {}", "@Actor(id=0) public void actor() {} @Actor(id=0) public void other() {}",
            "@Actor(id=0) public void actor() {} @Check public static boolean check() {return true;}",
            "@Actor(id=0) public void actor() {} @Check public boolean check(int x) {return true;}");
        for(int i=0;i<bodies.size();i++) {
            Path classes=BytecodeFixtures.compile(directory.resolve("case"+i),17,"@Model public class Fixture { public Fixture() {} "+bodies.get(i)+" }");
            assertEquals("DECLARATION",assertThrows(BytecodeLoadException.class,
                ()->BytecodeLoader.load(classes,"Fixture",BytecodeLimits.defaults())).category);
        }
        Path missing=BytecodeFixtures.compile(directory.resolve("noModel"),26,"public class Fixture { public Fixture() {} @Actor(id=0) public void actor() {} }");
        assertEquals("DECLARATION",assertThrows(BytecodeLoadException.class,()->BytecodeLoader.load(missing,"Fixture",BytecodeLimits.defaults())).category);
    }

    /** Application class initialization is never delegated to the host, even for a tempting side effect.
     *
     * @param directory compiler workspace
     * @throws Exception for fixture failures */
    @Test void setupNeverExecutesOnHost(@TempDir Path directory) throws Exception {
        String property="interleave94A.target.initializer"; System.clearProperty(property);
        Path classes=BytecodeFixtures.compile(directory,26,"""
            @Model public class Fixture {
              static { System.setProperty("interleave94A.target.initializer","executed"); }
              public Fixture() {} @Actor(id=0) public void actor() {}
            }
            """);
        assertEquals("UNSUPPORTED",assertThrows(BytecodeLoadException.class,()->BytecodeLoader.load(classes,"Fixture",BytecodeLimits.defaults())).category);
        assertNull(System.getProperty(property));
    }

    /** Missing hierarchy metadata, broken staticness and invalid stacks fail before setup.
     *
     * @param directory compiler workspace
     * @throws Exception for fixture failures */
    @Test void verifiesTypesAndMembersWithoutHostLinking(@TempDir Path directory) throws Exception {
        Path classes=BytecodeFixtures.compile(directory,26,"@Model public class Fixture { public int value; public Fixture() {} @Actor(id=0) public void actor() {value++;} }");
        byte[] original=Files.readAllBytes(classes.resolve("Fixture.class"));
        var parser=ClassFile.of();
        byte[] broken=parser.transformClass(parser.parse(original),(builder,element)-> {
            if(element instanceof MethodModel method && method.methodName().equalsString("actor"))
                builder.transformMethod(method,MethodTransform.transformingCode((code,item)-> {
                    if(item instanceof FieldInstruction field && field.opcode()==Opcode.GETFIELD)
                        code.getstatic(field.field());
                    else code.with(item);
                }));
            else builder.with(element);
        });
        Files.write(classes.resolve("Fixture.class"),broken);
        assertEquals("LINKAGE",assertThrows(BytecodeLoadException.class,()->BytecodeLoader.load(classes,"Fixture",BytecodeLimits.defaults())).category);
        // Same-size raw corruption avoids the builder repairing/generating stack maps for us.
        var model=parser.parse(original); var actor=model.methods().stream().filter(m->m.methodName().equalsString("actor")).findFirst().orElseThrow();
        byte[] code=((CodeAttribute)actor.code().orElseThrow()).codeArray();
        byte[] bad=original.clone(); int offset=find(bad,code);
        assertTrue(offset>=0); bad[offset]=(byte)Opcode.ICONST_0.bytecode();
        Files.write(classes.resolve("Fixture.class"),bad);
        assertEquals("MALFORMED",assertThrows(BytecodeLoadException.class,()->BytecodeLoader.load(classes,"Fixture",BytecodeLimits.defaults())).category);
        Files.write(classes.resolve("Fixture.class"),original);
        byte[] missing=parser.transformClass(parser.parse(original),(builder,element)-> {
            if(element instanceof java.lang.classfile.Superclass) builder.withSuperclass(ClassDesc.of("Absent")); else builder.with(element);
        });
        Files.write(classes.resolve("Fixture.class"),missing);
        assertThrows(BytecodeLoadException.class,()->BytecodeLoader.load(classes,"Fixture",BytecodeLimits.defaults()));
    }

    /** No path aliases, class-name swaps, unsupported versions or preview input can escape admission.
     *
     * @param directory compiler workspace
     * @throws Exception for fixture failures */
    @Test void validatesNamesPathsAndClassVersions(@TempDir Path directory) throws Exception {
        Path classes=BytecodeFixtures.compile(directory,26,"@Model public class Fixture { public Fixture() {} @Actor(id=0) public void actor() {} }");
        byte[] original=Files.readAllBytes(classes.resolve("Fixture.class"));
        for(int major : new int[]{60,71}) {
            byte[] bad=original.clone(); bad[6]=(byte)(major>>>8); bad[7]=(byte)major; Files.write(classes.resolve("Fixture.class"),bad);
            assertEquals("UNSUPPORTED",assertThrows(BytecodeLoadException.class,()->BytecodeLoader.load(classes,"Fixture",BytecodeLimits.defaults())).category);
        }
        byte[] preview=original.clone(); preview[4]=(byte)255; preview[5]=(byte)255; Files.write(classes.resolve("Fixture.class"),preview);
        assertEquals("UNSUPPORTED",assertThrows(BytecodeLoadException.class,()->BytecodeLoader.load(classes,"Fixture",BytecodeLimits.defaults())).category);
        Files.write(classes.resolve("Fixture.class"),original);
        for(String invalid : List.of("../Fixture","Fixture/Other","Fixture\\Other",".Fixture","Fixture.","Fixture..X","[LFixture;"))
            assertEquals("INPUT",assertThrows(BytecodeLoadException.class,()->BytecodeLoader.load(classes,invalid,BytecodeLimits.defaults())).category);
        Files.write(classes.resolve("Other.class"),original);
        assertEquals("MALFORMED",assertThrows(BytecodeLoadException.class,()->BytecodeLoader.load(classes,"Other",BytecodeLimits.defaults())).category);
        Path links=directory.resolve("links"); Files.createDirectories(links);
        Files.createSymbolicLink(links.resolve("Fixture.class"),classes.resolve("Fixture.class"));
        assertThrows(BytecodeLoadException.class,()->BytecodeLoader.load(links,"Fixture",BytecodeLimits.defaults()));
        Path nested=classes.resolve("nested"); Files.createDirectories(nested);
        var parser=ClassFile.of();Files.write(nested.resolve("Fixture.class"),
            parser.transformClass(parser.parse(original),ClassDesc.of("nested.Fixture"),ClassTransform.ACCEPT_ALL));
        assertEquals("nested/Fixture",BytecodeLoader.load(classes,"nested.Fixture",BytecodeLimits.defaults()).model);
        Files.createSymbolicLink(links.resolve("nested"),nested);
        assertThrows(BytecodeLoadException.class,()->BytecodeLoader.load(links,"nested.Fixture",BytecodeLimits.defaults()));
        assertThrows(BytecodeLoadException.class,()->BytecodeLoader.load(null,"Fixture",BytecodeLimits.defaults()));
        assertThrows(BytecodeLoadException.class,()->BytecodeLoader.load(classes,null,BytecodeLimits.defaults()));
        assertThrows(BytecodeLoadException.class,()->BytecodeLoader.load(classes,"Fixture",null));
    }


    /** A missing superclass index is malformed metadata rather than an uncategorized host exception.
     *
     * @param directory compiler workspace
     * @throws Exception for fixture failures */
    @Test void rejectsAbsentSuperclassIndex(@TempDir Path directory) throws Exception {
        Path classes=BytecodeFixtures.compile(directory,26,
            "@Model public class Fixture { public Fixture() {} @Actor(id=0) public void actor() {} }");
        Path file=classes.resolve("Fixture.class"); byte[] input=Files.readAllBytes(file); var type=ClassFile.of().parse(input);
        int flags=type.flags().flagsMask(), self=type.thisClass().index(), parent=type.superclass().orElseThrow().index();
        byte[] header={(byte)(flags>>>8),(byte)flags,(byte)(self>>>8),(byte)self,(byte)(parent>>>8),(byte)parent,0,0};
        int offset=find(input,header); assertTrue(offset>0); input[offset+4]=0;input[offset+5]=0; Files.write(file,input);
        assertEquals("MALFORMED",assertThrows(BytecodeLoadException.class,
            ()->BytecodeLoader.load(classes,"Fixture",BytecodeLimits.defaults())).category);
    }

    /** Binary names admit supplementary identifier code points and reject punctuation/start/dangling-surrogate input.
     *
     * @param directory compiler workspace
     * @throws Exception for fixture failures */
    @Test void validatesUnicodeNamesWithoutLosingProgress(@TempDir Path directory) throws Exception {
        Path classes=BytecodeFixtures.compile(directory,26,
            "@Model public class Fixture { public Fixture() {} @Actor(id=0) public void actor() {} }");
        var parser=ClassFile.of(); var input=parser.parse(Files.readAllBytes(classes.resolve("Fixture.class")));
        for(String name:List.of("\uD801\uDC00X","A\uD801\uDC00")) {
            Files.write(classes.resolve(name+".class"),parser.transformClass(input,ClassDesc.of(name),ClassTransform.ACCEPT_ALL));
            assertEquals(name,BytecodeLoader.load(classes,name,BytecodeLimits.defaults()).model);
        }
        for(String name:List.of("0Fixture","A!","A\uD800","\uD800","x.".repeat(257)+"Fixture","A".repeat(4097)))
            assertEquals("INPUT",assertThrows(BytecodeLoadException.class,
                ()->BytecodeLoader.load(classes,name,BytecodeLimits.defaults())).category);
    }

    /** An unrelated typed catch cannot be treated as the monitor catch-all admitted by94A.
     *
     * @param directory compiler workspace
     * @throws Exception for fixture failures */
    @Test void rejectsTypedCatchEvenWhenFaultTypeDiffers(@TempDir Path directory) throws Exception {
        Path classes=BytecodeFixtures.compile(directory,26,"""
            @Model public class Fixture { Fixture absent; int value; public Fixture() {}
              @Actor(id=0) public void actor(){try{value=absent.value;}catch(ArithmeticException e){value=9;}}
            }
            """);
        assertEquals("UNSUPPORTED",assertThrows(BytecodeLoadException.class,
            ()->BytecodeLoader.load(classes,"Fixture",BytecodeLimits.defaults())).category);
    }


    /** Lexical DUP/ASTORE adjacency is insufficient when a branch can bypass monitor-slot wiring.
     *
     * @param directory compiler workspace
     * @throws Exception for fixture failures */
    @Test void rejectsBranchIntoMonitorEntryWiring(@TempDir Path directory) throws Exception {
        Path classes=BytecodeFixtures.compile(directory,26,"""
            @Model public class Fixture { Object left=new Object(); Object right=new Object(); int value=1;
              public Fixture() {} @Actor(id=0) public void actor() {}
            }
            """);
        var parser=ClassFile.of();Path file=classes.resolve("Fixture.class");
        Files.write(file,parser.transformClass(parser.parse(Files.readAllBytes(file)),(builder,element)-> {
            if(element instanceof MethodModel method && method.methodName().equalsString("actor"))
                builder.transformMethod(method,(output,item)-> {
                    if(item instanceof CodeModel) output.withCode(code->{
                        var fixture=ClassDesc.of("Fixture"); var enter=code.newLabel();var start=code.newLabel();var end=code.newLabel();var handler=code.newLabel();
                        code.aload(0).getfield(fixture,"left",ConstantDescs.CD_Object).astore(1);
                        code.aload(0).getfield(fixture,"right",ConstantDescs.CD_Object)
                            .aload(0).getfield(fixture,"value",ConstantDescs.CD_int).ifne(enter).dup().astore(1)
                            .labelBinding(enter).monitorenter().labelBinding(start).aload(1).monitorexit().labelBinding(end).return_()
                            .labelBinding(handler).astore(2).aload(1).monitorexit().aload(2).athrow();
                        code.exceptionCatchAll(start,end,handler);
                    }); else output.with(item);
                }); else builder.with(element);
        }));
        assertThrows(BytecodeLoadException.class,()->BytecodeLoader.load(classes,"Fixture",BytecodeLimits.defaults()));
    }


    /** Duplicate method identities cannot hide an alternative body behind first-match resolution.
     *
     * @param directory compiler workspace
     * @throws Exception for fixture failures */
    @Test void rejectsDuplicateMethodIdentities(@TempDir Path directory) throws Exception {
        var classes=BytecodeFixtures.compile(directory,26,
            "@Model public class Fixture {public Fixture(){} @Actor(id=0) public void actor(){helper();} static void helper(){} }");
        var parser=ClassFile.of();Path file=classes.resolve("Fixture.class");
        Files.write(file,parser.transformClass(parser.parse(Files.readAllBytes(file)),(builder,element)-> {
            builder.with(element);
            if(element instanceof MethodModel method && method.methodName().equalsString("helper"))builder.with(element);
        }));
        assertEquals("MALFORMED",assertThrows(BytecodeLoadException.class,
            ()->BytecodeLoader.load(classes,"Fixture",BytecodeLimits.defaults())).category);
    }


    /** The installed JDK verifier skips bridge methods, so this profile rejects a reachable bridge outright.
     *
     * @param directory compiler workspace
     * @throws Exception for fixture failures */
    @Test void rejectsReachableBridgeWithoutTrustingSkippedVerification(@TempDir Path directory) throws Exception {
        var classes=BytecodeFixtures.compile(directory,26,
            "@Model public class Fixture {int value;public Fixture(){} @Actor(id=0) public void actor(){value++;} }");
        var parser=ClassFile.of();Path file=classes.resolve("Fixture.class");
        byte[] bridged=parser.transformClass(parser.parse(Files.readAllBytes(file)),(builder,element)-> {
            if(element instanceof MethodModel method && method.methodName().equalsString("actor"))
                builder.transformMethod(method,(out,item)-> {
                    if(item instanceof AccessFlags flags)out.withFlags(flags.flagsMask()|ClassFile.ACC_BRIDGE); else out.with(item);
                }); else builder.with(element);
        });
        var actor=parser.parse(bridged).methods().stream().filter(m->m.methodName().equalsString("actor")).findFirst().orElseThrow();
        int offset=find(bridged,((CodeAttribute)actor.code().orElseThrow()).codeArray());assertTrue(offset>0);
        bridged[offset]=(byte)Opcode.POP.bytecode();Files.write(file,bridged);
        assertThrows(BytecodeLoadException.class,()->BytecodeLoader.load(classes,"Fixture",BytecodeLimits.defaults()));
    }


    /** Narrow descriptors remain outside94A even though explicit integer narrowing opcodes are supported.
     *
     * @param directory compiler workspace
     * @throws Exception for fixture failures */
    @Test void rejectsNarrowDescriptorsAndInstanceCalls(@TempDir Path directory) throws Exception {
        List<String> bodies=List.of("byte value;", "char value;", "short value;",
            "static byte helper(){return 1;} @Check public boolean check(){return helper()==1;}",
            "static char helper(){return 1;} @Check public boolean check(){return helper()==1;}",
            "static short helper(){return 1;} @Check public boolean check(){return helper()==1;}",
            "public Fixture(){this(1);} public Fixture(int x){}", "void helper(){} @Check public boolean check(){helper();return true;}");
        for(int index=0;index<bodies.size();index++) {
            String constructor=index==6 ? "" : "public Fixture(){}";
            var classes=BytecodeFixtures.compile(directory.resolve("case"+index),26,
                "@Model public class Fixture {"+constructor+" @Actor(id=0) public void actor(){} "+bodies.get(index)+"}");
            assertEquals("UNSUPPORTED",assertThrows(BytecodeLoadException.class,
                ()->BytecodeLoader.load(classes,"Fixture",BytecodeLimits.defaults())).category);
        }
    }

    /** Locate a compiler-produced bytecode region without constructing expected instruction semantics.
     *
     * @param bytes original class bytes
     * @param needle exact code attribute body
     * @return unique offset */
    private static int find(byte[] bytes,byte[] needle) {
        for(int i=0;i<=bytes.length-needle.length;i++) {
            boolean match=true; for(int j=0;j<needle.length;j++) if(bytes[i+j]!=needle[j]) {match=false;break;}
            if(match) return i;
        }
        return -1;
    }
}
