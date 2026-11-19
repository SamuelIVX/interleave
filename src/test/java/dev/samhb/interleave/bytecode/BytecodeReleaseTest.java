/** Public bytecode loading and runner behavior through the approved94C release interfaces. */
package dev.samhb.interleave.bytecode;

import dev.samhb.interleave.InterleaveRunner;
import dev.samhb.interleave.format.BytecodeProgramLoader;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

class BytecodeReleaseTest {
    /** Real compiled increments produce a check failure and completion through the public runner.
     * @param directory external compiler workspace
     * @throws Exception for fixture failures */
    @Test void publicLoaderAndRunnerFindRealLostUpdate(@TempDir Path directory) throws Exception {
        var classes=BytecodeFixtures.compile(directory,26,"""
            @Model public class Fixture {public int x;public Fixture(){}
              @Actor(id=0)public void first(){x++;} @Actor(id=1)public void second(){x++;}
              @Check public boolean valid(){return x==2;}}
            """);
        var program=BytecodeProgramLoader.load(classes,"Fixture");
        var result=InterleaveRunner.builder().build().runBytecode(program);
        assertTrue(result.hasViolation());assertFalse(result.limitExceeded());assertFalse(result.hasIncomplete());
        assertEquals(1,result.failingTraces().size());assertEquals(1,result.completedTraces().size());
        var trace=result.failingTraces().getFirst();assertNull(trace.evidence());assertNotNull(trace.bytecodeEvidence());
        assertEquals(program.artifactDigest(),trace.bytecodeEvidence().identity().artifactDigest());
        assertEquals(program.profileVersion(),trace.bytecodeEvidence().identity().profileVersion());
        assertEquals(program.limits(),trace.bytecodeEvidence().identity().limits());
        assertEquals("Fixture",trace.bytecodeEvidence().identity().modelClass());
        assertEquals("BATCHED",trace.bytecodeEvidence().identity().mode());
        assertEquals("FINAL",trace.bytecodeEvidence().identity().checks().getFirst().when());
        assertEquals(dev.samhb.interleave.core.StepOutcome.ADVANCED,trace.outcomes().getFirst());
        assertEquals(dev.samhb.interleave.core.StepOutcome.TERMINATED,trace.outcomes().getLast());

        assertEquals(trace.threadIds().size(),trace.bytecodeEvidence().transitions().size());
        assertEquals(trace.threadIds().size()+1,trace.states().size());
        assertEquals("CHECK_FALSE",trace.bytecodeEvidence().terminal().kind());
        assertEquals("int:1",trace.states().getLast().fields().get("setup:0.Fixture.x:I"));
        var initial=trace.bytecodeEvidence().snapshots().getFirst();var last=trace.bytecodeEvidence().snapshots().getLast();
        assertEquals("0",initial.fields().get("setup:0.Fixture.x:I").raw());
        assertEquals("REFERENCE",initial.locals().get("0").get("frame0.local0").type());
        assertEquals("setup:0",initial.locals().get("0").get("frame0.local0").raw());
        assertEquals("NULL",initial.locals().get("0").get("frame0.monitor").type());
        assertTrue(last.heap().stream().allMatch(object->object.shared() && object.initialized()));
        for(var actor:java.util.List.of("0","1")) {
            var local=last.locals().get(actor);assertEquals("TERMINATED",local.get("phase").raw());
            assertEquals("7",local.get("instructions").raw());assertEquals("0",local.get("allocations").raw());
            assertEquals("0",local.get("heapCells").raw());assertEquals("0",local.get("allocationSequence").raw());
        }

        var json=com.google.gson.JsonParser.parseString(result.toJson()).getAsJsonObject();
        assertEquals("BYTECODE",json.get("programKind").getAsString());
        assertEquals("EXACT",json.get("storeType").getAsString());assertEquals("VIOLATION",json.get("verdict").getAsString());
        assertEquals("DFS",json.get("requestedStrategy").getAsString());assertEquals("DFS",json.get("effectiveStrategy").getAsString());
        assertTrue(json.get("fallbackReason").isJsonNull());assertFalse(json.get("limitExceeded").getAsBoolean());
        var identity=json.getAsJsonObject("bytecodeIdentity");
        assertEquals(program.artifactDigest(),identity.get("artifactDigest").getAsString());
        assertEquals("BYTECODE-SC-1",identity.get("profileVersion").getAsString());
        assertEquals("Fixture",identity.get("modelClass").getAsString());assertEquals("BATCHED",identity.get("mode").getAsString());
        assertEquals(256,identity.getAsJsonObject("limits").get("actorInstructions").getAsInt());
        assertEquals(2,identity.getAsJsonArray("actors").size());assertEquals(1,identity.getAsJsonArray("checks").size());

        assertFalse(trace.toJson().contains("\"sourceDigest\""));
    }    /** Strict replay validates actual operations/checks and returns immutable matching snapshots.
     * @param directory external compiler workspace
     * @throws Exception for fixture failures */
    @Test void strictReplayAuthenticatesRealWitnesses(@TempDir Path directory) throws Exception {
        var classes=BytecodeFixtures.compile(directory,26,"""
            @Model public class Fixture {public int x;public Fixture(){}
              @Actor(id=0)public void first(){x++;} @Actor(id=1)public void second(){x++;}
              @Check public boolean valid(){return x==2;}}
            """);
        var program=BytecodeProgramLoader.load(classes,"Fixture");var result=InterleaveRunner.builder().build().runBytecode(program);
        for(var trace:java.util.List.of(result.failingTraces().getFirst(),result.completedTraces().getFirst())) {
            var replay=new dev.samhb.interleave.search.TraceReplayer().replayBytecode(program,trace);
            assertEquals(trace.outcome(),replay.outcome());assertEquals(trace.states(),replay.states());
            assertEquals(trace.bytecodeEvidence().snapshots(),replay.bytecodeEvidence().snapshots());
            assertEquals(trace.bytecodeEvidence().terminal(),replay.bytecodeEvidence().terminal());
            assertEquals(trace.bytecodeEvidence().terminal(),replay.bytecodeEvidence().replayObservation());
        }
    }

