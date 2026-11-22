/** JVM differential exception delivery, ordered handlers and compiled cleanup behavior. */
package dev.samhb.interleave.bytecode;

import dev.samhb.interleave.format.BytecodeProgramLoader;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;
import static dev.samhb.interleave.bytecode.BytecodeState.*;

class BytecodeExceptionTest {
    /** A typed handler resumes normal execution and compiled finally runs exactly once.
     * @param directory compiler workspace
     * @throws Exception for compiler, loader or independent JVM failures */
    @Test void typedHandlerAndFinallyMatchJvm(@TempDir Path directory) throws Exception {
        for(int release:new int[]{17,21,26}) {
            var work=directory.resolve("release"+release);
            var classes=BytecodeFixtures.compile(work,release,"""
                @Model public class Fixture { public int result,cleanup;public Fixture(){}
                  int read(int x){try{return 8/x;}catch(ArithmeticException e){return 3;}finally{cleanup++;}}
                  @Actor(id=0)public void actor(){result=read(0);}
                  @Check public boolean valid(){return result==3 && cleanup==1;}}
                """);
            assertEquals("3:1",BytecodeFixtures.oracle(work,classes,"Fixture f=new Fixture();f.actor();System.out.print(f.result+\":\"+f.cleanup);"));
            var program=BytecodeProgramLoader.load(classes,"Fixture");
            var state=BytecodeFixtures.run(program,program.initialState(),0);
            assertEquals(Phase.TERMINATED,state.executions.get(0).phase);
            assertEquals(new Int(3),state.fixtureField("result"));assertEquals(new Int(1),state.fixtureField("cleanup"));
            for(var mode:BytecodeScheduler.Mode.values()) {
                var result=new BytecodeExplorer(program,mode).explore(10000);assertTrue(result.exhaustive());
                assertEquals(java.util.Set.of(BytecodeExplorer.Kind.COMPLETED),result.witnesses().stream().map(w->w.outcome().kind()).collect(java.util.stream.Collectors.toSet()));
            }
        }
    }
    /** Rethrow preserves identity, outer superclass selection and replacement by a finally fault.
     * @param directory compiler workspace
     * @throws Exception for compiler or independent JVM failure */
    @Test void orderedHandlersRethrowAndNullMatchJvm(@TempDir Path directory) throws Exception {
        var classes=BytecodeFixtures.compile(directory,17,"""
            @Model public class Fixture {public int result;public boolean firstStage;public Throwable caught;public Fixture(){}
              int inner(){try{throw (RuntimeException)null;}catch(NullPointerException e){caught=e;throw e;}}
              @Actor(id=0)public void actor(){
                try{inner();}catch(ArithmeticException e){result=9;}
                catch(RuntimeException e){if(e==caught)result=1;}
                firstStage=result==1;
                try{try{int x=1/result;throw (RuntimeException)null;}finally{result=2;int x=1/(result-2);}}
                catch(ArithmeticException e){result=3;}
              }
              @Check public boolean valid(){return firstStage && result==3;}}
            """);
        assertEquals("true:3",BytecodeFixtures.oracle(directory,classes,"Fixture f=new Fixture();f.actor();System.out.print(f.firstStage+\":\"+f.result);"));
        var program=BytecodeProgramLoader.load(classes,"Fixture");
        for(var mode:BytecodeScheduler.Mode.values()) {
            var result=new BytecodeExplorer(program,mode).explore(10000);
            assertTrue(result.exhaustive());assertEquals(java.util.Set.of(BytecodeExplorer.Kind.COMPLETED),
                result.witnesses().stream().map(w->w.outcome().kind()).collect(java.util.stream.Collectors.toSet()));
        }
    }

