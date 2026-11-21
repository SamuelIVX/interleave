/** Real-definition and child-JVM checks for bytecode DFS/observer logical-work assertions. */
package dev.samhb.mutationharness;

import java.lang.classfile.*;
import java.lang.classfile.instruction.*;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.TimeUnit;
import javax.tools.ToolProvider;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

class BytecodeWorkBudgetTest {
    /** Child replacement workspace. */
    @TempDir Path directory;

    /** Original definitions preserve lost-update detection and normal completion with/without instrumentation.
     * @throws Exception for child/fixture failures */
    @Test void preservesRealSearchAndObserverOutcomes() throws Exception {
        assertEquals("OUTCOMES [CHECK_FALSE, COMPLETED]",run(null,null,false));
        assertEquals("OUTCOMES [CHECK_FALSE, COMPLETED]",run(null,null,true));
    }

    /** Interrupts deterministically after an actual primitive commit, including a shared write and method entry.
     * @throws Exception for class/child failures */
    @Test void cancellationAfterPrimitiveCommitKeepsParentOwned() throws Exception {
        var file=classFile();int[] checkpoints={0};
        var changed=file.transformClass(file.parse(bytes("BytecodeScheduler")),ClassTransform.transformingMethodBodies(
            method->method.methodName().equalsString("advance"),(code,element)->{
                code.with(element);
                if(element instanceof InvokeInstruction call && call.owner().asInternalName().equals("dev/samhb/interleave/bytecode/OpcodeExecutor") && call.name().equalsString("step")) {
                    checkpoints[0]++;var thread=java.lang.constant.ClassDesc.of("java.lang.Thread");
                    code.invokestatic(thread,"currentThread",java.lang.constant.MethodTypeDesc.of(thread))
                        .invokevirtual(thread,"interrupt",java.lang.constant.MethodTypeDesc.of(java.lang.constant.ConstantDescs.CD_void));
                }
            }));
        assertEquals(2,checkpoints[0]);assertEquals(List.of(),file.verify(changed));
        assertEquals("CANCELLATION_OWNED",run("BytecodeScheduler",changed,false,"cancel"));
    }

    /** Removing the actual deque pop must fail a finite graph-work assertion, never a wall-clock oracle.
     * @throws Exception for class/child failures */
    @Test void catchesRemovedDfsPop() throws Exception {
        var file=classFile();int[] changed={0};var original=bytes("BytecodeExplorer");
        var mutant=file.transformClass(file.parse(original),ClassTransform.transformingMethodBodies(
            method->method.methodName().equalsString("explore"),(code,element)->{
                if(element instanceof InvokeInstruction call && call.owner().asInternalName().equals("java/util/ArrayDeque") && call.name().equalsString("pop")) {
                    code.pop().aconst_null();changed[0]++;
                } else code.with(element);
            }));
        assertEquals(1,changed[0]);assertEquals(List.of(),file.verify(mutant));
        assertEquals(List.of(),file.verify(BytecodeWorkBudget.instrument(mutant,true,getClass().getClassLoader())));
        assertEquals("BOUND_EXCEEDED",run("BytecodeExplorer",mutant,true));
    }

    /** A real root-return size removal is stopped by the independent per-invocation event bound.
     * @throws Exception for class/child failures */
    @Test void catchesObserverRootReturnRegression() throws Exception {
        var file=classFile();int[] changed={0};
        var mutant=file.transformClass(file.parse(bytes("BytecodeChecks")),ClassTransform.transformingMethodBodies(
            method->method.methodName().equalsString("evaluate"),(code,element)->{
                if(element instanceof InvokeInstruction call && call.owner().asInternalName().equals("java/util/ArrayList") && call.name().equalsString("size")) {
                    code.pop().iconst_0();changed[0]++;
                } else code.with(element);
            }));
        assertEquals(1,changed[0]);assertEquals(List.of(),file.verify(mutant));
        assertEquals("BOUND_EXCEEDED",run("BytecodeChecks",mutant,true));
    }

    /** A removed real class-count checkpoint cannot turn the monotone loader closure into an infinite pass loop.
     * @throws Exception for checker/child failures */
    @Test void catchesNonconvergentLoaderClosure() throws Exception {
        var file=classFile();int[] changed={0};
        var mutant=file.transformClass(file.parse(bytes("BytecodeLoader")),ClassTransform.transformingMethodBodies(
            method->method.methodName().equalsString("load"),(code,element)->{
                if(changed[0]==0 && element instanceof InvokeInstruction call && call.owner().asInternalName().equals("java/util/Map") && call.name().equalsString("size")) {
                    code.pop().iconst_0();changed[0]++;
                } else code.with(element);
            }));
        assertEquals(1,changed[0]);assertEquals(List.of(),file.verify(mutant));
        var instrumented=BytecodeWorkBudget.instrument(mutant,true,getClass().getClassLoader());
        assertEquals(List.of(),file.verify(instrumented));
        assertThrows(IllegalArgumentException.class,()->BytecodeWorkBudget.instrument(mutant,false,getClass().getClassLoader()));
        // The child starts with the already transformed replacement, matching PIT's redefinition path.
        assertEquals("BOUND_EXCEEDED",run("BytecodeLoader",instrumented,false));
    }

