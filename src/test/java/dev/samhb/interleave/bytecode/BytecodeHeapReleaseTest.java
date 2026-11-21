/** Public heap-profile evidence, strict replay, reports and lossless default-wide snapshots. */
package dev.samhb.interleave.bytecode;

import dev.samhb.interleave.*;
import dev.samhb.interleave.format.BytecodeProgramLoader;
import dev.samhb.interleave.search.TraceReplayer;
import dev.samhb.interleave.search.CorruptedTraceException;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

class BytecodeHeapReleaseTest {
    /** Published arrays/aliases, runtime faults and partial allocation cutoffs round-trip public consumers.
     * @param directory compiler workspace
     * @throws Exception for fixture/serialization failures */
    @Test void heapProfileReplaysAcrossConsumers(@TempDir Path directory) throws Exception {
        for(String body:java.util.List.of(
            "Object[] a=new Object[1];a[0]=a;saved=a;result=new int[1][0].length;",
            "saved=new double[1];Object[] a=new Fixture[1];a[0]=new Object();",
            "saved=new float[1];Object a=new int[1];Fixture f=(Fixture)a;",
            "saved=new long[1];Object a=new Object();Object b=new Object();")) {
            var work=directory.resolve("case"+body.hashCode());
            var classes=BytecodeFixtures.compile(work,26,"@Model public class Fixture {Object saved;int result;public Fixture(){}"
                +"@Actor(id=0)public void a(){"+body+"}}");
            var limits=new BytecodeLimits(256,body.contains("new long") ? 2 : 128,8192,32,64,256,4096,128,8192,1024);
            var program=BytecodeProgramLoader.load(classes,"Fixture",limits);
            var result=InterleaveRunner.builder().build().runBytecode(program);
            var traces=new java.util.ArrayList<TraceRecord>();traces.addAll(result.completedTraces());traces.addAll(result.failingTraces());traces.addAll(result.incompleteTraces());
            assertEquals(1,traces.size());
            var trace=traces.getFirst();var terminal=trace.bytecodeEvidence().terminal();
            if(body.contains("new double")){assertEquals("UNCAUGHT_EXCEPTION",terminal.kind());assertEquals("java/lang/ArrayStoreException",terminal.fault().type());}
            else if(body.contains("new float")){assertEquals("UNCAUGHT_EXCEPTION",terminal.kind());assertEquals("java/lang/ClassCastException",terminal.fault().type());}
            else assertEquals(body.contains("new long") ? "CUTOFF" : "COMPLETED",terminal.kind());
            var decoded=TraceRecord.fromJson(trace.toJson());
            assertEquals(trace.bytecodeEvidence(),decoded.bytecodeEvidence());
            assertEquals(trace.states(),new TraceReplayer().replayBytecode(program,decoded).states());
            assertEquals("BYTECODE-SC-2",decoded.bytecodeEvidence().identity().profileVersion());
            var last=trace.bytecodeEvidence().snapshots().getLast();assertTrue(last.heap().stream().anyMatch(o->o.identity().startsWith("actor:0:")));
            if(body.contains("new double"))assertTrue(last.fields().values().stream().anyMatch(v->v.type().equals("DOUBLE_BITS") && v.raw().equals("0000000000000000")));
            if(body.contains("new float"))assertTrue(last.fields().values().stream().anyMatch(v->v.type().equals("FLOAT_BITS") && v.raw().equals("00000000")));
            if(body.contains("new long")){assertTrue(result.hasIncomplete());assertEquals("ALLOCATIONS",trace.bytecodeEvidence().terminal().cutoff().reason());}
            var forged=com.google.gson.JsonParser.parseString(trace.toJson()).getAsJsonObject();
            var fields=forged.getAsJsonObject("bytecodeEvidence").getAsJsonArray("snapshots").get(1).getAsJsonObject().getAsJsonObject("fields");
            fields.addProperty("forged-array-element",7);
            assertThrows(IllegalArgumentException.class,()->TraceRecord.fromJson(forged.toString()));
            var changedFields=new java.util.HashMap<>(last.fields());
            String cell=changedFields.keySet().stream().filter(key->key.endsWith("[0]")).findFirst().orElseThrow();
            var value=changedFields.get(cell);String raw=switch(value.type()) {
                case "REFERENCE","NULL"->"setup:0";case "DOUBLE_BITS"->"0000000000000001";case "FLOAT_BITS"->"00000001";default->"1";
            };
            changedFields.put(cell,new dev.samhb.interleave.core.BytecodeEvidence.Value(value.type().equals("NULL") ? "REFERENCE" : value.type(),raw));
            var data=trace.bytecodeEvidence();var snapshots=new java.util.ArrayList<>(data.snapshots());
            snapshots.set(snapshots.size()-1,new dev.samhb.interleave.core.BytecodeEvidence.Snapshot(last.stateDigest(),changedFields,last.locals(),last.heap()));
            var forgedData=new dev.samhb.interleave.core.BytecodeEvidence(data.version(),data.identity(),data.transitions(),snapshots,data.terminal(),null);
            var forgedRecord=new TraceRecord(trace.threadIds(),trace.outcomes(),trace.outcome(),trace.programHash(),snapshots.stream().map(BytecodeSnapshots::display).toList(),null,forgedData);
            assertThrows(CorruptedTraceException.class,()->new TraceReplayer().replayBytecode(program,TraceRecord.fromJson(forgedRecord.toJson())));
            var bytes=new java.io.ByteArrayOutputStream();try(var out=new java.io.ObjectOutputStream(bytes)){out.writeObject(result);}
            try(var in=new java.io.ObjectInputStream(new java.io.ByteArrayInputStream(bytes.toByteArray()))) {
                var restored=(TestResult)in.readObject();assertEquals(result.bytecodeIdentity(),restored.bytecodeIdentity());
                assertEquals(result.completedTraces().size(),restored.completedTraces().size());assertEquals(result.failingTraces().size(),restored.failingTraces().size());
                assertEquals(result.incompleteTraces().size(),restored.incompleteTraces().size());
            }
        }
    }
    /** Real bounded multidimensional heaps can expose more than65536 snapshot field entries.
     * @param directory compiler workspace
     * @throws Exception for fixture failures */
    @Test void snapshotsCoverAggregateActorHeap(@TempDir Path directory) throws Exception {
        var classes=BytecodeFixtures.compile(directory,26,"@Model public class Fixture{public Fixture(){}@Actor(id=0)public void a(){int[][] x=new int[9][4096];}@Actor(id=1)public void b(){int[][] x=new int[9][4096];}}");
        var limits=new BytecodeLimits(256,128,40000,32,4096,256,4096,128,8192,1024);var program=BytecodeLoader.load(classes,"Fixture",limits);
        var state=BytecodeFixtures.run(program,program.initialState(),0);state=BytecodeFixtures.run(program,state,1);
        assertEquals(36883,state.executions.get(0).cells);assertEquals(36883,state.executions.get(1).cells);
        var snapshot=BytecodeSnapshots.capture(state,state.canonicalBytes());assertTrue(snapshot.fields().size()>65536);
        assertTrue(snapshot.fields().size()<=88201);assertEquals("0",snapshot.fields().get("actor:1:9[4095]").raw());
    }

}
