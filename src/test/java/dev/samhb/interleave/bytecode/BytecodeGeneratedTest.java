/** Fixed-seed sequential differential generation with bounded shrinking and saved replayable source. */
package dev.samhb.interleave.bytecode;

import java.nio.file.*;
import java.util.*;
import java.util.function.Predicate;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

class BytecodeGeneratedTest {
    /** Reproducible generator identity, independent of any interpreter random state. */
    private static final long SEED=0x94A2026L;

    /** Generated bounded arithmetic/control flow is compared with a separate JVM process.
     *
     * @param directory compiler workspace
     * @throws Exception for fixture/oracle failures */
    @Test void generatedProgramsMatchJvmWithShrinking(@TempDir Path directory) throws Exception {
        var random=new Random(SEED);
        for(int sample=0;sample<36;sample++) {
            int initial=random.nextInt(); var operations=new ArrayList<String>();
            for(int i=0;i<7;i++) {
                int value=random.nextInt();
                operations.add(switch(random.nextInt(9)) {
                    case 0 -> "x += "+value+";"; case 1 -> "x -= "+value+";";
                    case 2 -> "x *= "+value+";"; case 3 -> "x ^= "+value+";";
                    case 4 -> "x >>>= "+(value&63)+";"; case 5 -> "x /= "+(value|1)+";";
                    case 6 -> "if((x&1)==0) x += "+value+"; else x -= "+value+";";
                    case 7 -> "for(int i=0;i<3;i++) x=(x<<1)^"+value+";";
                    default -> "x=(byte)x+(short)(x>>3)+(char)(x^"+value+");";
                });
            }
            int release=new int[]{17,21,26}[sample%3]; Path work=directory.resolve("case"+sample);
            if(!matches(work,release,source(initial,operations))) {
                final int selected=sample; final int[] attempt={0};
                var minimal=shrink(operations,candidate->{
                    try {return !matches(directory.resolve("shrink"+selected+"-"+attempt[0]++),release,source(initial,candidate));}
                    catch(Exception failure) {throw new IllegalStateException(failure);}
                });
                Path regression=Path.of("build/bytecode-regressions/seed-"+SEED+"-case-"+sample+"/Fixture.java");
                Files.createDirectories(regression.getParent()); Files.writeString(regression,source(initial,minimal));
                fail("JVM/interpreter mismatch; shrunk source saved to "+regression);
            }
        }
    }

    /** The test-only shrinker removes irrelevant operations and reaches deletion-one-minimality. */
    @Test void shrinkerRetainsOnlyFailureConditions() {
        var input=List.of("prefix","requiredA","noise","requiredB","suffix");
        Predicate<List<String>> condition=values->values.contains("requiredA") && values.contains("requiredB");
        assertEquals(List.of("requiredA","requiredB"),shrink(input,condition));
        assertEquals(List.of(),shrink(input,ignored->true));
    }

    /** Stored source regressions remain runnable in each admitted target release.
     *
     * @param directory compiler workspace
     * @throws Exception for resource/compiler/oracle failures */
    @Test void savedRegressionSourcesMatchJvm(@TempDir Path directory) throws Exception {
        for(String name:List.of("arithmetic","cleanup")) for(int release:new int[]{17,21,26}) {
            try(var input=getClass().getResourceAsStream("/bytecode/foundation/regressions/"+name+"/Fixture.java")) {
                assertNotNull(input); String source=new String(input.readAllBytes(),java.nio.charset.StandardCharsets.UTF_8);
                assertTrue(matches(directory.resolve(name+release),release,source));
            }
        }
    }

    /**
     * @param initial seed input
     * @param operations bounded generated statements
     * @return fixture source */
    private static String source(int initial,List<String> operations) {
        return "@Model public class Fixture { public int value; public Fixture() {} @Actor(id=0) public void actor() { int x="
            +initial+";"+String.join("",operations)+"value=x; } }";
    }

    /**
     * @param directory isolated output
     * @param release class-file release
     * @param source numeric fixture
     *
     * @return normalized interpreter/JVM equality
     * @throws Exception for fixture/oracle failures */
    private static boolean matches(Path directory,int release,String source) throws Exception {
        Path classes=BytecodeFixtures.compile(directory,release,source);
        var limits=new BytecodeLimits(4096,128,8192,32,64,256,4096,128,8192,1024);
        var program=BytecodeLoader.load(classes,"Fixture",limits);
        var state=BytecodeFixtures.run(program,program.initialState(),0);
        String expected=BytecodeFixtures.oracle(directory,classes,"Fixture f=new Fixture(); f.actor(); System.out.println(f.value);");
        return state.executions.get(0).phase==BytecodeState.Phase.TERMINATED
            && Integer.toString(((BytecodeState.Int)state.fixtureField("value")).value()).equals(expected);
    }

    /** Greedy bounded deletion shrinker; no global shortest-program claim.
     *
     * @param original generated statement sequence
     * @param stillFails independent differential predicate
     *
     * @return deletion-one-minimal failing sequence */
    private static List<String> shrink(List<String> original,Predicate<List<String>> stillFails) {
        var current=new ArrayList<>(original); int index=0;
        while(index<current.size()) {
            var candidate=new ArrayList<>(current); candidate.remove(index);
            if(stillFails.test(List.copyOf(candidate))) {current=candidate;index=0;} else index++;
        }
        return List.copyOf(current);
    }
}
