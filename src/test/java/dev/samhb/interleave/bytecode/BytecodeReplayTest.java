/** Strict public replay adversaries and abnormal-prefix evidence for the bounded bytecode profile. */
package dev.samhb.interleave.bytecode;

import dev.samhb.interleave.*;
import dev.samhb.interleave.format.BytecodeProgramLoader;
import dev.samhb.interleave.search.TraceReplayer;
import dev.samhb.interleave.search.CorruptedTraceException;
import dev.samhb.interleave.core.IllegalScheduleException;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

class BytecodeReplayTest {
    /** Fuel exhaustion during compiler cleanup is incomplete while retaining the original pending fault.
     * @param directory external compiler workspace
     * @throws Exception for fixture failures */
    @Test void cleanupFuelCutoffRetainsFaultAndHeldMonitor(@TempDir Path directory) throws Exception {
        var classes=BytecodeFixtures.compile(directory,26,"""
            @Model public class Fixture {public int x;public int zero;public Object lock=new Object();public Fixture(){}
              @Actor(id=0)public void actor(){synchronized(lock){x=1/zero;}}}
            """);
        var limits=new BytecodeLimits(11,128,8192,32,64,256,4096,128,8192,1024);
        var program=BytecodeProgramLoader.load(classes,"Fixture",limits);var result=InterleaveRunner.builder().build().runBytecode(program);
        assertFalse(result.hasViolation());assertTrue(result.hasIncomplete());assertFalse(result.limitExceeded());
        var trace=result.incompleteTraces().getFirst();var terminal=trace.bytecodeEvidence().terminal();
        assertEquals("CUTOFF",terminal.kind());assertEquals(11,terminal.cutoff().limit());
        assertNotNull(terminal.fault());assertEquals("java/lang/ArithmeticException",terminal.fault().type());
        assertTrue(trace.bytecodeEvidence().snapshots().getLast().heap().stream().anyMatch(object->object.owner()==0 && object.depth()==1));
        var replay=new TraceReplayer().replayBytecode(program,trace);assertEquals(terminal,replay.bytecodeEvidence().replayObservation());
    }    /** JSON returns typed immutable BYTECODE evidence that passes the same strict replayer.
     * @param directory external compiler workspace
     * @throws Exception for fixture failures */
    @Test void bytecodeJsonRoundTripsStrictReplay(@TempDir Path directory) throws Exception {
        var classes=BytecodeFixtures.compile(directory,26,"""
            @Model public class Fixture {public int x;public Fixture(){}
              @Actor(id=0)public void actor(){x=7;int zero=0;x=1/zero;}}
            """);
        var program=BytecodeProgramLoader.load(classes,"Fixture");var result=InterleaveRunner.builder().build().runBytecode(program);
        var trace=result.failingTraces().getFirst();var decoded=TraceRecord.fromJson(trace.toJson());
        assertEquals(trace.bytecodeEvidence(),decoded.bytecodeEvidence());assertEquals(trace.states(),decoded.states());
        assertEquals("int:7",decoded.states().getLast().fields().get("setup:0.Fixture.x:I"));
        assertEquals("UNCAUGHT_EXCEPTION",decoded.bytecodeEvidence().terminal().kind());
        assertEquals(dev.samhb.interleave.core.StepOutcome.ASSERTION_FAILED,decoded.outcomes().getLast());

        var replay=new TraceReplayer().replayBytecode(program,decoded);assertEquals(trace.states(),replay.states());
    }