    /** Explicit exception construction retains the interned literal message identity.
     * @param directory compiler workspace
     * @throws Exception for compiler or independent JVM failure */
    @Test void explicitMessageMatchesJvm(@TempDir Path directory) throws Exception {
        var classes=BytecodeFixtures.compile(directory,17,"""
            @Model public class Fixture {public boolean result;public Fixture(){}
              @Actor(id=0)public void actor(){RuntimeException e=new RuntimeException("message");
                boolean message=e.getMessage()=="message";try{throw e;}catch(RuntimeException caught){result=caught==e && message;}}
              @Check public boolean valid(){return result;}}
            """);
        assertEquals("true",BytecodeFixtures.oracle(directory,classes,"Fixture f=new Fixture();f.actor();System.out.print(f.result);"));
        var program=BytecodeProgramLoader.load(classes,"Fixture");
        for(var mode:BytecodeScheduler.Mode.values()) {
            var result=new BytecodeExplorer(program,mode).explore(10000);
            assertTrue(result.exhaustive());assertEquals(java.util.Set.of(BytecodeExplorer.Kind.COMPLETED),
                result.witnesses().stream().map(w->w.outcome().kind()).collect(java.util.stream.Collectors.toSet()));
        }
    }

    /** Compiled two-resource cleanup preserves body identity and reverse-close suppressed order.
     * @param directory compiler workspace
     * @throws Exception for resource, compiler or independent JVM failure */
    @Test void tryWithResourcesKeepsPrimaryAndSuppressed(@TempDir Path directory) throws Exception {
        String source;
        try(var resource=getClass().getResourceAsStream("/bytecode/exceptions/two-resources/Fixture.java")) {
            source=new String(java.util.Objects.requireNonNull(resource).readAllBytes(),java.nio.charset.StandardCharsets.UTF_8);
        }
        for(int release:new int[]{17,21,26}) {
            var work=directory.resolve("release"+release);var classes=BytecodeFixtures.compile(work,release,source);
            assertEquals("21:body:2:second:first:true:true",BytecodeFixtures.oracle(work,classes,
                "Fixture f=new Fixture();f.actor();System.out.print(f.order+\":\"+f.primary.getMessage()+\":\"+f.suppressed.length+\":\"+f.first+\":\"+f.second+\":\"+(f.suppressed[0]!=f.primary)+\":\"+(f.suppressed[0]!=f.suppressed[1]));"));
            assertEquals("true:false:false",BytecodeFixtures.oracle(work,classes,
                "Fixture f=new Fixture();f.actor();boolean original=f.valid();f.primary=null;boolean missing=f.valid();f.primary=new Failure(\"changed\");System.out.print(original+\":\"+missing+\":\"+f.valid());"));
            var program=BytecodeProgramLoader.load(classes,"Fixture");
            var accounting=BytecodeFixtures.run(program,program.initialState(),0).executions.get(0);
            assertEquals(6,accounting.allocations);assertEquals(29,accounting.cells);
            for(var mode:BytecodeScheduler.Mode.values()) {
                var result=new BytecodeExplorer(program,mode).explore(10000);
                assertTrue(result.exhaustive());assertEquals(java.util.Set.of(BytecodeExplorer.Kind.COMPLETED),
                    result.witnesses().stream().map(w->w.outcome().kind()).collect(java.util.stream.Collectors.toSet()));
            }
        }
    }

    /** Unknown implicit VM messages reject rather than silently becoming target-language null.
     * @param directory compiler workspace
     * @throws Exception for fixture compilation */
    @Test void implicitMessageInspectionRejects(@TempDir Path directory) throws Exception {
        var classes=BytecodeFixtures.compile(directory,17,"""
            @Model public class Fixture {public boolean result;public Fixture(){}
              @Actor(id=0)public void actor(){try{int x=1/0;}catch(ArithmeticException e){result=e.getMessage()!=null;}}}
            """);
        var diagnostic=assertThrows(BytecodeLoadException.class,()->BytecodeProgramLoader.load(classes,"Fixture"));
        assertTrue(diagnostic.getMessage().contains("message"));
    }

