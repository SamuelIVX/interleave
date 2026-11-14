/** Metadata-only admission and setup through the internal loader seam. */
package dev.samhb.interleave.bytecode;

import java.nio.file.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

class BytecodeLoaderTest {
    /** Loading interprets setup, preserves actor metadata and makes isolated initialized states.
     *
     * @param directory isolated compiler output
     *
     * @throws Exception for fixture failures
     */
    @Test void loadsSetupWithoutPublicSelectors(@TempDir Path directory) throws Exception {
        Path classes=BytecodeFixtures.compile(directory,17,"""
            @Model public class Fixture {
              public int value=7; public Object left=new Object(); public Object right=new Object();
              public Fixture() {} @Actor(id=0) public void actor() { value++; }
            }
            """);
        var program=BytecodeLoader.load(classes,"Fixture",BytecodeLimits.defaults());
        assertEquals(1,program.actors.size());
        var state=program.initialState();
        assertEquals(new BytecodeState.Int(7),state.fixtureField("value"));
        assertNotEquals(state.fixtureField("left"),state.fixtureField("right"));
        assertEquals(3,state.executions.get(BytecodeState.SETUP).allocations);
        assertArrayEquals(state.canonicalBytes(),program.initialState().canonicalBytes());
    }
    /** Frozen metadata survives input replacement and concurrent fresh-state execution.
     *
     * @param directory isolated input
     * @throws Exception for fixture/concurrency failures */
    @Test void frozenArtifactsAndConcurrentRunsAreIsolated(@TempDir Path directory) throws Exception {
        Path classes=BytecodeFixtures.compile(directory,26,
            "@Model public class Fixture { public int value=7; public Fixture() {} @Actor(id=0) public void actor(){value++;} }");
        var program=BytecodeLoader.load(classes,"Fixture",BytecodeLimits.defaults()); String digest=program.artifactDigest();
        byte[] original=Files.readAllBytes(classes.resolve("Fixture.class"));
        Files.write(classes.resolve("Fixture.class"),new byte[]{0});
        assertThrows(BytecodeLoadException.class,()->BytecodeLoader.load(classes,"Fixture",BytecodeLimits.defaults()));
        var workers=java.util.concurrent.Executors.newFixedThreadPool(2,runnable->{
            var thread=new Thread(runnable,"bytecode-isolation-probe");thread.setDaemon(true);return thread;
        });
        try {
            var first=workers.submit(()->BytecodeFixtures.run(program,program.initialState(),0));
            var second=workers.submit(()->BytecodeFixtures.run(program,program.initialState(),0));
            var left=first.get(10,java.util.concurrent.TimeUnit.SECONDS); var right=second.get(10,java.util.concurrent.TimeUnit.SECONDS);
            assertEquals(new BytecodeState.Int(8),left.fixtureField("value")); assertEquals(new BytecodeState.Int(8),right.fixtureField("value"));
            assertArrayEquals(left.canonicalBytes(),right.canonicalBytes());
            left.heap.get(BytecodeState.FIXTURE).fields.clear(); assertEquals(new BytecodeState.Int(8),right.fixtureField("value"));
        } finally {
            workers.shutdownNow();
            assertTrue(workers.awaitTermination(2,java.util.concurrent.TimeUnit.SECONDS),"Concurrent probe failed to stop");
        }
        assertEquals(digest,program.artifactDigest()); assertEquals(new BytecodeState.Int(7),program.initialState().fixtureField("value"));
        Files.write(classes.resolve("Fixture.class"),original);
        assertEquals(digest,BytecodeLoader.load(classes,"Fixture",BytecodeLimits.defaults()).artifactDigest());
    }