    /** Root checks and global stops replay their actual prefix without inventing completion.
     * @param directory compiler workspace
     * @throws Exception for fixture failures */
    @Test void rootFailuresAndExternalStopsRetainOriginalMeaning(@TempDir Path directory) throws Exception {
        var classes=BytecodeFixtures.compile(directory,26,"""
            @Model public class Fixture {public int x;public Fixture(){}
              @Actor(id=0)public void actor(){x=1;}
              @Check(when=Check.When.ALWAYS)public boolean valid(){return x<0;}}
            """);
        var program=BytecodeProgramLoader.load(classes,"Fixture");
        var root=InterleaveRunner.builder().build().runBytecode(program).failingTraces().getFirst();
        assertTrue(root.threadIds().isEmpty());assertEquals("ALWAYS",root.bytecodeEvidence().identity().checks().getFirst().when());

        assertEquals("CHECK_FALSE",new TraceReplayer().replayBytecode(program,TraceRecord.fromJson(root.toJson())).bytecodeEvidence().terminal().kind());
        var classes2=BytecodeFixtures.compile(directory.resolve("normal"),26,"""
            @Model public class Fixture {public int x;public Fixture(){} @Actor(id=0)public void actor(){x=1;}}
            """);
        var normal=BytecodeProgramLoader.load(classes2,"Fixture");
        var stopped=InterleaveRunner.builder().maxStates(1).build().runBytecode(normal);
        assertTrue(stopped.limitExceeded());assertTrue(stopped.hasIncomplete());
        var prefix=stopped.incompleteTraces().getFirst();
        var replay=new TraceReplayer().replayBytecode(normal,TraceRecord.fromJson(prefix.toJson()));
        assertEquals(dev.samhb.interleave.search.TraceOutcome.INCOMPLETE,replay.outcome());
        assertEquals("INTERRUPTED",replay.bytecodeEvidence().terminal().kind());
        assertNotNull(replay.bytecodeEvidence().replayObservation());
        assertEquals(dev.samhb.interleave.core.ResourceLimitReason.MAX_STATES,stopped.resourceLimitReason());
        var upgraded=new TraceRecord(prefix.threadIds(),prefix.outcomes(),dev.samhb.interleave.search.TraceOutcome.COMPLETED,
            prefix.programHash(),prefix.states(),null,prefix.bytecodeEvidence());
        assertThrows(dev.samhb.interleave.search.CorruptedTraceException.class,()->new TraceReplayer().replayBytecode(normal,upgraded));
        var d=prefix.bytecodeEvidence();
        var forgedObservation=new dev.samhb.interleave.core.BytecodeEvidence(2,d.identity(),d.transitions(),d.snapshots(),d.terminal(),
            new dev.samhb.interleave.core.BytecodeEvidence.Terminal("COMPLETED",-1,null,null,null,java.util.List.of(),null));
        var changed=new TraceRecord(prefix.threadIds(),prefix.outcomes(),prefix.outcome(),prefix.programHash(),prefix.states(),null,forgedObservation);
        assertThrows(dev.samhb.interleave.search.CorruptedTraceException.class,()->new TraceReplayer().replayBytecode(normal,changed));
        assertEquals(replay.bytecodeEvidence(),new TraceReplayer().replayBytecode(normal,replay).bytecodeEvidence());

        assertThrows(dev.samhb.interleave.search.CorruptedTraceException.class,()->new TraceReplayer().replayBytecode(normal,root));
        Thread.currentThread().interrupt();
        try {
            var cancelled=InterleaveRunner.builder().build().runBytecode(normal);
            assertTrue(Thread.currentThread().isInterrupted());assertTrue(cancelled.hasIncomplete());
            assertEquals("CANCELLED",cancelled.incompleteTraces().getFirst().bytecodeEvidence().terminal().interruption());
            assertThrows(java.util.concurrent.CancellationException.class,()->new TraceReplayer().replayBytecode(normal,prefix));
        } finally {Thread.interrupted();}
    }

