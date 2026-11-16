/** Reproducible opt-in interpreter measurements with real post-GC retained heap, separate from key bytes. */
package dev.samhb.interleave.bytecode;

import java.lang.management.ManagementFactory;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.security.MessageDigest;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

class BytecodeMeasurementTest {
    /** Explicit sole measurement root; cleared before each baseline collection. */
    private static volatile Held retained;
    /** Keeps the search store and witnesses live after the worker method has returned.
     * @param explorer exact key store owner
     * @param result concrete findings */
    private record Held(BytecodeExplorer explorer,BytecodeExplorer.Result result) {}

    /** Both measurement fixtures must agree with independent instruction enumeration before timing.
     * @param directory compiler workspace
     * @throws Exception for fixture failures */
    @Test void referenceBatchOutcomeGate(@TempDir Path directory) throws Exception {
        for(String fixture:List.of("lost-update","two-lock")) {
            var program=fixture(directory,fixture);var expected=BytecodeInstructionOracle.enumerate(program);requireOutcomes(fixture,expected);
            for(var mode:BytecodeScheduler.Mode.values()) {
                var result=new BytecodeExplorer(program,mode).explore(100000);
                assertTrue(result.exhaustive());assertEquals(expected,BytecodeBatchingOracleTest.independentProjection(result));
                assertTrue(result.metrics().encodingBytes()>=result.metrics().retainedKeyBytes());
                assertEquals(1,result.metrics().sharing().traversals());assertEquals(0,result.metrics().sharing().promotions());
                assertEquals(fixture.equals("two-lock") ? 4 : 2,result.metrics().sharing().objects());
                assertEquals(fixture.equals("two-lock") ? 2 : 0,result.metrics().sharing().edges());
            }
        }
    }

    /** Runs one fresh measurement JVM; use the profileBytecode Gradle task for fixed heap/GC flags.
     * Two warmups per fixture/mode precede three measured repetitions with alternating mode order.
     * @param arguments output directory and fork identifier
     * @throws Exception for compiler, output or correctness-gate failures */
    public static void main(String[] arguments) throws Exception {
        if(arguments.length!=2)throw new IllegalArgumentException("Expected output directory and fork id");
        var flags=ManagementFactory.getRuntimeMXBean().getInputArguments();
        if(!flags.contains("-XX:+UseSerialGC") || !flags.contains("-XX:MarkSweepDeadRatio=0"))
            throw new IllegalArgumentException("Retained-memory measurement requires SerialGC and MarkSweepDeadRatio=0");
        Path output=Path.of(arguments[0]);Files.createDirectories(output);String fork=arguments[1];
        var metadata=new StringBuilder("# 94B raw measurement fork ").append(fork).append("\n\n");
        metadata.append("- Java: `").append(System.getProperty("java.runtime.version")).append("`\n")
            .append("- VM: `").append(System.getProperty("java.vm.name")).append("`\n")
            .append("- OS/architecture: `").append(System.getProperty("os.name")).append('/').append(System.getProperty("os.arch")).append("`\n")
            .append("- JVM arguments: `").append(ManagementFactory.getRuntimeMXBean().getInputArguments()).append("`\n")
            .append("- Warmups: 2 per fixture/mode; repetitions: 3; modes alternate order per repetition.\n")
            .append("- Retained heap: used heap after verified explicit GC and full compaction (MarkSweepDeadRatio=0), with explorer and result reachable; delta against pre-run used heap.\n")
            .append("- Key bytes are only canonical payload, reported separately. History-only states are zero in the setup-only all-shared profile.\n");
        var rows=new ArrayList<String>();
        rows.add("fixture,fork,repetition,mode,states,transitions,opcodes,instructions,encodingCalls,encodingBytes,encodingNanos,retainedKeyBytes,elapsedNanos,heapBefore,heapAfter,heapDelta,gcCollections,checkInvocations,checkInstructions,sharingTraversals,sharingObjects,sharingEdges,sharingPromotions,historyOnlyStates,outcomes");
        for(String fixture:List.of("lost-update","two-lock")) {
            var program=fixture(output,fixture);
            Path source=output.resolve(fixture).resolve("sources/Fixture.java");
            metadata.append("- ").append(fixture).append(" source SHA-256: `").append(hex(Files.readAllBytes(source))).append("`; artifact manifest: `").append(program.artifactDigest()).append("`; limits: `").append(program.limits).append("`\n");
            var expected=BytecodeInstructionOracle.enumerate(program);requireOutcomes(fixture,expected);
            for(int warmup=0;warmup<2;warmup++)for(var mode:BytecodeScheduler.Mode.values())runRetained(program,expected,mode);
            for(int repetition=0;repetition<3;repetition++) {
                var order=repetition%2==0 ? List.of(BytecodeScheduler.Mode.REFERENCE,BytecodeScheduler.Mode.BATCHED)
                    : List.of(BytecodeScheduler.Mode.BATCHED,BytecodeScheduler.Mode.REFERENCE);
                for(var mode:order)rows.add(sample(program,expected,fixture,fork,repetition,mode));
            }
        }
        Files.write(output.resolve("samples.csv"),rows,StandardCharsets.UTF_8);
        Files.writeString(output.resolve("conditions.md"),metadata,StandardCharsets.UTF_8);
    }

