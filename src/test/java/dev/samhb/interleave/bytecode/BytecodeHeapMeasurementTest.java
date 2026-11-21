/** Reproducible broader-heap measurements gated by independent instruction outcomes. */
package dev.samhb.interleave.bytecode;

import java.lang.management.ManagementFactory;
import java.nio.file.*;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

class BytecodeHeapMeasurementTest {
    /** Sole live retention root spanning explicit GC samples. */
    private static volatile Held retained;
    /** Keeps actual exact storage and witnesses live outside the measurement stack.
     * @param explorer key store owner
     * @param result concrete terminal observations */
    private record Held(BytecodeExplorer explorer,BytecodeExplorer.Result result) {}

    /** Both heap fixtures must meet the independent reference/batching gate before measurement.
     * @param directory compiler workspace
     * @throws Exception for fixture failures */
    @Test void dynamicHeapOutcomeGate(@TempDir Path directory) throws Exception {
        for(String fixture:List.of("cyclic-publication","aliased-array")) {
            var program=fixture(directory,fixture);var expected=BytecodeInstructionOracle.enumerate(program);
            for(var mode:BytecodeScheduler.Mode.values()) {
                var result=new BytecodeExplorer(program,mode).explore(100000);assertTrue(result.exhaustive());
                assertEquals(expected,BytecodeBatchingOracleTest.independentProjection(result));
                assertTrue(result.metrics().sharing().promotions()>0);assertTrue(result.metrics().sharing().edges()>0);
            }
        }
    }