    /** Mutating authenticated operations, identity or terminal/snapshot data cannot replay.
     * @param directory compiler workspace
     * @throws Exception for fixture failures */
    @Test void tamperedEvidenceAndMalformedJsonReject(@TempDir Path directory) throws Exception {
        var classes=BytecodeFixtures.compile(directory,26,"""
            @Model public class Fixture {public int x;public Fixture(){} @Actor(id=0)public void actor(){x=1;}}
            """);
        var program=BytecodeProgramLoader.load(classes,"Fixture");
        var trace=InterleaveRunner.builder().build().runBytecode(program).completedTraces().getFirst();
        var original=com.google.gson.JsonParser.parseString(trace.toJson()).getAsJsonObject();
        var foreign=new java.util.ArrayList<>(trace.threadIds());foreign.set(0,-1);
        var badChoice=new TraceRecord(foreign,trace.outcomes(),trace.outcome(),trace.programHash(),trace.states(),null,trace.bytecodeEvidence());
        assertThrows(dev.samhb.interleave.core.IllegalScheduleException.class,()->new TraceReplayer().replayBytecode(program,badChoice));
        assertThrows(dev.samhb.interleave.search.CorruptedTraceException.class,()->new TraceReplayer().replayBytecode(program,
            new TraceRecord(java.util.List.of(),java.util.List.of(),dev.samhb.interleave.search.TraceOutcome.COMPLETED,null)));

        java.util.List<Forgery> mutations=java.util.List.of(
            new Forgery(CorruptedTraceException.class,j->j.getAsJsonObject("bytecodeEvidence").getAsJsonObject("identity").addProperty("modelClass","Other")),
            new Forgery(IllegalArgumentException.class,j->j.getAsJsonObject("bytecodeEvidence").getAsJsonObject("identity").addProperty("artifactDigest","0".repeat(64))),
            new Forgery(CorruptedTraceException.class,j->j.getAsJsonObject("bytecodeEvidence").getAsJsonObject("identity").getAsJsonObject("limits").addProperty("actorInstructions",257)),
            new Forgery(CorruptedTraceException.class,j->j.getAsJsonObject("bytecodeEvidence").getAsJsonObject("identity").addProperty("mode","REFERENCE")),
            new Forgery(CorruptedTraceException.class,j->j.getAsJsonObject("bytecodeEvidence").getAsJsonArray("transitions").get(0).getAsJsonObject().addProperty("actor",1)),
            new Forgery(IllegalScheduleException.class,j->j.getAsJsonArray("threads").set(0,new com.google.gson.JsonPrimitive(1))),
            new Forgery(CorruptedTraceException.class,j->j.getAsJsonArray("outcomes").set(0,new com.google.gson.JsonPrimitive("TERMINATED"))),
            new Forgery(CorruptedTraceException.class,j->j.getAsJsonObject("bytecodeEvidence").getAsJsonArray("snapshots").get(0).getAsJsonObject().addProperty("stateDigest","0".repeat(64))),
            new Forgery(CorruptedTraceException.class,j->j.getAsJsonObject("bytecodeEvidence").getAsJsonArray("transitions").get(0).getAsJsonObject().getAsJsonArray("operations").get(0).getAsJsonObject().getAsJsonObject("location").addProperty("bci",999)),
            new Forgery(CorruptedTraceException.class,j->j.addProperty("outcome","INCOMPLETE")),
            new Forgery(IllegalArgumentException.class,j->j.addProperty("sourceDigest","0".repeat(64))),
            new Forgery(IllegalArgumentException.class,j->j.getAsJsonObject("bytecodeEvidence").getAsJsonObject("terminal").addProperty("kind","PREFIX")));
        for(var forgery:mutations) {
            var changed=original.deepCopy();forgery.mutation().accept(changed);
            if(forgery.failure()==IllegalArgumentException.class) {
                assertThrows(IllegalArgumentException.class,()->TraceRecord.fromJson(changed.toString()),changed::toString);
            } else {
                var decoded=TraceRecord.fromJson(changed.toString());
                assertThrows(forgery.failure(),()->new TraceReplayer().replayBytecode(program,decoded),changed::toString);
            }
        }
        assertThrows(IllegalArgumentException.class,()->TraceRecord.fromJson(trace.toJson().replaceFirst("\\{","{\"threads\":[],")));
        assertThrows(IllegalArgumentException.class,()->TraceRecord.fromJson("[".repeat(70)+"0"+"]".repeat(70)));
        assertThrows(IllegalArgumentException.class,()->TraceRecord.fromJson("[".repeat(10000)+"0"+"]".repeat(10000)));
        assertThrows(IllegalArgumentException.class,()->TraceRecord.fromJson(trace.toJson()+"{}"));
        assertThrows(IllegalArgumentException.class,()->TraceRecord.fromJson(trace.toJson().replace("\"Fixture\"","\"Fix"+(char)10+"ture\"")));

        assertThrows(IllegalArgumentException.class,()->TraceRecord.fromJson(trace.toJson().replace("\"INT\"","\"UNKNOWN\"")));
    }
    /** Reserved wide tags and isolated UTF-16 code units survive transport without numeric coercion.
     * This checks the wire format, not admission of later-phase primitive execution.
     * @param directory compiler workspace
     * @throws Exception for fixture failures */
    @Test void wireTypesAreLosslessAndDeeplyImmutable(@TempDir Path directory) throws Exception {
        var classes=BytecodeFixtures.compile(directory,17,"@Model public class Fixture {public Fixture(){}@Actor(id=0)public void a(){}}");
        var program=BytecodeProgramLoader.load(classes,"Fixture");var original=InterleaveRunner.builder().build().runBytecode(program).completedTraces().getFirst();
        var data=original.bytecodeEvidence();var snapshots=new java.util.ArrayList<>(data.snapshots());var first=snapshots.getFirst();
        var fields=new java.util.HashMap<>(first.fields());
        fields.put("wide",new dev.samhb.interleave.core.BytecodeEvidence.Value("LONG","9223372036854775807"));
        fields.put("float",new dev.samhb.interleave.core.BytecodeEvidence.Value("FLOAT_BITS","80000000"));
        fields.put("double",new dev.samhb.interleave.core.BytecodeEvidence.Value("DOUBLE_BITS","7ff8000000000001"));
        String isolated="unicode:"+(char)0xd800+":"+(char)0xdc00;
        fields.put("text",new dev.samhb.interleave.core.BytecodeEvidence.Value("TEXT",isolated));
        snapshots.set(0,new dev.samhb.interleave.core.BytecodeEvidence.Snapshot(first.stateDigest(),fields,first.locals(),first.heap()));
        var payload=new dev.samhb.interleave.core.BytecodeEvidence(2,data.identity(),data.transitions(),snapshots,data.terminal(),null);
        var record=new TraceRecord(original.threadIds(),original.outcomes(),original.outcome(),original.programHash(),snapshots.stream().map(BytecodeSnapshots::display).toList(),null,payload);
        var bytes=record.toJson().getBytes(java.nio.charset.StandardCharsets.UTF_8);
        var decoded=TraceRecord.fromJson(new String(bytes,java.nio.charset.StandardCharsets.UTF_8));
        assertEquals(payload,decoded.bytecodeEvidence());assertEquals(isolated,decoded.bytecodeEvidence().snapshots().getFirst().fields().get("text").raw());
        assertThrows(UnsupportedOperationException.class,()->decoded.bytecodeEvidence().snapshots().getFirst().fields().clear());
        assertThrows(UnsupportedOperationException.class,()->decoded.states().getFirst().fields().clear());
        assertThrows(UnsupportedOperationException.class,()->decoded.bytecodeEvidence().snapshots().getFirst().locals().clear());
        assertThrows(UnsupportedOperationException.class,()->decoded.bytecodeEvidence().snapshots().getFirst().locals().get("0").clear());
        assertThrows(UnsupportedOperationException.class,()->decoded.states().getFirst().locals().get("0").clear());
        assertThrows(IllegalArgumentException.class,()->TraceRecord.fromJson(record.toJson().replace("\"9223372036854775807\"","9223372036854775807")));
        // Transporting an edited snapshot never authenticates it as actual target execution.
        assertThrows(dev.samhb.interleave.search.CorruptedTraceException.class,()->new TraceReplayer().replayBytecode(program,decoded));
    }
    /** Location-independent frozen bytes replay; changed debug bytes and selected models reject.
     * @param directory compiler workspace
     * @throws Exception for fixture/filesystem failures */
    @Test void replayUsesFrozenArtifactAndSelectedModel(@TempDir Path directory) throws Exception {
        String source="@Model public class Fixture {public int x;public Fixture(){}@Actor(id=0)public void a(){x=1;}}";
        var classes=BytecodeFixtures.compile(directory.resolve("debug"),17,source);
        var original=BytecodeProgramLoader.load(classes,"Fixture");var trace=InterleaveRunner.builder().build().runBytecode(original).completedTraces().getFirst();
        var copy=directory.resolve("copy");java.nio.file.Files.createDirectories(copy);
        try(var files=java.nio.file.Files.list(classes)){for(var file:files.toList())java.nio.file.Files.copy(file,copy.resolve(file.getFileName()));}
        var moved=BytecodeProgramLoader.load(copy,"Fixture");assertEquals(original.artifactDigest(),moved.artifactDigest());
        assertEquals(trace.states(),new TraceReplayer().replayBytecode(moved,trace).states());
        var noDebug=BytecodeProgramLoader.load(BytecodeFixtures.compile(directory.resolve("no-debug"),17,source,false),"Fixture");
        assertNotEquals(original.artifactDigest(),noDebug.artifactDigest());
        assertThrows(dev.samhb.interleave.search.CorruptedTraceException.class,()->new TraceReplayer().replayBytecode(noDebug,trace));
        java.nio.file.Files.delete(copy.resolve("Fixture.class"));
        assertEquals(trace.states(),new TraceReplayer().replayBytecode(moved,trace).states());
        var changed=BytecodeProgramLoader.load(classes,"Fixture",new BytecodeLimits(257,128,8192,32,64,256,4096,128,8192,1024));
        assertThrows(dev.samhb.interleave.search.CorruptedTraceException.class,()->new TraceReplayer().replayBytecode(changed,trace));
    }
    /** A matching digest cannot substitute another claimed model, even with no actor choices.
     * The initial loader rejects a reachable second annotated model; this attacks the imported
     * identity directly while retaining the exact original artifact digest.
     * @param directory compiler workspace
     * @throws Exception for fixture failures */
    @Test void sameArtifactDigestDoesNotAuthenticateForeignRootModel(@TempDir Path directory) throws Exception {
        var classes=BytecodeFixtures.compile(directory,17,"""
            @Model public class Fixture {public Fixture(){}
              @Actor(id=0)public void a(){}@Check(when=Check.When.ALWAYS)public boolean ok(){return false;}}
            """);
        var first=BytecodeProgramLoader.load(classes,"Fixture");
        for(var runner:java.util.List.of(InterleaveRunner.builder().build(),InterleaveRunner.builder().maxTime(java.time.Duration.ZERO).build())) {
            var result=runner.runBytecode(first);var root=result.hasViolation()?result.failingTraces().getFirst():result.incompleteTraces().getFirst();
            assertTrue(root.threadIds().isEmpty());
            var forged=com.google.gson.JsonParser.parseString(root.toJson()).getAsJsonObject();
            forged.getAsJsonObject("bytecodeEvidence").getAsJsonObject("identity").addProperty("modelClass","Other");
            var record=TraceRecord.fromJson(forged.toString());
            assertEquals(first.artifactDigest(),record.bytecodeEvidence().identity().artifactDigest());
            assertThrows(dev.samhb.interleave.search.CorruptedTraceException.class,()->new TraceReplayer().replayBytecode(first,record));
        }
    }

