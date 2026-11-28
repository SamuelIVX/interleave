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

    /** Fixed-seed heap/cast/dispatch programs use the same independent JVM and deletion shrinker.
     * @param directory compiler workspace
     * @throws Exception for fixture/oracle failures */
    @Test void generatedHeapProgramsMatchJvmWithShrinking(@TempDir Path directory) throws Exception {
        long seed=0x952026L;var random=new Random(seed);
        for(int sample=0;sample<12;sample++) {
            int initial=random.nextInt();var operations=new ArrayList<String>();
            for(int index=0;index<5;index++) {
                int n=random.nextInt(17)-8;
                operations.add(switch(random.nextInt(4)) {
                    case 0 -> "{int[] a=new int[2];a[1]=x;x=a[1]+"+n+";}";
                    case 1 -> "{Cell c=new Cell(x);Read r=c;x=r.read()+"+n+";}";
                    case 2 -> "{Object[] a=new Cell[1];a[0]=new Cell(x);x=((Cell)a[0]).read()^"+n+";}";
                    default -> "{Cell c=new Cell(x);c.next=c;Object o=c;if(o instanceof Cell)x=((Cell)o).next.read()-("+n+");}";
                });
            }
            int release=new int[]{17,21,26}[sample%3];
            if(!matches(directory.resolve("case"+sample),release,heapSource(initial,operations))) {
                int selected=sample;int[] attempt={0};
                var minimal=shrink(operations,candidate->{try{return !matches(directory.resolve("shrink"+selected+"-"+attempt[0]++),release,heapSource(initial,candidate));}
                    catch(Exception failure){throw new IllegalStateException(failure);}});
                Path regression=Path.of("build/bytecode-regressions/heap-seed-"+seed+"-case-"+sample+"/Fixture.java");
                Files.createDirectories(regression.getParent());Files.writeString(regression,heapSource(initial,minimal));
                fail("Heap differential mismatch; saved "+regression);
            }
        }
    }

    /** Fixed-seed exception programs compare catch/finally/rethrow and suppression with the JVM.
     * @param directory compiler workspace
     * @throws Exception for differential or saved-regression failures */
    @Test void generatedExceptionsMatchJvmWithShrinking(@TempDir Path directory) throws Exception {
        long seed=0x962026L;var random=new Random(seed);
        for(int sample=0;sample<12;sample++) {
            int initial=random.nextInt(17);var operations=new ArrayList<String>();
            for(int index=0;index<4;index++) {
                int n=random.nextInt(7),divisor=random.nextInt(2);
                operations.add(switch(random.nextInt(4)) {
                    case 0 -> "try{x=18/"+divisor+";}catch(ArithmeticException e){x+="+n+";}finally{x^=3;}";
                    case 1 -> "try{throw new RuntimeException(\"explicit\");}catch(Exception e){x+="+n+";}";
                    case 2 -> "try{try{x=9/"+divisor+";}catch(ArithmeticException e){throw e;}}catch(RuntimeException e){x^="+n+";}";
                    default -> "{Throwable e=new Throwable();Throwable child=new RuntimeException();e.addSuppressed(child);e.addSuppressed(child);x+=e.getSuppressed().length;}";
                });
            }
            int release=new int[]{17,21,26}[sample%3];
            if(!matches(directory.resolve("exceptions"+sample),release,source(initial,operations))) {
                int selected=sample;int[] attempt={0};
                var minimal=shrink(operations,candidate->{try{return !matches(directory.resolve("exception-shrink"+selected+"-"+attempt[0]++),release,source(initial,candidate));}
                    catch(Exception failure){throw new IllegalStateException(failure);}});
                Path regression=Path.of("build/bytecode-regressions/exception-seed-"+seed+"-case-"+sample+"/Fixture.java");
                Files.createDirectories(regression.getParent());Files.writeString(regression,source(initial,minimal));
                fail("Exception differential mismatch; saved "+regression);
            }
        }
    }

    /** Wide primitive generators retain independent JVM checks, deletion shrinking and saved seeds.
     * @param directory compiler/oracle workspace
     * @throws Exception for fixture or saved-regression failures */
    @Test void generatedPrimitivesMatchJvmWithShrinking(@TempDir Path directory) throws Exception {
        long seed=0x972026L;var random=new Random(seed);
        for(int sample=0;sample<12;sample++) {
            int initial=random.nextInt();var operations=new ArrayList<String>();
            for(int index=0;index<6;index++) {
                long n=random.nextLong();int distance=random.nextInt(129)-64;
                operations.add(switch(random.nextInt(6)) {
                    case 0->"x=(int)(((long)x+"+n+"L)>>>"+distance+");";
                    case 1->"{long a=x;a=(a*"+n+"L)^(-a);x=(int)(a>>"+distance+");}";
                    case 2->"{float a=x;a=(a*1.5f)/3.0f;x=(int)(a%127.0f);}";
                    case 3->"{double a=x;a=(a-0.5)*1.25;x=(int)(a/-3.0);}";
                    case 4->"{double a=x;if(a<0.0)x=(int)(long)(float)a;else x=(int)(a%31.0);}";
                    default->"{long a=x;a/="+(n|1)+"L;x=(int)(a+(double)x);}";
                });
            }
            int release=new int[]{17,21,26}[sample%3];
            if(!primitiveMatches(directory.resolve("primitives"+sample),release,source(initial,operations))) {
                int selected=sample;int[] attempt={0};
                Path regression=Path.of("build/bytecode-regressions/primitive-seed-"+seed+"-case-"+sample+"/Fixture.java");
                savePrimitiveRegression(regression,initial,operations,candidate->{try{return !primitiveMatches(directory.resolve("primitive-shrink"+selected+"-"+attempt[0]++),release,source(initial,candidate));}
                    catch(Exception failure){throw new IllegalStateException(failure);}});
                fail("Primitive differential mismatch; saved "+regression);
            }
        }
    }

    /** Checks sequential JVM results and both scheduling modes against the instruction enumerator.
     * @param directory isolated compiler workspace
     * @param release target class-file release
     * @param source generated bounded program
     * @return whether the independent JVM result matches
     * @throws Exception for fixture/oracle failures */
    private static boolean primitiveMatches(Path directory,int release,String source) throws Exception {
        boolean matches=matches(directory,release,source);
        var limits=new BytecodeLimits(4096,128,8192,32,64,256,4096,128,8192,1024);
        var program=BytecodeLoader.load(directory.resolve("classes"),"Fixture",limits);
        var expected=BytecodeInstructionOracle.enumerate(program);
        for(var mode:BytecodeScheduler.Mode.values()) {
            var result=new BytecodeExplorer(program,mode).explore(100000);assertTrue(result.traversalComplete());
            if(!expected.equals(BytecodeBatchingOracleTest.independentProjection(result)))return false;
        }
        return matches;
    }

    /** A comparison mismatch reaches the shrink/save path before the test reports failure.
     * @param directory saved regression workspace
     * @throws Exception for file failures */
    @Test void primitiveMismatchIsShrunkAndSaved(@TempDir Path directory) throws Exception {
        Path regression=directory.resolve("seed-9904166/case0/Fixture.java");
        savePrimitiveRegression(regression,3,List.of("x+=1;","x=7;","x*=2;"),candidate->candidate.contains("x=7;"));
        assertEquals(source(3,List.of("x=7;")),Files.readString(regression));
    }

    /** Saves the minimal primitive mismatch without swallowing setup/compiler failures.
     * @param file deterministic source regression location
     * @param initial original seed input
     * @param operations original generated statements
     * @param stillFails independently evaluated mismatch predicate
     * @throws java.io.IOException for output failures */
    private static void savePrimitiveRegression(Path file,int initial,List<String> operations,Predicate<List<String>> stillFails) throws java.io.IOException {
        var minimal=shrink(operations,stillFails);Files.createDirectories(file.getParent());Files.writeString(file,source(initial,minimal));
    }

    /** Adds a bounded application hierarchy around independently generated actor statements.
     * @param initial initial JVM int
     * @param operations bounded statements
     * @return complete compiler fixture */
    private static String heapSource(int initial,List<String> operations) {
        return "interface Read{int read();}class Cell implements Read{int x;Cell next;Cell(int x){this.x=x;}public int read(){return x;}}"+source(initial,operations);
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