    /** Pending intrinsic events, payloads and compiled handlers survive public JSON replay.
     * @param directory compiler workspace
     * @throws Exception for fixture failures */
    @Test void exceptionIdentityAndConsumersReplayStrictly(@TempDir Path directory) throws Exception {
        var classes=BytecodeFixtures.compile(directory,17,"""
            @Model public class Fixture {public Throwable first,second;public Fixture(){}
              @Actor(id=0)public void actor(){first=new RuntimeException("body");second=new RuntimeException("close");
                first.addSuppressed(second);throw (RuntimeException)first;}}
            """);
        var program=BytecodeProgramLoader.load(classes,"Fixture");
        var result=dev.samhb.interleave.InterleaveRunner.builder().build().runBytecode(program);
        assertTrue(result.hasViolation());assertFalse(result.hasIncomplete());
        var trace=result.failingTraces().getFirst();assertEquals(2,trace.bytecodeEvidence().version());
        assertEquals("BYTECODE-SC-3",trace.bytecodeEvidence().identity().profileVersion());
        assertTrue(trace.bytecodeEvidence().transitions().stream().flatMap(t->t.operations().stream()).anyMatch(op->op.event().equals("INTRINSIC_BODY")));
        assertTrue(trace.bytecodeEvidence().transitions().stream().flatMap(t->t.operations().stream()).anyMatch(op->op.event().equals("MONITOR_EXIT")));
        java.nio.file.Files.writeString(Path.of("build/bytecode-exception.json"),result.toJson());
        java.nio.file.Files.writeString(Path.of("build/bytecode-exception-report.json"),new dev.samhb.interleave.report.ReportWriter(new dev.samhb.interleave.report.BenchmarkHarness().runBytecode(program)).writeJson());
        var cli=BytecodeReleaseTest.cli(directory,"--bytecode","Fixture","--class-path",classes.toString(),"--json");
        java.nio.file.Files.writeString(Path.of("build/bytecode-exception-cli.json"),cli.getValue());
        var decoded=dev.samhb.interleave.TraceRecord.fromJson(trace.toJson());
        var replay=new dev.samhb.interleave.search.TraceReplayer().replayBytecode(program,decoded);
        assertEquals(trace.states(),replay.states());assertEquals(trace.bytecodeEvidence().terminal(),replay.bytecodeEvidence().replayObservation());
        var altered=com.google.gson.JsonParser.parseString(trace.toJson()).getAsJsonObject();
        var typed=altered.getAsJsonObject("bytecodeEvidence").getAsJsonArray("snapshots");
        String key="actor:0:0.@Throwable.suppressed0:internal";
        typed.get(typed.size()-1).getAsJsonObject().getAsJsonObject("fields").getAsJsonObject(key).addProperty("raw","actor:0:0");
        var display=altered.getAsJsonArray("states");display.get(display.size()-1).getAsJsonObject().getAsJsonObject("fields").addProperty(key,"reference:actor:0:0");
        var changed=dev.samhb.interleave.TraceRecord.fromJson(altered.toString());
        assertThrows(dev.samhb.interleave.search.CorruptedTraceException.class,()->new dev.samhb.interleave.search.TraceReplayer().replayBytecode(program,changed));
    }

    /** Empty caches, fresh arrays, duplicate order, causes and disabled suppression match the JVM.
     * @param directory compiler workspace
     * @throws Exception for compiler or independent JVM failures */
    @Test void suppressionIdentityAndValidationMatchJvm(@TempDir Path directory) throws Exception {
        var classes=BytecodeFixtures.compile(directory,17,"""
            class Quiet extends Throwable {Quiet(){super(null,null,false,false);}}
            @Model public class Fixture {public boolean result;public Fixture(){}
              @Actor(id=0)public void actor(){
                Throwable first=new RuntimeException("one"),second=new RuntimeException("two");
                Throwable plain=new Throwable(null,first);Quiet quiet=new Quiet();
                boolean valid=plain.getCause()==first && first.getCause()==null;
                Throwable[] empty=first.getSuppressed();valid=valid && empty==second.getSuppressed() && empty.length==0;
                first.addSuppressed(second);first.addSuppressed(second);
                Throwable[] a=first.getSuppressed(),b=first.getSuppressed();
                valid=valid && a!=b && a.length==2 && a[0]==second && a[1]==second;
                a[0]=first;valid=valid && b[0]==second && first.getSuppressed()[0]==second;
                boolean self=false,nil=false;
                try{quiet.addSuppressed(quiet);}catch(IllegalArgumentException e){self=true;}
                try{quiet.addSuppressed(null);}catch(NullPointerException e){nil=true;}
                quiet.addSuppressed(first);result=valid && self && nil && quiet.getSuppressed()==empty;
              }
              @Check public boolean valid(){return result;}}
            """);
        assertEquals("true",BytecodeFixtures.oracle(directory,classes,"Fixture f=new Fixture();f.actor();System.out.print(f.result);"));
        var program=BytecodeProgramLoader.load(classes,"Fixture");
        var result=dev.samhb.interleave.InterleaveRunner.builder().build().runBytecode(program);
        assertFalse(result.hasViolation());assertFalse(result.hasIncomplete());
        var actor=result.completedTraces().getFirst().bytecodeEvidence().snapshots().getLast().locals().get("0");
        // Six Throwable payloads at six cells, three three-cell arrays, two suppressed references.
        assertEquals("47",actor.get("heapCells").raw());assertEquals("9",actor.get("allocations").raw());
    }