    /** Empty invented terminal events and extra choices cannot masquerade as completed executions.
     * @param directory compiler workspace
     * @throws Exception for fixture failures */
    @Test void fabricatedEmptyTerminalsAndTrailingChoicesReject(@TempDir Path directory) throws Exception {
        var program=BytecodeProgramLoader.load(BytecodeFixtures.compile(directory,17,
            "@Model public class Fixture {public Fixture(){}@Actor(id=0)public void a(){}}"),"Fixture");
        var trace=InterleaveRunner.builder().build().runBytecode(program).completedTraces().getFirst();var data=trace.bytecodeEvidence();
        for(var terminal:java.util.List.of(data.terminal(),new dev.samhb.interleave.core.BytecodeEvidence.Terminal("DEADLOCK",-1,null,null,null,
                java.util.List.of(new dev.samhb.interleave.core.BytecodeEvidence.Wait(0,0,"setup:0",1)),null))) {
            var fake=new dev.samhb.interleave.core.BytecodeEvidence(2,data.identity(),java.util.List.of(),java.util.List.of(data.snapshots().getFirst()),terminal,null);
            var record=new TraceRecord(java.util.List.of(),java.util.List.of(),terminal.kind().equals("DEADLOCK")?dev.samhb.interleave.search.TraceOutcome.DEADLOCK:trace.outcome(),trace.programHash(),java.util.List.of(trace.states().getFirst()),null,fake);
            assertThrows(dev.samhb.interleave.search.CorruptedTraceException.class,()->new TraceReplayer().replayBytecode(program,record));
        }
        var threads=new java.util.ArrayList<>(trace.threadIds());threads.add(0);
        var outcomes=new java.util.ArrayList<>(trace.outcomes());outcomes.add(trace.outcomes().getLast());
        var steps=new java.util.ArrayList<>(data.transitions());steps.add(data.transitions().getLast());
        var snapshots=new java.util.ArrayList<>(data.snapshots());snapshots.add(data.snapshots().getLast());
        var states=new java.util.ArrayList<>(trace.states());states.add(trace.states().getLast());
        var trailing=new TraceRecord(threads,outcomes,trace.outcome(),trace.programHash(),states,null,
            new dev.samhb.interleave.core.BytecodeEvidence(2,data.identity(),steps,snapshots,data.terminal(),null));
        assertThrows(dev.samhb.interleave.search.CorruptedTraceException.class,()->new TraceReplayer().replayBytecode(program,trailing));
    }