    /** Binding/checkpoint drift fails closed rather than silently omitting the instrumentation.
     * @throws Exception for class-file failures */
    @Test void rejectsMethodAndCheckpointDrift() throws Exception {
        var file=classFile();
        for(String name:List.of("BytecodeExplorer","BytecodeChecks")) {
            var original=bytes(name);String method=name.equals("BytecodeExplorer") ? "explore" : "evaluate";
            var missing=file.transformClass(file.parse(original),ClassTransform.dropping(element->element instanceof MethodModel m && m.methodName().equalsString(method)));
            assertThrows(IllegalArgumentException.class,()->BytecodeWorkBudget.instrument(missing,false,getClass().getClassLoader()));
            var noCheckpoint=file.transformClass(file.parse(original),ClassTransform.transformingMethodBodies(m->m.methodName().equalsString(method),(code,element)->{
                if(element instanceof InvokeInstruction call && (call.name().equalsString("peek") || call.name().equalsString("currentThread"))) {
                    if(call.name().equalsString("peek"))code.pop();code.aconst_null();
                } else code.with(element);
            }));
            assertThrows(IllegalArgumentException.class,()->BytecodeWorkBudget.instrument(noCheckpoint,false,getClass().getClassLoader()));
            assertEquals(List.of(),file.verify(BytecodeWorkBudget.instrument(noCheckpoint,true,getClass().getClassLoader())));
        }
    }

    /** Resolves checker implementation types, never application model classes.
     * @return class-file service for host checker verification */
    private static ClassFile classFile(){return ClassFile.of(ClassFile.ClassHierarchyResolverOption.of(ClassHierarchyResolver.ofClassLoading(BytecodeWorkBudgetTest.class.getClassLoader())));}

    /** Reads the real compiled checker definition.
     * @param name checker class name
     * @return original bytes
     * @throws Exception for missing/failed resources */
    private static byte[] bytes(String name) throws Exception {
        try(var source=BytecodeWorkBudgetTest.class.getResourceAsStream("/dev/samhb/interleave/bytecode/"+name+".class")) {assertNotNull(source);return source.readAllBytes();}
    }

    /** Launches a bounded child and always reaps it; the timeout is only process-startup safety.
     * @param name optional replacement checker class
     * @param replacement optional actual mutant
     * @param agent whether the existing test-only agent is active
     * @param modes optional deterministic cancellation probe mode
     * @return stripped successful child output
     * @throws Exception for fixture/process failures */
    private String run(String name,byte[] replacement,boolean agent,String... modes) throws Exception {
        Path classes=Files.createTempDirectory(directory,"classes");String classpath=System.getProperty("interleave.testRuntimeClasspath");
        Path source=classes.resolve("Fixture.java");String body=modes.length==0 ? """
            import dev.samhb.interleave.bytecode.*;
            @Model public class Fixture {public int x;public Fixture(){}
              @Actor(id=0)public void first(){x++;} @Actor(id=1)public void second(){x++;}
              @Check public boolean valid(){return x==2;}}
            """ : """
            import dev.samhb.interleave.bytecode.*;
            @Model public class Fixture {public int x;public Fixture(){}
              @Actor(id=0)public void first(){x=1;} @Actor(id=1)public synchronized void second(){}}
            """;
        Files.writeString(source,body);
        assertEquals(0,ToolProvider.getSystemJavaCompiler().run(null,null,null,"--release","26","-classpath",classpath,"-d",classes.toString(),source.toString()));
        if(replacement!=null) {Path target=classes.resolve("dev/samhb/interleave/bytecode/"+name+".class");Files.createDirectories(target.getParent());Files.write(target,replacement);}
        var command=new ArrayList<String>();command.add(Path.of(System.getProperty("java.home"),"bin","java").toString());
        if(agent)command.add("-javaagent:"+System.getProperty("interleave.mutationHarnessJar"));
        command.addAll(List.of("-cp",classes+java.io.File.pathSeparator+classpath,"dev.samhb.interleave.bytecode.BytecodeWorkProbe",classes.toString()));
        command.addAll(List.of(modes));
        Path output=Files.createTempFile(directory,"child",".log");var child=new ProcessBuilder(command).redirectErrorStream(true).redirectOutput(output.toFile()).start();
        try {assertTrue(child.waitFor(15,TimeUnit.SECONDS),"Bytecode harness child did not terminate");String text=Files.readString(output);assertEquals(0,child.exitValue(),text);return text.strip();}
        finally {if(child.isAlive()){child.destroyForcibly();assertTrue(child.waitFor(5,TimeUnit.SECONDS),"Bytecode child could not be reaped");}}
    }
}