    /** Artifact identity changes with valid byte changes while remaining independent of the directory path.
     *
     * @param directory compiler workspace
     * @throws Exception for fixture failures */
    @Test void digestTracksFrozenBytesAcrossDirectoryAliases(@TempDir Path directory) throws Exception {
        var classes=BytecodeFixtures.compile(directory.resolve("first"),26,
            "@Model public class Fixture {int value=7;public Fixture(){} @Actor(id=0)public void actor(){value++;} }");
        var first=BytecodeLoader.load(classes,"Fixture",BytecodeLimits.defaults());
        Path alias=Files.createDirectories(directory.resolve("alias"));Files.copy(classes.resolve("Fixture.class"),alias.resolve("Fixture.class"));
        assertEquals(first.artifactDigest(),BytecodeLoader.load(alias,"Fixture",BytecodeLimits.defaults()).artifactDigest());
        classes=BytecodeFixtures.compile(directory.resolve("changed"),26,
            "@Model public class Fixture {int value=8;public Fixture(){} @Actor(id=0)public void actor(){value++;} }");
        var second=BytecodeLoader.load(classes,"Fixture",BytecodeLimits.defaults());
        assertTrue(first.artifactDigest().matches("[0-9a-f]{64}"));assertNotEquals(first.artifactDigest(),second.artifactDigest());
    }


    /** Repeated and distinct references resolve correctly against a large declared-method table.
     *
     * @param directory compiler workspace
     * @throws Exception for fixture failures */
    @Test void resolvesRepeatedCallsAgainstLargeDeclarationTable(@TempDir Path directory) throws Exception {
        var source=new StringBuilder("@Model public class Fixture {int value;public Fixture(){} @Actor(id=0)public void actor(){value=m0()+m499()+m999()+m499();}");
        for(int index=0;index<1000;index++)source.append("static int m").append(index).append("(){return ").append(index).append(";}");source.append('}');
        var classes=BytecodeFixtures.compile(directory,26,source.toString());
        var program=BytecodeLoader.load(classes,"Fixture",BytecodeLimits.defaults());
        assertEquals(5,program.methods.size());assertEquals(new BytecodeState.Int(1997),
            BytecodeFixtures.run(program,program.initialState(),0).fixtureField("value"));
    }

    /** A directory is rejected as a class input independently of optional external test tools.
     * @param directory immutable input workspace
     * @throws Exception for file failures */
    @Test void rejectsNonRegularDirectoryInput(@TempDir Path directory) throws Exception {
        Path classes=Files.createDirectories(directory.resolve("classes"));
        Files.createDirectory(classes.resolve("Fixture.class"));
        assertEquals("INPUT",assertThrows(BytecodeLoadException.class,
            ()->BytecodeLoader.load(classes,"Fixture",BytecodeLimits.defaults())).category);
    }

    /** A deadline isolates a FIFO regression from the test JVM; this case requires the mkfifo utility.
     * @param directory immutable input workspace
     * @throws Exception for process/file failures */
    @Test void rejectsFifoInputsBeforeOpening(@TempDir Path directory) throws Exception {
        org.junit.jupiter.api.Assumptions.assumeTrue(mkfifoAvailable(),"mkfifo is required for the FIFO case");
        Path classes=Files.createDirectories(directory.resolve("classes"));
        Process create=new ProcessBuilder("mkfifo",classes.resolve("Fixture.class").toString()).start();
        try {
            assertTrue(create.waitFor(2,java.util.concurrent.TimeUnit.SECONDS)); assertEquals(0,create.exitValue());
        } finally { if(create.isAlive()) {create.destroyForcibly(); create.waitFor();} }
        Path output=directory.resolve("probe.txt");
        String classpath=BytecodeFixtures.classpath(BytecodeLoader.class,BytecodeInputProbe.class);
        Process probe=new ProcessBuilder(Path.of(System.getProperty("java.home"),"bin","java").toString(),
            "-cp",classpath,"dev.samhb.interleave.bytecode.BytecodeInputProbe",classes.toString())
            .redirectErrorStream(true).redirectOutput(output.toFile()).start();
        try {
            assertTrue(probe.waitFor(2,java.util.concurrent.TimeUnit.SECONDS),"Loader blocked opening a FIFO");
            assertEquals(0,probe.exitValue(),Files.readString(output)); assertEquals("INPUT",Files.readString(output).strip());
        } finally { if(probe.isAlive()) {probe.destroyForcibly(); probe.waitFor();} }
    }

    /** Checks the command search path without spawning an optional utility just to detect it.
     * @return whether an executable regular mkfifo file is available on PATH */
    private static boolean mkfifoAvailable() {
        return java.util.Arrays.stream(System.getenv().getOrDefault("PATH","").split(java.util.regex.Pattern.quote(java.io.File.pathSeparator)))
            .map(directory->Path.of(directory,"mkfifo")).anyMatch(path->Files.isRegularFile(path) && Files.isExecutable(path));
    }

}
