/** Independent scheduling and cross-allocator bounds for synchronized Throwable operations. */
package dev.samhb.interleave.bytecode;

import dev.samhb.interleave.InterleaveRunner;
import dev.samhb.interleave.format.BytecodeProgramLoader;
import java.nio.file.Path;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

class BytecodeExceptionConcurrencyTest {
    /** A trusted getter participates in a real receiver-monitor cycle rather than bypassing it.
     * @param directory compiler workspace
     * @throws Exception for fixture failures */
    @Test void synchronizedGetterParticipatesInDeadlock(@TempDir Path directory) throws Exception {
        var classes=BytecodeFixtures.compile(directory,17,"""
            @Model public class Fixture {public Throwable error=new Throwable();public Object lock=new Object();public Fixture(){}
              @Actor(id=0)public void first(){synchronized(error){synchronized(lock){}}}
              @Actor(id=1)public void second(){synchronized(lock){error.getSuppressed();}}}
            """);
        var program=BytecodeProgramLoader.load(classes,"Fixture");BytecodeBatchingOracleTest.compare(program);
        var result=InterleaveRunner.builder().build().runBytecode(program);
        assertFalse(result.hasIncomplete());assertFalse(result.deadlockedTraces().isEmpty());
        var trace=result.deadlockedTraces().getFirst();assertEquals(2,trace.bytecodeEvidence().terminal().waits().size());
        assertEquals(trace.bytecodeEvidence().terminal(),new dev.samhb.interleave.search.TraceReplayer().replayBytecode(program,trace).bytecodeEvidence().replayObservation());
    }

    /** Two receiver locks cannot reserve the same escaped allocator's last cell at entry time.
     * @param directory compiler workspace
     * @throws Exception for fixture failures */
    @Test void suppressionRechecksOriginalAllocatorAtBody(@TempDir Path directory) throws Exception {
        var classes=BytecodeFixtures.compile(directory,17,"""
            @Model public class Fixture {public Throwable first=new Throwable(),second=new Throwable(),child=new Throwable();public Fixture(){}
              @Actor(id=0)public void first(){first.addSuppressed(child);}
              @Actor(id=1)public void second(){second.addSuppressed(child);}}
            """);
        // Setup: fixture4 + class1 + three Throwables18 + String literals32+35 =90 cells.
        var limits=new BytecodeLimits(256,128,8192,32,64,256,4096,128,91,1024);
        var program=BytecodeProgramLoader.load(classes,"Fixture",limits);
        var result=InterleaveRunner.builder().build().runBytecode(program);
        assertFalse(result.hasViolation());assertTrue(result.hasIncomplete());
        java.nio.file.Files.writeString(Path.of("build/bytecode-exception-owner.json"),result.toJson());
        assertEquals(Set.of(0,1),result.incompleteTraces().stream().map(t->t.bytecodeEvidence().terminal().actor()).collect(Collectors.toSet()));
        for(var trace:result.incompleteTraces()) {
            var terminal=trace.bytecodeEvidence().terminal();var cutoff=terminal.cutoff();
            assertEquals("HEAP_CELLS",cutoff.reason());assertEquals(-1,cutoff.budgetOwner());assertEquals(91,cutoff.limit());
            assertEquals(1,cutoff.required());assertEquals(0,cutoff.remaining());
            assertEquals("int:91",trace.states().getLast().fields().get("@setup.heapCells"));
            assertTrue(trace.bytecodeEvidence().snapshots().getLast().heap().stream().anyMatch(o->o.owner()==terminal.actor() && o.depth()==1));
            var decoded=dev.samhb.interleave.TraceRecord.fromJson(trace.toJson());
            assertEquals(terminal,new dev.samhb.interleave.search.TraceReplayer().replayBytecode(program,decoded).bytecodeEvidence().replayObservation());
        }
    }
    /** Waiting suppression reads recompute current count and full fuel before monitor blocking.
     * @param directory compiler workspace
     * @throws Exception for fixture failures */
    @Test void blockedGetterRecomputesCountAndFuel(@TempDir Path directory) throws Exception {
        for(boolean tight:new boolean[]{false,true}) {
            String padding=tight ? "int a=0;"+"a++;".repeat(14) : "";
            var classes=BytecodeFixtures.compile(directory.resolve(Boolean.toString(tight)),17,"""
                @Model public class Fixture {public Throwable error=new Throwable(),child=new Throwable();public int size;public Fixture(){}
                  @Actor(id=0)public void first(){synchronized(error){error.addSuppressed(child);error.addSuppressed(child);}}
                  @Actor(id=1)public void second(){
                """+padding+"size=error.getSuppressed().length;}}");
            var limits=new BytecodeLimits(tight ? 21 : 256,128,8192,32,64,256,4096,128,8192,1024);
            var program=BytecodeProgramLoader.load(classes,"Fixture",limits);BytecodeBatchingOracleTest.compare(program);
            var result=InterleaveRunner.builder().build().runBytecode(program);
            if(tight) {
                assertFalse(result.hasViolation());assertTrue(result.hasIncomplete());
                var cutoff=result.incompleteTraces().stream().filter(t->t.bytecodeEvidence().terminal().cutoff().required()==3).findFirst().orElseThrow();
                assertEquals(2,cutoff.bytecodeEvidence().terminal().cutoff().remaining());
                assertEquals(1,cutoff.bytecodeEvidence().terminal().actor());
                assertTrue(cutoff.bytecodeEvidence().snapshots().getLast().heap().stream().noneMatch(o->o.owner()==1));
            } else {
                assertFalse(result.hasIncomplete());assertFalse(result.hasViolation());
                assertEquals(Set.of("int:0","int:2"),result.completedTraces().stream().map(t->
                    t.states().getLast().fields().get("setup:0.Fixture.size:I").toString()).collect(Collectors.toSet()));
                var counts=new java.util.HashSet<String>();
                for(var trace:result.completedTraces())for(var snapshot:trace.bytecodeEvidence().snapshots()) {
                    var actor=snapshot.locals().get("1");var method=actor.get("intrinsic.method");
                    if(method!=null && method.raw().contains("getSuppressed") && actor.get("phase").raw().equals("INTRINSIC_BODY")) {
                        assertNotNull(actor.get("intrinsic.count"));counts.add(actor.get("intrinsic.count").raw());
                    }
                }
                assertEquals(Set.of("0","2"),counts);
            }
        }
    }