    /** Ensures equivalence cannot pass by losing both a known serialized and a known failing path.
     * @param fixture fixture identity
     * @param observations independent instruction outcomes */
    private static void requireOutcomes(String fixture,Set<BytecodeInstructionOracle.Observation> observations) {
        var kinds=observations.stream().map(BytecodeInstructionOracle.Observation::kind).collect(java.util.stream.Collectors.toSet());
        var expected=fixture.equals("two-lock") ? Set.of("COMPLETED","DEADLOCK") : Set.of("COMPLETED","CHECK_FALSE");
        if(observations.size()!=2 || !kinds.equals(expected))throw new AssertionError("Missing known fixture outcomes: "+fixture+" "+observations);
        String cell=BytecodeInstructionOracle.cellKey(BytecodeState.FIXTURE,new BytecodeProgram.FieldId("Fixture","x","I"));
        for(var outcome:observations) {
            int value=outcome.kind().equals("COMPLETED") ? 2 : fixture.equals("two-lock") ? 0 : 1;
            if(!new BytecodeState.Int(value).equals(outcome.fields().get(cell)))throw new AssertionError("Wrong shared observation: "+outcome);
            if(outcome.kind().equals("DEADLOCK") && outcome.waits().size()!=2)throw new AssertionError("Missing concrete wait cycle");
        }
    }

    /** Holds the actual visited-key store and concrete witnesses across post-run GC.
     * @param program immutable interpreted fixture
     * @param expected independently enumerated outcomes
     * @param fixture fixture name
     * @param fork fresh JVM identifier
     * @param repetition measured repetition
     * @param mode scheduling mode
     * @return one CSV row
     * @throws InterruptedException if measurement is cancelled */
    private static String sample(BytecodeProgram program,Set<BytecodeInstructionOracle.Observation> expected,String fixture,String fork,int repetition,BytecodeScheduler.Mode mode) throws InterruptedException {
        retained=null;collect();long before=ManagementFactory.getMemoryMXBean().getHeapMemoryUsage().getUsed();
        long elapsed=runRetained(program,expected,mode);
        long collections=gcCount();collect();long after=ManagementFactory.getMemoryMXBean().getHeapMemoryUsage().getUsed();long gc=gcCount()-collections;
        var metrics=retained.result().metrics();var sharing=metrics.sharing();
        return String.join(",",fixture,fork,Integer.toString(repetition),mode.toString(),Long.toString(metrics.states()),Long.toString(metrics.transitions()),
            Long.toString(metrics.opcodes()),Long.toString(metrics.instructions()),Long.toString(metrics.encodingCalls()),Long.toString(metrics.encodingBytes()),
            Long.toString(metrics.encodingNanos()),Long.toString(metrics.retainedKeyBytes()),Long.toString(elapsed),Long.toString(before),Long.toString(after),
            Long.toString(after-before),Long.toString(gc),Long.toString(metrics.checkInvocations()),Long.toString(metrics.checkInstructions()),Long.toString(sharing.traversals()),Long.toString(sharing.objects()),Long.toString(sharing.edges()),
            Long.toString(sharing.promotions()),"0",Integer.toString(retained.result().witnesses().size()));
    }

    /** Executes outside the GC sampler's stack frame, leaving only the explicit volatile retention root.
     * Warmup and previous search locals therefore cannot contaminate the next baseline.
     * @param program immutable fixture
     * @param expected independent observations
     * @param mode scheduling granularity
     * @return elapsed search nanoseconds */
    private static long runRetained(BytecodeProgram program,Set<BytecodeInstructionOracle.Observation> expected,BytecodeScheduler.Mode mode) {
        var explorer=new BytecodeExplorer(program,mode);long start=System.nanoTime();var result=explorer.explore(100000);long elapsed=System.nanoTime()-start;
        if(!result.exhaustive() || !expected.equals(BytecodeBatchingOracleTest.independentProjection(result)))throw new AssertionError("Measured outcome mismatch");
        retained=new Held(explorer,result);return elapsed;
    }

    /** Requests two synchronous full collections under the configured Serial GC and verifies progress.
     * @throws InterruptedException if measurement is cancelled */
    private static void collect() throws InterruptedException {
        long before=gcCount();System.gc();Thread.sleep(20);System.gc();Thread.sleep(20);
        if(gcCount()<=before)throw new IllegalStateException("Explicit GC made no observable progress; retained-memory sample invalid");
    }

    /** Sums JVM-reported completed collector invocations.
     * @return actual completed collections */
    private static long gcCount() {return ManagementFactory.getGarbageCollectorMXBeans().stream().mapToLong(bean->Math.max(0,bean.getCollectionCount())).sum();}

    /** Loads exactly the checked-in measurement source through the real compiler and interpreter loader.
     * @param directory isolated compiler workspace
     * @param name resource fixture name
     * @return validated program with identical default limits in both modes
     * @throws Exception for fixture failures */
    private static BytecodeProgram fixture(Path directory,String name) throws Exception {
        String source;
        try(var resource=BytecodeMeasurementTest.class.getResourceAsStream("/bytecode/concurrency/"+name+"/Fixture.java")) {
            if(resource==null)throw new IllegalStateException("Missing measurement fixture "+name);
            source=new String(resource.readAllBytes(),StandardCharsets.UTF_8);
        }
        return BytecodeLoader.load(BytecodeFixtures.compile(directory.resolve(name),26,source),"Fixture",BytecodeLimits.defaults());
    }

    /** Hashes exact source bytes without relying on filename or timestamps.
     * @param bytes source payload
     * @return SHA-256 hex
     * @throws Exception if the required JDK digest is unavailable */
    private static String hex(byte[] bytes) throws Exception {return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));}
}
