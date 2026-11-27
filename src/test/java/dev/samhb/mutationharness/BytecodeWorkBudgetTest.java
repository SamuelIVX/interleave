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
        assertEquals("OUTCOMES [CHECK_FALSE, COMPLETED]",run(null,null,true,"unbounded"));
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
        var file=classFile();var mutant=withoutDfsPop();
        assertEquals(List.of(),file.verify(BytecodeWorkBudget.instrument(mutant,true,getClass().getClassLoader())));
        assertEquals("BOUND_EXCEEDED",run("BytecodeExplorer",mutant,true));
    }

    /** An unlimited caller budget cannot disable the finite-prefix progress assertion.
     * @throws Exception for class/child failures */
    @Test void catchesRemovedDfsPopWithUnboundedSearch() throws Exception {
        assertEquals("BOUND_EXCEEDED",run("BytecodeExplorer",withoutDfsPop(),true,"unbounded"));
    }

    /** The original configured-budget assertion remains independent of the new admission-prefix bound.
     * @throws Exception for checker/child failures */
    @Test void retainsConfiguredBudgetAssertion() throws Exception {
        var file=classFile();int[] changes={0};boolean[] afterBudget={false};
        var mutant=file.transformClass(file.parse(bytes("BytecodeExplorer")),ClassTransform.transformingMethodBodies(
            method->method.methodName().equalsString("visit"),(code,element)->{
                if(element instanceof InvokeInstruction call && call.owner().asInternalName().endsWith("BytecodeExplorer$Budget")
                    && call.name().equalsString("maxStates"))afterBudget[0]=true;
                if(afterBudget[0] && element instanceof BranchInstruction branch) {
                    assertEquals(Opcode.IFLT,branch.opcode());code.branch(Opcode.IFLE,branch.target());changes[0]++;afterBudget[0]=false;
                } else code.with(element);
            }));
        assertEquals(1,changes[0]);assertEquals(List.of(),file.verify(mutant));
        assertEquals("STATE_LIMIT",run(null,null,true,"state-zero"));
        assertEquals("STATE_LIMIT_MISSED",run("BytecodeExplorer",mutant,false,"state-zero"));
        assertEquals("BOUND_EXCEEDED",run("BytecodeExplorer",mutant,true,"state-zero"));
    }

    /** A zero deadline still interrupts before state admission with the original or instrumented clock.
     * @throws Exception for class/child failures */
    @Test void preservesZeroDeadlineInterruption() throws Exception {
        var original=withFixedClockOrigin();
        assertEquals("ZERO_DEADLINE",run("BytecodeExplorer",original,false,"zero"));
        assertEquals("ZERO_DEADLINE",run("BytecodeExplorer",original,true,"zero"));
        assertEquals("POSITIVE_DEADLINE",run("BytecodeExplorer",original,false,"positive"));
        assertEquals("POSITIVE_DEADLINE",run("BytecodeExplorer",original,true,"positive"));
    }

    /** The >= to > boundary mutant stays observable even when the host clock is beyond the origin.
     * @throws Exception for class/child failures */
    @Test void exposesZeroDeadlineBoundaryMutation() throws Exception {
        var file=classFile();int[] changes={0};
        var mutant=file.transformClass(file.parse(withFixedClockOrigin()),ClassTransform.transformingMethodBodies(
            method->method.methodName().equalsString("stopReason"),(code,element)->{
                if(element instanceof BranchInstruction branch && branch.opcode()==Opcode.IFLT) {
                    code.branch(Opcode.IFLE,branch.target());changes[0]++;
                } else code.with(element);
            }));
        assertEquals(1,changes[0]);assertEquals(List.of(),file.verify(mutant));
        assertEquals("ZERO_DEADLINE",run("BytecodeExplorer",mutant,false,"zero"));
        assertEquals("DEADLINE_MISSED",run("BytecodeExplorer",mutant,true,"zero"));
    }

    /** Supplies a deterministic past origin without replacing the actual deadline clock checkpoint.
     * @return real checker definition with a fixed host-clock origin
     * @throws Exception for missing checker bytes */
    private byte[] withFixedClockOrigin() throws Exception {
        var file=classFile();int[] changes={0};
        var result=file.transformClass(file.parse(bytes("BytecodeExplorer")),ClassTransform.transformingMethodBodies(
            method->method.methodName().equalsString("explore"),(code,element)->{
                if(element instanceof InvokeInstruction call && call.owner().asInternalName().equals("java/lang/System")
                    && call.name().equalsString("currentTimeMillis")) {code.lconst_0();changes[0]++;}
                else code.with(element);
            }));
        assertEquals(1,changes[0]);assertEquals(List.of(),file.verify(result));return result;
    }

    /** Produces the actual call-removal mutant seen in both failed calibration trials.
     * @return verified replacement definition
     * @throws Exception for missing checker bytes */
    private byte[] withoutDfsPop() throws Exception {
        var file=classFile();int[] changed={0};var original=bytes("BytecodeExplorer");
        var mutant=file.transformClass(file.parse(original),ClassTransform.transformingMethodBodies(
            method->method.methodName().equalsString("explore"),(code,element)->{
                if(element instanceof InvokeInstruction call && call.owner().asInternalName().equals("java/util/ArrayDeque") && call.name().equalsString("pop")) {
                    code.pop().aconst_null();changed[0]++;
                } else code.with(element);
            }));
        assertEquals(1,changed[0]);assertEquals(List.of(),file.verify(mutant));
        return mutant;
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

    /** The new prefix/clock assertions reject missing fields, methods and initial clock checkpoints.
     * @throws Exception for checker class-file failures */
    @Test void rejectsSearchAccountingAndDeadlineDrift() throws Exception {
        var file=classFile();var original=bytes("BytecodeExplorer");
        for(var name:List.of("states","maxTimeMillis","startedMillis")) {
            var missing=file.transformClass(file.parse(original),ClassTransform.dropping(element->
                element instanceof FieldModel field && field.fieldName().equalsString(name)));
            assertThrows(IllegalArgumentException.class,()->BytecodeWorkBudget.instrument(missing,false,getClass().getClassLoader()));
        }
        var missing=file.transformClass(file.parse(original),ClassTransform.dropping(element->
            element instanceof MethodModel method && method.methodName().equalsString("stopReason")));
        assertThrows(IllegalArgumentException.class,()->BytecodeWorkBudget.instrument(missing,false,getClass().getClassLoader()));
        var noClock=file.transformClass(file.parse(original),ClassTransform.transformingMethodBodies(
            method->method.methodName().equalsString("stopReason"),(code,element)->{
                if(element instanceof InvokeInstruction call && call.owner().asInternalName().equals("java/lang/System")
                    && call.name().equalsString("currentTimeMillis"))code.lconst_0();else code.with(element);
            }));
        assertThrows(IllegalArgumentException.class,()->BytecodeWorkBudget.instrument(noClock,false,getClass().getClassLoader()));
        assertEquals(List.of(),file.verify(BytecodeWorkBudget.instrument(noClock,true,getClass().getClassLoader())));
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
     * @param modes optional cancellation, search-budget or deadline probe mode
     * @return stripped successful child output
     * @throws Exception for fixture/process failures */
    private String run(String name,byte[] replacement,boolean agent,String... modes) throws Exception {
        Path classes=Files.createTempDirectory(directory,"classes");String classpath=System.getProperty("interleave.testRuntimeClasspath");
        Path source=classes.resolve("Fixture.java");String body=!Arrays.asList(modes).contains("cancel") ? """
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