    /** Reports retain artifact identity and replay real deadlock witnesses without legacy callbacks.
     * @param directory external compiler workspace
     * @throws Exception for fixture failures */
    @Test void bytecodeReportsRetainPrecisionAndLocations(@TempDir Path directory) throws Exception {
        var classes=BytecodeFixtures.compile(directory,26,"""
            @Model public class Fixture {public Object a=new Object(),b=new Object();public Fixture(){}
              @Actor(id=0)public void first(){synchronized(a){synchronized(b){}}}
              @Actor(id=1)public void second(){synchronized(b){synchronized(a){}}}}
            """);
        var program=BytecodeProgramLoader.load(classes,"Fixture");
        var rows=new dev.samhb.interleave.report.BenchmarkHarness().runBytecode(program);
        assertEquals(1,rows.size());assertEquals("VIOLATION",rows.getFirst().verdict());
        assertThrows(IllegalArgumentException.class,()->rows.getFirst().withExploration(null,
            new dev.samhb.interleave.search.DfsResult(java.util.Map.of(),java.util.List.of(),0)));
        var writer=new dev.samhb.interleave.report.ReportWriter(rows);
        var json=com.google.gson.JsonParser.parseString(writer.writeJson()).getAsJsonObject();
        assertTrue(json.get("soundness").getAsBoolean());
        var row=json.getAsJsonArray("benchmarks").get(0).getAsJsonObject();
        assertEquals("BYTECODE",row.get("programKind").getAsString());
        assertEquals("EXACT",row.get("storeType").getAsString());
        var trace=dev.samhb.interleave.TraceRecord.fromJson(row.get("failingTrace").toString());
        assertEquals("DEADLOCK",trace.bytecodeEvidence().terminal().kind());
        assertEquals(2,trace.bytecodeEvidence().terminal().waits().size());
        var waits=trace.bytecodeEvidence().terminal().waits();
        assertTrue(waits.stream().anyMatch(w->w.actor()==0 && w.owner()==1 && w.monitor().equals("setup:2") && w.depth()==1));
        assertTrue(waits.stream().anyMatch(w->w.actor()==1 && w.owner()==0 && w.monitor().equals("setup:1") && w.depth()==1));
        var heap=trace.bytecodeEvidence().snapshots().getLast().heap();
        assertTrue(heap.stream().anyMatch(object->object.identity().equals("setup:1") && object.type().equals("java/lang/Object") && object.owner()==0 && object.depth()==1));
        assertTrue(heap.stream().anyMatch(object->object.identity().equals("setup:2") && object.owner()==1 && object.depth()==1));

        assertEquals(trace.states(),new dev.samhb.interleave.search.TraceReplayer().replayBytecode(program,trace).states());
        assertTrue(writer.writeMarkdown().contains(program.artifactDigest()));
    }
    /** Actual CLI selects BYTECODE explicitly and refuses incompatible modes before loading.
     * @param directory external compiler workspace
     * @throws Exception for fixture/process failures */
    @Test void bytecodeCliRejectsUnsupportedSelections(@TempDir Path directory) throws Exception {
        var classes=BytecodeFixtures.compile(directory,17,"""
            @Model public class Fixture {public int x;public Fixture(){}
              @Actor(id=0)public void actor(){x=1;} @Check public boolean valid(){return x==0;}}
            """);
        var good=cli(directory,"--bytecode","Fixture","--class-path",classes.toString(),"--json","--strategy","DFS","--store","exact");
        assertEquals(0,good.getKey(),good.getValue());
        var report=com.google.gson.JsonParser.parseString(good.getValue()).getAsJsonObject();
        assertEquals("BYTECODE",report.getAsJsonArray("benchmarks").get(0).getAsJsonObject().get("programKind").getAsString());
        for(var extra:java.util.List.of(new String[]{"--strategy","DPOR"},new String[]{"--store","bitstate"},
                new String[]{"--symmetry"},new String[]{"--simplify-cutoff"},new String[]{"--all"},
                new String[]{"--bytecode","Other"},new String[]{"--max-preemptions","0"})) {
            var args=new java.util.ArrayList<String>(java.util.List.of("--bytecode","Fixture","--class-path",classes.toString()));
            args.addAll(java.util.List.of(extra));var bad=cli(directory,args.toArray(String[]::new));
            assertEquals(1,bad.getKey(),bad.getValue());assertTrue(bad.getValue().contains("BYTECODE"),bad.getValue());
        }
        for(var extra:java.util.List.of(new String[]{"--strategy","DPOR"},new String[]{"--store","bitstate"})) {
            var args=new java.util.ArrayList<String>(java.util.List.of("--bytecode","Fixture","--class-path",directory.resolve("missing").toString()));
            args.addAll(java.util.List.of(extra));var bad=cli(directory,args.toArray(String[]::new));
            assertEquals(1,bad.getKey(),bad.getValue());
            assertTrue(bad.getValue().contains("BYTECODE currently supports only --strategy DFS --store exact"),bad.getValue());
        }
        assertEquals(1,cli(directory,"--bytecode","Fixture").getKey());
        assertEquals(1,cli(directory,"--class-path",classes.toString()).getKey());
    }

