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
        assertEquals(trace.threadIds().size(),trace.bytecodeEvidence().transitions().size());
        assertEquals(trace.threadIds().size()+1,trace.states().size());
        assertEquals("CHECK_FALSE",trace.bytecodeEvidence().terminal().kind());
        assertEquals("int:1",trace.states().getLast().fields().get("setup:0.Fixture.x:I"));
        assertTrue(result.toJson().contains("\"programKind\": \"BYTECODE\""));
        assertFalse(trace.toJson().contains("\"sourceDigest\""));
    }
}