    /** Escaped payload growth belongs to actor0 while its returned array belongs to actor1.
     * @param directory compiler workspace
     * @throws Exception for fixture failures */
    @Test void escapedActorPayloadKeepsAllocatorAndCallerSeparate(@TempDir Path directory) throws Exception {
        var classes=BytecodeFixtures.compile(directory,17,"""
            @Model public class Fixture {public Throwable error,child=new Throwable();public Throwable[] saved;public Fixture(){}
              @Actor(id=0)public void first(){error=new Throwable();}
              @Actor(id=1)public void second(){Throwable observed=error;if(observed!=null){observed.addSuppressed(child);saved=observed.getSuppressed();}}}
            """);
        var program=BytecodeProgramLoader.load(classes,"Fixture");BytecodeBatchingOracleTest.compare(program);
        var result=InterleaveRunner.builder().build().runBytecode(program);assertFalse(result.hasIncomplete());assertFalse(result.hasViolation());
        var escaped=result.completedTraces().stream().filter(t->t.bytecodeEvidence().snapshots().getLast().fields()
            .get("setup:0.Fixture.saved:[Ljava/lang/Throwable;").type().equals("REFERENCE")).findFirst().orElseThrow();
        var actors=escaped.bytecodeEvidence().snapshots().getLast().locals();
        assertEquals("7",actors.get("0").get("heapCells").raw());assertEquals("1",actors.get("0").get("allocations").raw());
        assertEquals("2",actors.get("1").get("heapCells").raw());assertEquals("1",actors.get("1").get("allocations").raw());
        var bounded=BytecodeProgramLoader.load(classes,"Fixture",new BytecodeLimits(256,128,6,32,64,256,4096,128,8192,1024));
        BytecodeBatchingOracleTest.compare(bounded);var stopped=InterleaveRunner.builder().build().runBytecode(bounded);
        assertFalse(stopped.hasViolation());assertFalse(stopped.completedTraces().isEmpty());assertTrue(stopped.hasIncomplete());
        java.nio.file.Files.writeString(Path.of("build/bytecode-exception-actor-owner.json"),stopped.toJson());
        for(var trace:stopped.incompleteTraces()) {
            var terminal=trace.bytecodeEvidence().terminal();assertEquals(1,terminal.actor());assertEquals(0,terminal.cutoff().budgetOwner());
            assertEquals("HEAP_CELLS",terminal.cutoff().reason());assertEquals(6,terminal.cutoff().limit());
            var altered=com.google.gson.JsonParser.parseString(trace.toJson()).getAsJsonObject();
            altered.getAsJsonObject("bytecodeEvidence").getAsJsonObject("terminal").getAsJsonObject("cutoff").addProperty("budgetOwner",1);
            var decoded=dev.samhb.interleave.TraceRecord.fromJson(altered.toString());
            assertThrows(dev.samhb.interleave.search.CorruptedTraceException.class,()->new dev.samhb.interleave.search.TraceReplayer().replayBytecode(bounded,decoded));
        }
    }

}