    /** Runs the public CLI in a separate JVM with bounded time and file-backed output.
     * @param directory process workspace
     * @param arguments CLI flags
     * @return status and merged output
     * @throws Exception for process/filesystem failures */
    private static java.util.Map.Entry<Integer,String> cli(Path directory,String... arguments) throws Exception {
        var command=new java.util.ArrayList<String>(java.util.List.of(Path.of(System.getProperty("java.home"),"bin","java").toString(),
            "-cp",BytecodeFixtures.classpath(dev.samhb.interleave.cli.Main.class,com.google.gson.Gson.class),"dev.samhb.interleave.cli.Main"));
        command.addAll(java.util.List.of(arguments));var output=java.nio.file.Files.createTempFile(directory,"cli-",".log");
        var process=new ProcessBuilder(command).redirectErrorStream(true).redirectOutput(output.toFile()).start();
        try {assertTrue(process.waitFor(15,java.util.concurrent.TimeUnit.SECONDS));return java.util.Map.entry(process.exitValue(),java.nio.file.Files.readString(output));}
        finally {if(process.isAlive())process.destroyForcibly();}
    }
    /** Produces fresh Java artifacts consumed by normalization and real browser CI.
     * @param directory compiler workspace
     * @throws Exception for fixture failures */
    @Test void bytecodeViewerUsesFreshProducerArtifacts(@TempDir Path directory) throws Exception {
        var cases=java.util.Map.of(
            "lost-update", "public int x;@Actor(id=0)public void a(){x++;}@Actor(id=1)public void b(){x++;}@Check public boolean ok(){return x==2;}",
            "deadlock", "public Object a=new Object(),b=new Object();@Actor(id=0)public void a(){synchronized(a){synchronized(b){}}}@Actor(id=1)public void b(){synchronized(b){synchronized(a){}}}",
            "fault", "public int x,z;@Actor(id=0)public void a(){x=7;x=1/z;}",
            "root", "@Actor(id=0)public void a(){}@Check(when=Check.When.ALWAYS)public boolean ok(){return false;}",
            "cutoff", "@Actor(id=0)public void a(){while(true){}}",
            "observer", "public int z;@Actor(id=0)public void a(){}@Check(when=Check.When.ALWAYS)public boolean ok(){return 1/z==0;}",
            "no-line", "public int x;@Actor(id=0)public void a(){x=1;}",
            "long-label", "public int "+"x".repeat(600)+";@Actor(id=0)public void a(){"+"x".repeat(600)+"=1;}");
        java.nio.file.Files.createDirectories(Path.of("build"));
        for(var entry:cases.entrySet()) {
            var classes=BytecodeFixtures.compile(directory.resolve(entry.getKey()),17,
                "@Model public class Fixture {public Fixture(){}"+entry.getValue()+"}",!entry.getKey().equals("no-line"));
            var program=BytecodeProgramLoader.load(classes,"Fixture");var run=InterleaveRunner.builder().build().runBytecode(program);
            java.nio.file.Files.writeString(Path.of("build/bytecode-"+entry.getKey()+".json"),run.toJson());
            if(entry.getKey().equals("lost-update")) {
                java.nio.file.Files.writeString(Path.of("build/bytecode-report.json"),new dev.samhb.interleave.report.ReportWriter(new dev.samhb.interleave.report.BenchmarkHarness().runBytecode(program)).writeJson());
                java.nio.file.Files.writeString(Path.of("build/bytecode-interrupted.json"),InterleaveRunner.builder().maxStates(1).build().runBytecode(program).toJson());
                java.nio.file.Files.writeString(Path.of("build/bytecode-cli.json"),cli(directory,"--bytecode","Fixture","--class-path",classes.toString(),"--json").getValue());
            }
            if(entry.getKey().equals("no-line"))assertTrue(run.completedTraces().getFirst().bytecodeEvidence().transitions().stream().flatMap(t->t.operations().stream()).allMatch(op->op.line()==null));
        }
    }
    /** An independently compiled consumer uses only the built jar's public library interfaces.
     * @param directory independent compiler/process workspace
     * @throws Exception for fixture/process failures */
    @Test void publicBytecodeLoaderAndRunnerWorkFromJar(@TempDir Path directory) throws Exception {
        var jar=Path.of(java.util.Objects.requireNonNull(System.getProperty("interleave.releaseJar"),"Gradle must supply the current library jar"));
        assertTrue(java.nio.file.Files.isRegularFile(jar));assertFalse(jar.toString().endsWith("-mutation-harness.jar"));
        var classes=directory.resolve("classes");java.nio.file.Files.createDirectories(classes);
        var compiler=javax.tools.ToolProvider.getSystemJavaCompiler();
        assertEquals(0,compiler.run(null,null,null,"--release","17","-g","-cp",jar.toString(),"-d",classes.toString(),
            "examples/bytecode/LostUpdate.java","examples/bytecode/TwoLockDeadlock.java"));
        for(var name:java.util.List.of("LostUpdate","TwoLockDeadlock")) {
            var cliOutput=directory.resolve(name+"-cli.json");
            var cliProcess=new ProcessBuilder(Path.of(System.getProperty("java.home"),"bin","java").toString(),"-cp",
                jar+java.io.File.pathSeparator+BytecodeFixtures.classpath(com.google.gson.Gson.class),"dev.samhb.interleave.cli.Main",
                "--bytecode","examples.bytecode."+name,"--class-path",classes.toString(),"--store","exact","--strategy","DFS","--json")
                .redirectErrorStream(true).redirectOutput(cliOutput.toFile()).start();
            try {
                assertTrue(cliProcess.waitFor(15,java.util.concurrent.TimeUnit.SECONDS));var text=java.nio.file.Files.readString(cliOutput);
                assertEquals(0,cliProcess.exitValue(),text);var report=com.google.gson.JsonParser.parseString(text).getAsJsonObject();
                assertTrue(report.get("soundness").getAsBoolean());
                var row=report.getAsJsonArray("benchmarks").get(0).getAsJsonObject();assertEquals("VIOLATION",row.get("verdict").getAsString());
                assertEquals(name.equals("LostUpdate")?"CHECK_FALSE":"DEADLOCK",row.getAsJsonObject("failingTrace").getAsJsonObject("bytecodeEvidence").getAsJsonObject("terminal").get("kind").getAsString());
            } finally {if(cliProcess.isAlive())cliProcess.destroyForcibly();}
        }
        var source=directory.resolve("Consumer.java");
        java.nio.file.Files.writeString(source,"""
            import dev.samhb.interleave.*;
            import dev.samhb.interleave.format.BytecodeProgramLoader;
            import dev.samhb.interleave.search.TraceReplayer;
            import java.nio.file.Path;
            public class Consumer {public static void main(String[] args)throws Exception {
              var runner=InterleaveRunner.builder().build();var replay=new TraceReplayer();
              for(String name:new String[]{"LostUpdate","TwoLockDeadlock"}) {
                var program=BytecodeProgramLoader.load(Path.of(args[0]),"examples.bytecode."+name);
                var result=runner.runBytecode(program);
                var traces=name.equals("LostUpdate")?result.failingTraces():result.deadlockedTraces();
                if(traces.isEmpty() || result.hasIncomplete())throw new AssertionError(name);
                var record=TraceRecord.fromJson(traces.getFirst().toJson());
                if(!replay.replayBytecode(program,record).states().equals(record.states()))throw new AssertionError("replay");
                var bytes=new java.io.ByteArrayOutputStream();
                try(var out=new java.io.ObjectOutputStream(bytes)){out.writeObject(result);}
                try(var in=new java.io.ObjectInputStream(new java.io.ByteArrayInputStream(bytes.toByteArray()))) {
                  if(!((TestResult)in.readObject()).bytecodeIdentity().equals(result.bytecodeIdentity()))throw new AssertionError("native");
                }
                System.out.println(name+":"+record.bytecodeEvidence().terminal().kind());
              }
            }}
            """);
        var consumer=directory.resolve("consumer");java.nio.file.Files.createDirectories(consumer);
        assertEquals(0,compiler.run(null,null,null,"-cp",jar.toString(),"-d",consumer.toString(),source.toString()));
        var output=directory.resolve("consumer.log");
        var process=new ProcessBuilder(Path.of(System.getProperty("java.home"),"bin","java").toString(),"-cp",
            consumer+java.io.File.pathSeparator+jar+java.io.File.pathSeparator+BytecodeFixtures.classpath(com.google.gson.Gson.class),
            "Consumer",classes.toString()).redirectErrorStream(true).redirectOutput(output.toFile()).start();
        try {
            assertTrue(process.waitFor(15,java.util.concurrent.TimeUnit.SECONDS));var text=java.nio.file.Files.readString(output);
            assertEquals(0,process.exitValue(),text);assertEquals(java.util.List.of("LostUpdate:CHECK_FALSE","TwoLockDeadlock:DEADLOCK"),text.lines().toList());
        } finally {if(process.isAlive())process.destroyForcibly();}
    }
    /** Unsupported routes reject without invoking caller callbacks or changing legacy settings.
     * @param directory compiler workspace
     * @throws Exception for fixture failures */
    @Test void bytecodeRunnerRejectsForeignCallbacksAndStrategies(@TempDir Path directory) throws Exception {
        var program=BytecodeProgramLoader.load(BytecodeFixtures.compile(directory,17,
            "@Model public class Fixture {public Fixture(){}@Actor(id=0)public void a(){}}"),"Fixture");
        for(var strategy:dev.samhb.interleave.Strategy.values())if(strategy!=dev.samhb.interleave.Strategy.DFS)
            assertThrows(IllegalArgumentException.class,()->InterleaveRunner.builder().strategy(strategy).build().runBytecode(program));
        assertThrows(IllegalArgumentException.class,()->InterleaveRunner.builder().invariant((state,config)->{throw new AssertionError("foreign property invoked");}).build().runBytecode(program));
        assertThrows(IllegalArgumentException.class,()->InterleaveRunner.builder().stateStoreFactory(()->{throw new AssertionError("foreign store invoked");}).build().runBytecode(program));
        assertThrows(IllegalArgumentException.class,()->InterleaveRunner.builder().symmetryReduction(true).build().runBytecode(program));
        assertThrows(IllegalArgumentException.class,()->new dev.samhb.interleave.report.BenchmarkHarness(100,1,java.util.Set.of(dev.samhb.interleave.report.StoreType.BITSTATE),null).runBytecode(program));
        var timed=InterleaveRunner.builder().maxTime(java.time.Duration.ZERO).build().runBytecode(program);
        assertTrue(timed.limitExceeded());assertTrue(timed.hasIncomplete());
        assertEquals(dev.samhb.interleave.core.ResourceLimitReason.MAX_TIME,timed.resourceLimitReason());
        assertThrows(IllegalArgumentException.class,()->BytecodeRuntime.run(program,-1,null));

        assertEquals("MAX_TIME",timed.incompleteTraces().getFirst().bytecodeEvidence().terminal().interruption());
        assertEquals("INCOMPLETE",com.google.gson.JsonParser.parseString(timed.toJson()).getAsJsonObject().get("verdict").getAsString());
    }
    /** Real compiler line tables identify distinct writes by method, descriptor and BCI.
     * @param directory compiler workspace
     * @throws Exception for fixture failures */
    @Test void bytecodeLocationsUseActualCompilerLines(@TempDir Path directory) throws Exception {
        var classes=BytecodeFixtures.compile(directory,17,"""
            @Model public class Fixture {public int x;public Fixture(){}
              @Actor(id=0)public void a(){
                x=1;
                x=2;
              }
            }
            """);
        var program=BytecodeProgramLoader.load(classes,"Fixture");
        var trace=InterleaveRunner.builder().build().runBytecode(program).completedTraces().getFirst();
        var writes=trace.bytecodeEvidence().transitions().stream().flatMap(t->t.operations().stream()).filter(op->"PUTFIELD".equals(op.opcode())).toList();
        assertEquals(2,writes.size());assertEquals(java.util.List.of(2,7),writes.stream().map(op->op.location().bci()).toList());
        assertEquals(java.util.List.of(4,5),writes.stream().map(op->op.line()).toList());
        assertTrue(writes.stream().allMatch(op->op.location().owner().equals("Fixture") && op.location().name().equals("a") && op.location().descriptor().equals("()V")));
        assertNull(trace.evidence());assertFalse(trace.toJson().contains("sourceDigest"));
    }
    /** Reporting preserves valid class-file UTF-16 field names through UTF-8 files and strict replay.
     * @param directory compiler and file workspace
     * @throws Exception for fixture failures */
    @Test void bytecodeReportsPreserveIsolatedUtf16Names(@TempDir Path directory) throws Exception {
        var classes=BytecodeFixtures.compile(directory,17,"@Model public class Fixture {public int field;public Fixture(){}@Actor(id=0)public void a(){}}");
        var file=classes.resolve("Fixture.class");var parser=java.lang.classfile.ClassFile.of();String name="field"+(char)0xd800;
        var bytes=parser.transformClass(parser.parse(java.nio.file.Files.readAllBytes(file)),(builder,element)->{
            if(element instanceof java.lang.classfile.FieldModel field && field.fieldName().equalsString("field"))
                builder.withField(name,java.lang.constant.ConstantDescs.CD_int,java.lang.classfile.ClassFile.ACC_PUBLIC);
            else builder.with(element);
        });
        java.nio.file.Files.write(file,bytes);var program=BytecodeProgramLoader.load(classes,"Fixture");
        var writer=new dev.samhb.interleave.report.ReportWriter(new dev.samhb.interleave.report.BenchmarkHarness().runBytecode(program));
        var reportFile=directory.resolve("report.json");java.nio.file.Files.write(reportFile,writer.writeJson().getBytes(java.nio.charset.StandardCharsets.UTF_8));
        var row=com.google.gson.JsonParser.parseString(java.nio.file.Files.readString(reportFile)).getAsJsonObject().getAsJsonArray("benchmarks").get(0).getAsJsonObject();
        var trace=dev.samhb.interleave.TraceRecord.fromJson(row.getAsJsonArray("completedTraces").get(0).toString());
        assertTrue(trace.states().getFirst().fields().containsKey("setup:0.Fixture."+name+":I"));
        assertEquals(trace.states(),new dev.samhb.interleave.search.TraceReplayer().replayBytecode(program,trace).states());
    }
}