    /** Actual merged95 native bytes remain readable without guessing a charged owner.
     * @throws Exception for archived fixture or native stream failures */
    @Test void legacyNativeCutoffRetainsAbsentOwner() throws Exception {
        com.google.gson.JsonObject fixture;
        try(var resource=getClass().getResourceAsStream("/bytecode/exceptions/legacy-cutoff.json")) {
            fixture=com.google.gson.JsonParser.parseString(new String(java.util.Objects.requireNonNull(resource).readAllBytes(),java.nio.charset.StandardCharsets.UTF_8)).getAsJsonObject();
        }
        byte[] bytes=java.util.Base64.getDecoder().decode(fixture.get("base64").getAsString());
        assertEquals(fixture.get("sha256").getAsString(),java.util.HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256").digest(bytes)));
        try(var input=new java.io.ObjectInputStream(new java.io.ByteArrayInputStream(bytes))) {
            var cutoff=(dev.samhb.interleave.core.BytecodeEvidence.Cutoff)input.readObject();
            assertNull(cutoff.budgetOwner());assertEquals("ALLOCATIONS",cutoff.reason());assertEquals(8,cutoff.location().bci());
            assertEquals(1,cutoff.limit());assertEquals(1,cutoff.required());assertEquals(0,cutoff.remaining());
        }
    }

    /** Constructors retain full-cost preflight and exact-limit normal return semantics.
     * @param directory compiler workspace
     * @throws Exception for fixture failures */
    @Test void constructorFuelPreflightAndExactReturn(@TempDir Path directory) throws Exception {
        var classes=BytecodeFixtures.compile(directory,17,
            "@Model public class Fixture {public Fixture(){} @Actor(id=0)public void actor(){new RuntimeException();}}");
        var shortProgram=BytecodeProgramLoader.load(classes,"Fixture",new BytecodeLimits(3,128,8192,32,64,256,4096,128,8192,1024));
        var shortResult=dev.samhb.interleave.InterleaveRunner.builder().build().runBytecode(shortProgram);
        var trace=shortResult.incompleteTraces().getFirst();var cutoff=trace.bytecodeEvidence().terminal().cutoff();
        assertEquals("INSTRUCTION_FUEL",cutoff.reason());assertEquals(4,cutoff.location().bci());assertEquals(2,cutoff.required());
        assertEquals(1,cutoff.remaining());assertEquals(0,cutoff.budgetOwner());
        var actor=trace.bytecodeEvidence().snapshots().getLast().locals().get("0");assertEquals("2",actor.get("instructions").raw());
        assertTrue(trace.bytecodeEvidence().snapshots().getLast().heap().stream().filter(o->o.type().equals("java/lang/RuntimeException"))
            .allMatch(o->o.owner()==-2 && o.depth()==0 && !o.initialized()));
        // NEW, DUP, invoke+one intrinsic unit, POP, RETURN: six units and five opcodes.
        var exactProgram=BytecodeProgramLoader.load(classes,"Fixture",new BytecodeLimits(6,128,8192,32,64,256,4096,128,8192,1024));
        var exact=dev.samhb.interleave.InterleaveRunner.builder().build().runBytecode(exactProgram);
        assertFalse(exact.hasIncomplete());assertFalse(exact.hasViolation());assertEquals(1,exact.completedTraces().size());
        assertEquals("6",exact.completedTraces().getFirst().bytecodeEvidence().snapshots().getLast().locals().get("0").get("instructions").raw());
    }

    /** VM fault materialization exhausts at the original division rather than a handler position.
     * @param directory compiler workspace
     * @throws Exception for fixture failures */
    @Test void materializationCutoffKeepsOriginalThrowLocation(@TempDir Path directory) throws Exception {
        var classes=BytecodeFixtures.compile(directory,17,"""
            @Model public class Fixture {public int result,zero;public Fixture(){}
              @Actor(id=0)public void actor(){Object used=new Object();try{result=1/zero;}catch(ArithmeticException e){result=9;}}}
            """);
        var program=BytecodeProgramLoader.load(classes,"Fixture",new BytecodeLimits(256,1,8192,32,64,256,4096,128,8192,1024));
        var result=dev.samhb.interleave.InterleaveRunner.builder().build().runBytecode(program);
        assertFalse(result.hasViolation());assertTrue(result.hasIncomplete());
        var trace=result.incompleteTraces().getFirst();var terminal=trace.bytecodeEvidence().terminal();
        assertEquals("ALLOCATIONS",terminal.cutoff().reason());assertEquals(14,terminal.cutoff().location().bci());
        assertEquals("actor",terminal.cutoff().location().name());assertEquals(0,terminal.cutoff().budgetOwner());
        assertEquals("java/lang/ArithmeticException",terminal.fault().type());
        assertEquals("int:0",trace.states().getLast().fields().get("setup:0.Fixture.result:I"));
        assertEquals(terminal,new dev.samhb.interleave.search.TraceReplayer().replayBytecode(program,trace).bytecodeEvidence().replayObservation());
    }

    /** Hidden Throwable callbacks demonstrably execute on the JVM and reject in the interpreter.
     * @param directory compiler workspace
     * @throws Exception for compiler or JVM failures */
    @Test void hiddenThrowableCallbacksReject(@TempDir Path directory) throws Exception {
        var sources=java.util.List.of(
            "class Evil extends RuntimeException {public Throwable fillInStackTrace(){Fixture.effects++;return this;}}",
            "class Evil extends RuntimeException {public String toString(){Fixture.effects++;return \"cause\";}}",
            "class Evil extends RuntimeException {public String toString(){Fixture.effects++;return \"cause\";}}");
        var bodies=java.util.List.of("new Evil();",
            "Throwable e=new Throwable();e.initCause(null);try{e.initCause(new Evil());}catch(IllegalStateException expected){}",
            "new RuntimeException(new Evil());");
        for(int index=0;index<sources.size();index++) {
            var work=directory.resolve("case"+index);var classes=BytecodeFixtures.compile(work,17,sources.get(index)+
                "@Model public class Fixture {public static int effects;public Fixture(){} @Actor(id=0)public void actor(){"+bodies.get(index)+"}}");
            assertEquals("1",BytecodeFixtures.oracle(work,classes,"new Fixture().actor();System.out.print(Fixture.effects);"));
            assertEquals("UNSUPPORTED",assertThrows(BytecodeLoadException.class,()->BytecodeProgramLoader.load(classes,"Fixture")).category);
        }
    }

    /** Nested reentrant compiled cleanup executes before a typed outer handler.
     * @param directory compiler workspace
     * @throws Exception for compiler or independent JVM failures */
    @Test void nestedUnwindReleasesExactMonitorDepth(@TempDir Path directory) throws Exception {
        var classes=BytecodeFixtures.compile(directory,17,"""
            @Model public class Fixture {public int result;public Object lock=new Object();public Fixture(){}
              @Actor(id=0)public synchronized void actor(){synchronized(lock){try{synchronized(lock){throw new RuntimeException();}}
                catch(RuntimeException e){result++;}}}
              @Check public boolean valid(){return result==1;}}
            """);
        assertEquals("1",BytecodeFixtures.oracle(directory,classes,"Fixture f=new Fixture();f.actor();System.out.print(f.result);"));
        var program=BytecodeProgramLoader.load(classes,"Fixture");BytecodeBatchingOracleTest.compare(program);
        var result=dev.samhb.interleave.InterleaveRunner.builder().build().runBytecode(program);
        assertFalse(result.hasViolation());assertFalse(result.hasIncomplete());
        assertTrue(result.completedTraces().getFirst().bytecodeEvidence().snapshots().getLast().heap().stream().allMatch(o->o.owner()==-2 && o.depth()==0));
    }

    /** Typed-handler materialization never lets an observer catch and normalize its own VM fault.
     * @param directory compiler workspace
     * @throws Exception for fixture failures */
    @Test void observerFaultAbortsBeforeHandlerAllocation(@TempDir Path directory) throws Exception {
        var classes=BytecodeFixtures.compile(directory,17,"""
            @Model public class Fixture {public int zero;public Fixture(){} @Actor(id=0)public void actor(){}
              @Check(when=Check.When.ALWAYS)public boolean valid(){try{return 1/zero>0;}catch(ArithmeticException e){return true;}}}
            """);
        var program=BytecodeProgramLoader.load(classes,"Fixture");BytecodeBatchingOracleTest.compare(program);
        var result=dev.samhb.interleave.InterleaveRunner.builder().build().runBytecode(program);
        assertFalse(result.hasViolation());assertTrue(result.hasIncomplete());assertFalse(result.limitExceeded());
        var trace=result.incompleteTraces().getFirst();assertEquals("OBSERVER_ERROR",trace.bytecodeEvidence().terminal().kind());
        assertTrue(trace.bytecodeEvidence().snapshots().getLast().heap().stream().noneMatch(o->o.type().endsWith("Exception")));
    }

    /** Literal identity and UTF-16 units survive input, constant fields, evidence and replay.
     * @param directory compiler workspace
     * @throws Exception for fixture failures */
    @Test void literalUtf16AndSeparateSetupAccounting(@TempDir Path directory) throws Exception {
        var classes=BytecodeFixtures.compile(directory,17,"""
            @Model public class Fixture {public static final String MESSAGE="\\uD800A\\uDC00";public boolean result;public Fixture(){}
              @Actor(id=0)public void actor(){Throwable e=new Throwable(MESSAGE);result=e.getMessage()==MESSAGE;}
              @Check public boolean valid(){return result;}}
            """);
        var program=BytecodeProgramLoader.load(classes,"Fixture",new BytecodeLimits(256,128,8192,32,64,3,4096,1,9,1024));
        var result=dev.samhb.interleave.InterleaveRunner.builder().build().runBytecode(program);
        assertFalse(result.hasIncomplete());assertFalse(result.hasViolation());
        var trace=result.completedTraces().getFirst();var fields=trace.bytecodeEvidence().snapshots().getFirst().fields();
        assertEquals("55296",fields.get("constant:1.@String.unit0:I").raw());assertEquals("65",fields.get("constant:1.@String.unit1:I").raw());
        assertEquals("56320",fields.get("constant:1.@String.unit2:I").raw());assertEquals("int:1",trace.states().getFirst().fields().get("@setup.allocations"));
        assertEquals("int:9",trace.states().getFirst().fields().get("@setup.heapCells"));
        var replay=new dev.samhb.interleave.search.TraceReplayer().replayBytecode(program,dev.samhb.interleave.TraceRecord.fromJson(trace.toJson()));
        assertEquals(trace.states(),replay.states());
        assertEquals("LIMIT",assertThrows(BytecodeLoadException.class,()->BytecodeProgramLoader.load(classes,"Fixture",new BytecodeLimits(256,128,8192,32,64,2,4096,1,9,1024))).category);
        assertEquals("LIMIT",assertThrows(BytecodeLoadException.class,()->BytecodeProgramLoader.load(classes,"Fixture",new BytecodeLimits(256,128,8192,32,64,3,4096,1,8,1024))).category);
    }

    /** An application interface can dispatch to an inherited trusted Throwable implementation.
     * @param directory compiler workspace
     * @throws Exception for compiler or JVM failures */
    @Test void interfaceDispatchToInheritedThrowableOperation(@TempDir Path directory) throws Exception {
        var classes=BytecodeFixtures.compile(directory,17,"""
            interface Named {String getMessage();Throwable getCause();}
            class Failure extends Throwable implements Named {Failure(){super("message",null);}}
            @Model public class Fixture {public boolean result;public Fixture(){}
              @Actor(id=0)public void actor(){Named n=new Failure();result=n.getMessage()=="message" && n.getCause()==null;}
              @Check public boolean valid(){return result;}}
            """);
        assertEquals("true",BytecodeFixtures.oracle(directory,classes,"Fixture f=new Fixture();f.actor();System.out.print(f.result);"));
        var program=BytecodeProgramLoader.load(classes,"Fixture");BytecodeBatchingOracleTest.compare(program);
        var result=dev.samhb.interleave.InterleaveRunner.builder().build().runBytecode(program);
        assertFalse(result.hasViolation());assertFalse(result.hasIncomplete());
    }

    /** A Throwable fixture pays the same five payload cells as an actor-created Throwable subclass.
     * @param directory compiler workspace
     * @throws Exception for fixture failures */
    @Test void throwableFixtureChargesSetupPayload(@TempDir Path directory) throws Exception {
        var classes=BytecodeFixtures.compile(directory,17,"""
            @Model public class Fixture extends Throwable {public boolean result;public Fixture(){super("m");}
              @Actor(id=0)public void actor(){result=getMessage()=="m";}
              @Check public boolean valid(){return result;}}
            """);
        // Fixture six-cell base+result1, one class monitor, String header+length+one unit3.
        var program=BytecodeProgramLoader.load(classes,"Fixture",new BytecodeLimits(256,128,8192,32,64,256,4096,1,11,1024));
        var result=dev.samhb.interleave.InterleaveRunner.builder().build().runBytecode(program);
        assertFalse(result.hasViolation());assertFalse(result.hasIncomplete());
        var snapshot=result.completedTraces().getFirst().bytecodeEvidence().snapshots().getFirst();
        assertEquals("int:11",result.completedTraces().getFirst().states().getFirst().fields().get("@setup.heapCells"));
        assertEquals(8,snapshot.fields().keySet().stream().filter(name->!name.startsWith("@setup.")).count());assertEquals(3,snapshot.heap().size());
        assertEquals("LIMIT",assertThrows(BytecodeLoadException.class,()->BytecodeProgramLoader.load(classes,"Fixture",new BytecodeLimits(256,128,8192,32,64,256,4096,1,10,1024))).category);
    }

    /** Zero/one/two close failures, normal body, body failure and disabled suppression match the JVM.
     * @param directory compiler workspace
     * @throws Exception for source/compiler/JVM failures */
    @Test void resourceFailureVariantsMatchJvm(@TempDir Path directory) throws Exception {
        String template;
        try(var input=getClass().getResourceAsStream("/bytecode/exceptions/resource-variants/Fixture.java")) {
            template=new String(java.util.Objects.requireNonNull(input).readAllBytes(),java.nio.charset.StandardCharsets.UTF_8);
        }
        int[] masks={0,2,6,0,2,6,6};boolean[] bodies={false,false,false,true,true,true,false};
        String[] expected={"21:none:0:none:none","21:first:0:none:none","21:second:1:first:none",
            "21:body:0:none:none","21:body:1:first:none","21:body:2:second:first","21:null:0:none:none"};
        for(int release:new int[]{17,21,26})for(int sample=0;sample<masks.length;sample++) {
            String source=template.replace("failures=6","failures="+masks[sample]).replace("bodyFail=true,disabled=false",
                "bodyFail="+bodies[sample]+",disabled="+(sample==6));
            var work=directory.resolve("release"+release+"-case"+sample);var classes=BytecodeFixtures.compile(work,release,source);
            assertEquals(expected[sample],BytecodeFixtures.oracle(work,classes,
                "Fixture f=new Fixture();f.actor();System.out.print(f.order+\":\"+(f.primary==null?\"none\":f.message==null?\"null\":f.message)+\":\"+(f.suppressed==null?0:f.suppressed.length)+\":\"+(f.first==null?\"none\":f.first)+\":\"+(f.second==null?\"none\":f.second));"));
            var program=BytecodeProgramLoader.load(classes,"Fixture",new BytecodeLimits(4096,128,8192,32,64,256,4096,128,8192,1024));
            var state=BytecodeFixtures.run(program,program.initialState(),0);assertEquals(Phase.TERMINATED,state.executions.get(0).phase);
            assertEquals(new Int(21),state.fixtureField("order"));String[] values=expected[sample].split(":");
            assertEquals(values[1].equals("none") || values[1].equals("null") ? NULL : program.strings.get(values[1]),state.fixtureField("message"));
            var suppressed=(Ref)state.fixtureField("suppressed");assertEquals(Integer.parseInt(values[2]),suppressed.equals(NULL) ? 0 : state.heap.get(suppressed).elements.size());
            assertEquals(values[3].equals("none") ? NULL : program.strings.get(values[3]),state.fixtureField("first"));
            assertEquals(values[4].equals("none") ? NULL : program.strings.get(values[4]),state.fixtureField("second"));
            BytecodeBatchingOracleTest.compare(program);
        }
    }

    /** Reordering a real class-file handler table changes execution and invalidates old evidence.
     * @param directory compiler workspace
     * @throws Exception for compiler, class-file or JVM failures */
    @Test void handlerOrderIsArtifactBoundAndMatchesJvm(@TempDir Path directory) throws Exception {
        var classes=BytecodeFixtures.compile(directory,17,"""
            @Model public class Fixture {public int result,zero;public Fixture(){}
              @Actor(id=0)public void actor(){try{int x=1/zero;}catch(ArithmeticException e){result=1;}catch(RuntimeException e){result=2;}}}
            """);
        var original=BytecodeProgramLoader.load(classes,"Fixture");var originalRun=dev.samhb.interleave.InterleaveRunner.builder().build().runBytecode(original);
        var trace=originalRun.completedTraces().getFirst();assertEquals("1",BytecodeFixtures.oracle(directory,classes,"Fixture f=new Fixture();f.actor();System.out.print(f.result);"));
        var parser=java.lang.classfile.ClassFile.of();var file=classes.resolve("Fixture.class");var model=parser.parse(java.nio.file.Files.readAllBytes(file));
        var handlers=model.methods().stream().filter(m->m.methodName().equalsString("actor")).findFirst().orElseThrow().code().orElseThrow().exceptionHandlers();
        assertEquals(2,handlers.size());
        java.lang.classfile.CodeTransform reorder=(code,element)->{if(!(element instanceof java.lang.classfile.instruction.ExceptionCatch))code.with(element);};
        reorder=reorder.andThen(java.lang.classfile.CodeTransform.endHandler(code->handlers.reversed().forEach(code::with)));
        java.nio.file.Files.write(file,parser.transformClass(model,java.lang.classfile.ClassTransform.transformingMethodBodies(m->m.methodName().equalsString("actor"),reorder)));
        assertEquals("2",BytecodeFixtures.oracle(directory,classes,"Fixture f=new Fixture();f.actor();System.out.print(f.result);"));
        var changed=BytecodeProgramLoader.load(classes,"Fixture");var state=BytecodeFixtures.run(changed,changed.initialState(),0);
        assertEquals(new Int(2),state.fixtureField("result"));assertNotEquals(original.artifactDigest(),changed.artifactDigest());
        assertThrows(dev.samhb.interleave.search.CorruptedTraceException.class,()->new dev.samhb.interleave.search.TraceReplayer().replayBytecode(changed,trace));
        assertEquals(trace.states(),new dev.samhb.interleave.search.TraceReplayer().replayBytecode(original,trace).states());
    }

    /** Constant requirements follow inherited interface targets rather than only symbolic owners.
     * @param directory compiler workspace
     * @throws Exception for compiler or JVM failures */
    @Test void interfaceThrowableCallsInstallRequiredConstants(@TempDir Path directory) throws Exception {
        for(boolean emptyGetter:new boolean[]{true,false}) {
            String action=emptyGetter ? "result=s.getSuppressed().length+1;"
                : "try{s.addSuppressed((Throwable)s);}catch(IllegalArgumentException e){result=e.getMessage()!=null ? 1 : 9;}";
            var work=directory.resolve(Boolean.toString(emptyGetter));var classes=BytecodeFixtures.compile(work,17,"""
                interface Supp {Throwable[] getSuppressed();void addSuppressed(Throwable value);}
                class Failure extends Throwable implements Supp {Failure(){super();}}
                @Model public class Fixture {public int result;public Fixture(){}
                  @Actor(id=0)public void actor(){Supp s=new Failure();
                """+action+"} @Check public boolean valid(){return result==1;}}");
            assertEquals("1",BytecodeFixtures.oracle(work,classes,"Fixture f=new Fixture();f.actor();System.out.print(f.result);"));
            var program=BytecodeProgramLoader.load(classes,"Fixture");BytecodeBatchingOracleTest.compare(program);
            var result=dev.samhb.interleave.InterleaveRunner.builder().build().runBytecode(program);
            assertFalse(result.hasViolation());assertFalse(result.hasIncomplete());
        }
    }

}