    /** Runs fixed-heap SerialGC forks with two warmups and three alternating-mode repetitions.
     * @param arguments output directory and fork identifier
     * @throws Exception for correctness, GC or output failures */
    public static void main(String[] arguments) throws Exception {
        if(arguments.length!=2)throw new IllegalArgumentException("Expected output directory and fork id");
        var flags=ManagementFactory.getRuntimeMXBean().getInputArguments();
        if(!flags.contains("-XX:+UseSerialGC") || !flags.contains("-XX:MarkSweepDeadRatio=0"))throw new IllegalArgumentException("Compacting SerialGC required");
        Path output=Path.of(arguments[0]);Files.createDirectories(output);String fork=arguments[1];
        var rows=new ArrayList<String>();rows.add("fixture,fork,repetition,mode,states,transitions,encodingCalls,encodingBytes,encodingNanos,elapsedNanos,heapBefore,heapAfter,heapDelta,sharingTraversals,sharingObjects,sharingEdges,sharingPromotions,historyOnlyStates,outcomes");
        var conditions=new StringBuilder("# 95 raw heap measurement conditions\n\n");
        conditions.append("- Java: ").append(System.getProperty("java.runtime.version")).append("\n- OS/architecture: ")
            .append(System.getProperty("os.name")).append('/').append(System.getProperty("os.arch")).append("\n- Flags: ").append(flags)
            .append("\n- Two warmups and three measured repetitions per fixture/mode; alternating mode order. Retained heap is post-compaction used heap with actual store/witnesses live, minus the pre-run post-GC heap.\n")
            .append("- History-only counts come from a separate production-granularity enumeration, checked against actual unique-state counts; only sharing flags are erased in comparison copies. Its work is outside timed samples.\n");
        for(String fixture:List.of("cyclic-publication","aliased-array")) {
            var program=fixture(output,fixture);var expected=BytecodeInstructionOracle.enumerate(program);
            conditions.append("- ").append(fixture).append(" source SHA-256: ").append(HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(output.resolve(fixture).resolve("sources/Fixture.java")))))
                .append("; artifact: ").append(program.artifactDigest()).append("; limits: ").append(program.limits()).append('\n');
            for(var mode:BytecodeScheduler.Mode.values())for(int i=0;i<2;i++)run(program,expected,mode);
            for(int repetition=0;repetition<3;repetition++)for(var mode:repetition%2==0 ? BytecodeScheduler.Mode.values() : new BytecodeScheduler.Mode[]{BytecodeScheduler.Mode.BATCHED,BytecodeScheduler.Mode.REFERENCE}) {
                retained=null;BytecodeMeasurementTest.collect();long before=ManagementFactory.getMemoryMXBean().getHeapMemoryUsage().getUsed();
                long elapsed=run(program,expected,mode);BytecodeMeasurementTest.collect();long after=ManagementFactory.getMemoryMXBean().getHeapMemoryUsage().getUsed();
                var m=retained.result().metrics();var work=m.sharing();long history=historyOnly(program,mode,m.states());
                rows.add(String.join(",",fixture,fork,Integer.toString(repetition),mode.name(),Long.toString(m.states()),Long.toString(m.transitions()),Long.toString(m.encodingCalls()),Long.toString(m.encodingBytes()),Long.toString(m.encodingNanos()),Long.toString(elapsed),Long.toString(before),Long.toString(after),Long.toString(after-before),Long.toString(work.traversals()),Long.toString(work.objects()),Long.toString(work.edges()),Long.toString(work.promotions()),Long.toString(history),Integer.toString(expected.size())));
            }
        }
        Files.write(output.resolve("samples.csv"),rows,StandardCharsets.UTF_8);Files.writeString(output.resolve("conditions.md"),conditions);
    }

    /** Runs with live storage retained and rejects any incomplete/mismatched measurement.
     * @param program fixture
     * @param expected independent outcomes
     * @param mode scheduling mode
     * @return elapsed nanoseconds */
    private static long run(BytecodeProgram program,Set<BytecodeInstructionOracle.Observation> expected,BytecodeScheduler.Mode mode) {
        var explorer=new BytecodeExplorer(program,mode);long start=System.nanoTime();var result=explorer.explore(100000);long elapsed=System.nanoTime()-start;
        if(!result.exhaustive() || !expected.equals(BytecodeBatchingOracleTest.independentProjection(result)))throw new AssertionError("Heap measurement outcome mismatch");
        retained=new Held(explorer,result);return elapsed;
    }

    /** Counts extra unique states distinguished solely by historical sharing flags.
     * @param program fixture
     * @param mode production scheduling granularity
     * @param expectedStates actual measured unique state count
     * @return full-state count minus distinct history-erased state count */
    private static long historyOnly(BytecodeProgram program,BytecodeScheduler.Mode mode,long expectedStates) {
        var scheduler=new BytecodeScheduler(program,mode);var pending=new ArrayDeque<BytecodeState>();pending.add(program.initialState());
        var seen=new HashSet<String>();var erased=new HashSet<String>();
        while(!pending.isEmpty()) {
            var state=pending.removeLast();String key=Base64.getEncoder().encodeToString(state.canonicalBytes());if(!seen.add(key))continue;
            if(seen.size()>100000)throw new AssertionError("History enumeration exceeded ceiling");
            var copy=state.copy();copy.heap.values().forEach(object->object.shared=false);erased.add(Base64.getEncoder().encodeToString(copy.canonicalBytes()));
            if(BytecodeObservations.evaluate(program,state).outcome()==null)for(int actor:scheduler.choices(state))pending.add(scheduler.advance(state,actor).state());
        }
        if(seen.size()!=expectedStates)throw new AssertionError("History enumeration disagrees with measured states");
        return seen.size()-erased.size();
    }

    /** Compiles exactly the saved source and loads the real frozen artifact.
     * @param directory workspace
     * @param name checked-in fixture name
     * @return initialized immutable program
     * @throws Exception for missing resource/compiler failures */
    private static BytecodeProgram fixture(Path directory,String name) throws Exception {
        String source;
        try(var input=BytecodeHeapMeasurementTest.class.getResourceAsStream("/bytecode/heap/"+name+"/Fixture.java")) {
            if(input==null)throw new IllegalStateException("Missing heap fixture "+name);source=new String(input.readAllBytes(),StandardCharsets.UTF_8);
        }
        return BytecodeLoader.load(BytecodeFixtures.compile(directory.resolve(name),26,source),"Fixture",BytecodeLimits.defaults());
    }
}