    /** Observer fuel and depth stops remain incomplete and replay their exact check/omitted location.
     * @param directory compiler workspace
     * @throws Exception for fixture failures */
    @Test void observerLimitsRemainIncompleteAndReplayable(@TempDir Path directory) throws Exception {
        for(var body:java.util.List.of("while(true){}","return recurse();")) {
            var classes=BytecodeFixtures.compile(directory.resolve(body.startsWith("while")?"fuel":"depth"),17,
                "@Model public class Fixture {public Fixture(){}@Actor(id=0)public void a(){}public static boolean recurse(){return recurse();}@Check(when=Check.When.ALWAYS)public boolean ok(){"+body+"}}");
            var limits=new BytecodeLimits(256,128,8192,4,64,256,4096,128,8192,32);
            var program=BytecodeProgramLoader.load(classes,"Fixture",limits);var result=InterleaveRunner.builder().build().runBytecode(program);
            assertFalse(result.hasViolation());assertTrue(result.hasIncomplete());assertFalse(result.limitExceeded());
            var trace=TraceRecord.fromJson(result.incompleteTraces().getFirst().toJson());
            assertEquals("OBSERVER_LIMIT",trace.bytecodeEvidence().terminal().kind());assertEquals("ok",trace.bytecodeEvidence().terminal().check().check().name());
            assertEquals(trace.bytecodeEvidence().terminal(),new TraceReplayer().replayBytecode(program,trace).bytecodeEvidence().replayObservation());
        }
    }
    /** The documented node bound rejects container growth before DTO construction.
     * A flat array exercises the bound independently of recursive depth or byte length. */
    @Test void jsonNodeBudgetBoundsContainerWork() {
        String oversized="["+"null,".repeat(999999)+"null]";
        var failure=assertThrows(IllegalArgumentException.class,()->TraceRecord.fromJson(oversized));
        assertNotNull(failure.getCause());assertTrue(failure.getCause().getMessage().contains("node limit"));
    }
    /** One independently classified import or replay forgery.
     * @param failure required public rejection category
     * @param mutation one changed wire property */
    private record Forgery(Class<? extends RuntimeException> failure,java.util.function.Consumer<com.google.gson.JsonObject> mutation) {}
}
