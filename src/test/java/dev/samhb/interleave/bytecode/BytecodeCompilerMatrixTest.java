/** Explicit supported/rejected compiler constructs across non-preview Java17/21/26 targets. */
package dev.samhb.interleave.bytecode;

import dev.samhb.interleave.format.BytecodeProgramLoader;
import java.nio.file.*;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

class BytecodeCompilerMatrixTest {
    /** Class-file release acceptance never implies arbitrary compiler-generated library support.
     * @param directory compiler workspace
     * @throws Exception for fixture failures */
    @Test void unsupportedCompilerConstructsStayRejected(@TempDir Path directory) throws Exception {
        var rejected=Map.of(
            "string-switch","@Model public class Fixture {public int x;public Fixture(){} @Actor(id=0)public void actor(){String s=\"x\";x=switch(s){case \"x\"->1;default->2;};}}",
            "enum","enum Kind {A,B} @Model public class Fixture {public Fixture(){} @Actor(id=0)public void actor(){Kind k=Kind.A;}}",
            "array-clone","@Model public class Fixture {public Fixture(){} @Actor(id=0)public void actor(){int[] a=new int[1];Object b=a.clone();}}",
            "assertions","@Model public class Fixture {public Fixture(){} @Actor(id=0)public void actor(){assert false;}}",
            "record","record Item(int x){} @Model public class Fixture {public Fixture(){} @Actor(id=0)public void actor(){Item i=new Item(1);}}");
        for(int release:new int[]{17,21,26}) {
            for(var entry:rejected.entrySet()) {
                var classes=BytecodeFixtures.compile(directory.resolve(entry.getKey()+release),release,entry.getValue());
                assertEquals("UNSUPPORTED",assertThrows(BytecodeLoadException.class,()->BytecodeProgramLoader.load(classes,"Fixture"),entry.getKey()+release).category);
            }
            if(release>=21) {
                var classes=BytecodeFixtures.compile(directory.resolve("pattern"+release),release,"""
                    @Model public class Fixture {public int x;public Fixture(){} @Actor(id=0)public void actor(){Object o=this;
                      x=switch(o){case String s->1;default->2;};}}
                    """);
                assertEquals("UNSUPPORTED",assertThrows(BytecodeLoadException.class,()->BytecodeProgramLoader.load(classes,"Fixture")).category);
            }
        }
    }

    /** Private nestmate callbacks remain interpreted at each supported compiler target.
     * @param directory compiler workspace
     * @throws Exception for compiler/JVM failures */
    @Test void nestmatesMatchJvmAcrossCompilerTargets(@TempDir Path directory) throws Exception {
        for(int release:new int[]{17,21,26}) {
            var work=directory.resolve("release"+release);var classes=BytecodeFixtures.compile(work,release,"""
                @Model public class Fixture {public int x;public Fixture(){}
                  static class Nested {private static int read(){return 7;}}
                  @Actor(id=0)public void actor(){x=Nested.read();}}
                """);
            assertEquals("7",BytecodeFixtures.oracle(work,classes,"Fixture f=new Fixture();f.actor();System.out.print(f.x);"));
            var program=BytecodeProgramLoader.load(classes,"Fixture");
            assertEquals(new BytecodeState.Int(7),BytecodeFixtures.run(program,program.initialState(),0).fixtureField("x"));
        }
    }
}
