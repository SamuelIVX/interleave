/** Instruction-reference/batched outcomes; an independent opcode enumerator supplements these targeted cases. */
package dev.samhb.interleave.bytecode;

import java.nio.file.Path;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

class BytecodeBatchingOracleTest {
    /** Real interpreter batching preserves lost-update detection while reducing selected transitions.
     * @param directory compiler workspace
     * @throws Exception for fixture failures */
    @Test void batchedLostUpdateMatchesReference(@TempDir Path directory) throws Exception {
        var classes=BytecodeFixtures.compile(directory,26,"""
            @Model public class Fixture {public int x;public Fixture(){}
              @Actor(id=0)public void first(){x++;} @Actor(id=1)public void second(){x++;}
              @Check public boolean valid(){return x==2;}
            }
            """);
        var program=BytecodeLoader.load(classes,"Fixture",BytecodeLimits.defaults());
        var reference=new BytecodeExplorer(program,BytecodeScheduler.Mode.REFERENCE).explore(100000);
        var batched=new BytecodeExplorer(program,BytecodeScheduler.Mode.BATCHED).explore(100000);
        assertTrue(reference.traversalComplete());assertTrue(batched.traversalComplete());
        assertEquals(outcomes(reference),outcomes(batched));
        assertEquals(Set.of(BytecodeExplorer.Kind.COMPLETED,BytecodeExplorer.Kind.CHECK_FALSE),
            batched.witnesses().stream().map(w->w.outcome().kind()).collect(java.util.stream.Collectors.toSet()));
        assertTrue(batched.metrics().transitions()<reference.metrics().transitions());
        assertTrue(batched.metrics().states()<reference.metrics().states());
    }

    /** Losing a private reference is a scheduling boundary because another actor can refresh sharing.
     * @param directory compiler workspace
     * @throws Exception for fixture failures */
    @Test void referenceRootLossPreservesSharingHistory(@TempDir Path directory) throws Exception {
        var actors=List.of(
            "@Actor(id=0)public void first(){Throwable n=new Throwable();x=1;n=null;} @Actor(id=1)public void second(){if(x==1){Object dummy=new Object();y=1;}}",
            "@Actor(id=0)public void first(){{Throwable n=new Throwable();x=1;}int n=0;} @Actor(id=1)public void second(){if(x==1){Object dummy=new Object();y=1;}}",
            "public synchronized Throwable create(){x=1;return new Throwable();} @Actor(id=0)public void first(){create();} @Actor(id=1)public void second(){synchronized(this){if(x==1){Object dummy=new Object();y=1;}}}",
            "public synchronized Throwable create(){x=1;return new Throwable();} @Actor(id=0)public void first(){if(create()!=null){x=1;}} @Actor(id=1)public void second(){synchronized(this){if(x==1){Object dummy=new Object();y=1;}}}",
            "public synchronized Object[] create(){x=1;return new Object[1];} @Actor(id=0)public void first(){int n=create().length;} @Actor(id=1)public void second(){synchronized(this){if(x==1){Object dummy=new Object();y=1;}}}",
            "public synchronized Throwable create(){x=1;return new Throwable();} @Actor(id=0)public void first(){boolean n=create() instanceof Throwable;} @Actor(id=1)public void second(){synchronized(this){if(x==1){Object dummy=new Object();y=1;}}}");
        for(int sample=0;sample<actors.size();sample++) {
            var classes=BytecodeFixtures.compile(directory.resolve("case"+sample),17,
                "@Model public class Fixture {public int x,y;public Fixture(){}"+actors.get(sample)+"}");
            compare(BytecodeLoader.load(classes,"Fixture",BytecodeLimits.defaults()));
        }
    }

    /** A valid bare constructor consumes its final uninitialized receiver after a visible write.
     * @param directory compiler workspace
     * @throws Exception for class transformation or fixture failures */
    @Test void bareObjectConstructorRetainsRootLossBoundary(@TempDir Path directory) throws Exception {
        var classes=BytecodeFixtures.compile(directory,17,"""
            @Model public class Fixture {public int x,y;public Fixture(){}
              @Actor(id=0)public void first(){new Object();x=1;}
              @Actor(id=1)public void second(){if(x==1){Object dummy=new Object();y=1;}}}
            """);
        var parser=java.lang.classfile.ClassFile.of();var file=classes.resolve("Fixture.class");
        java.nio.file.Files.write(file,parser.transformClass(parser.parse(java.nio.file.Files.readAllBytes(file)),
            java.lang.classfile.ClassTransform.transformingMethodBodies(method->method.methodName().equalsString("first"),(code,item)->{
                if(item instanceof java.lang.classfile.instruction.NewObjectInstruction) {
                    code.with(item).aload(0).iconst_1().putfield(java.lang.constant.ClassDesc.of("Fixture"),"x",java.lang.constant.ConstantDescs.CD_int);
                } else if(item instanceof java.lang.classfile.instruction.InvokeInstruction || item instanceof java.lang.classfile.instruction.ReturnInstruction)code.with(item);
            })));
        assertEquals("1:1",BytecodeFixtures.oracle(directory,classes,"Fixture f=new Fixture();f.first();f.second();System.out.print(f.x+\":\"+f.y);"));
        compare(BytecodeLoader.load(classes,"Fixture",BytecodeLimits.defaults()));
    }

    /** A failing cast consumes the sole operand root after an independently observable write.
     * Both private and promoted terminal histories are legal, depending on the competing allocation.
     * @param directory compiler workspace
     * @throws Exception for fixture transformation or execution failures */
    @Test void failingCastPreservesTerminalSharingHistories(@TempDir Path directory) throws Exception {
        var classes=BytecodeFixtures.compile(directory,17,"""
            @Model public class Fixture {public int flag,seen;public Fixture(){}
              @Actor(id=0)public void first(){Fixture unused=(Fixture)(Object)new Throwable();}
              @Actor(id=1)public void second(){if(flag==1){int[] allocation=new int[1];seen=1;}}}
            """);
        var parser=java.lang.classfile.ClassFile.of();var file=classes.resolve("Fixture.class");
        java.nio.file.Files.write(file,parser.transformClass(parser.parse(java.nio.file.Files.readAllBytes(file)),
            java.lang.classfile.ClassTransform.transformingMethodBodies(method->method.methodName().equalsString("first"),(code,item)->{
                if(item instanceof java.lang.classfile.instruction.TypeCheckInstruction)
                    code.aload(0).iconst_1().putfield(java.lang.constant.ClassDesc.of("Fixture"),"flag",java.lang.constant.ConstantDescs.CD_int);
                code.with(item);
            })));
        assertEquals("1:java.lang.ClassCastException",BytecodeFixtures.oracle(directory,classes,
            "Fixture f=new Fixture();try{f.first();}catch(ClassCastException fault){System.out.print(f.flag+\":\"+fault.getClass().getName());}"));
        var program=BytecodeLoader.load(classes,"Fixture",BytecodeLimits.defaults());compare(program);
        var seen=new BytecodeExplorer.Cell(BytecodeState.FIXTURE,new BytecodeProgram.FieldId("Fixture","seen","I"));
        for(var mode:BytecodeScheduler.Mode.values()) {
            var result=new BytecodeExplorer(program,mode).explore(100000);assertTrue(result.traversalComplete());
            assertTrue(result.witnesses().stream().anyMatch(w->new BytecodeState.Int(1).equals(w.outcome().shared().get(seen))
                && !w.state().heap.get(new BytecodeState.Ref(0,0)).shared));
            assertTrue(result.witnesses().stream().anyMatch(w->new BytecodeState.Int(1).equals(w.outcome().shared().get(seen))
                && w.state().heap.get(new BytecodeState.Ref(0,0)).shared));
        }
    }

    /** Static increments retain both real shared-read/write outcomes in both scheduling modes.
     * @param directory compiler workspace
     * @throws Exception for fixture failures */
    @Test void staticIncrementKeepsReadWriteBoundaries(@TempDir Path directory) throws Exception {
        var classes=BytecodeFixtures.compile(directory,26,"""
            @Model public class Fixture {public static int x;public Fixture(){}
              @Actor(id=0)public void first(){x++;} @Actor(id=1)public void second(){x++;}
              @Check public boolean valid(){return x==2;}}
            """);
        var program=BytecodeLoader.load(classes,"Fixture",BytecodeLimits.defaults());compare(program);
        var cell=new BytecodeExplorer.Cell(BytecodeState.NULL,new BytecodeProgram.FieldId("Fixture","x","I"));
        for(var mode:BytecodeScheduler.Mode.values()) {
            var result=new BytecodeExplorer(program,mode).explore(100000);assertEquals(2,result.witnesses().size());
            var observations=new TreeMap<Integer,BytecodeExplorer.Kind>();
            for(var witness:result.witnesses())observations.put(((BytecodeState.Int)witness.outcome().shared().get(cell)).value(),witness.outcome().kind());
            assertEquals(Map.of(1,BytecodeExplorer.Kind.CHECK_FALSE,2,BytecodeExplorer.Kind.COMPLETED),observations);
        }
    }

    /** Independent opcode enumeration covers terminal competition and bounded loops in both modes.
     * @param directory compiler workspace
     * @throws Exception for fixture failures */
    @Test void adversarialOutcomesMatchIndependentInstructions(@TempDir Path directory) throws Exception {
        var cases=List.of(
            "@Actor(id=0)public void first(){while(true){}} @Actor(id=1)public void second(){x=1;} @Check(when=Check.When.ALWAYS)public boolean valid(){return x==0;}",
            "@Actor(id=0)public void first(){int a=1;int b=a+1;int c=1/(b-2);} @Actor(id=1)public void second(){x=1;}",
            "@Actor(id=0)public void first(){int a=1;int b=a+1;int c=b+1;} @Actor(id=1)public void second(){x=1;} @Check(when=Check.When.ALWAYS)public boolean valid(){return x==0;}",
            "@Actor(id=0)public void first(){if(x==0){int a=1;int b=a+1;x=b;}else{x=3;}} @Actor(id=1)public void second(){x=1;}",
            "@Actor(id=0)public void first(){x++;} @Actor(id=1)public void second(){x++;} @Check public boolean valid(){return x==2;}",
            "@Actor(id=0)public void first(){x=1;} @Actor(id=1)public void second(){x=2;} @Check(when=Check.When.ALWAYS)public boolean valid(){return 1/(x-1)>0;}",
            "@Actor(id=0)public void first(){synchronized(this){synchronized(this){x=1/zero;}}} @Actor(id=1)public synchronized void second(){x=1;}",
            "@Actor(id=0)public void first(){} @Actor(id=1)public void second(){}"
        );
        for(int i=0;i<cases.size();i++) {
            var classes=BytecodeFixtures.compile(directory.resolve("case"+i),26,
                "@Model public class Fixture {public int x;public int zero;public Fixture(){}"+cases.get(i)+"}");
            var defaults=BytecodeLimits.defaults();
            var limits=new BytecodeLimits(i==7 ? 1 : i==2 ? 5 : i==6 ? 64 : 12,128,8192,32,64,256,4096,128,8192,defaults.checkInstructions());
            var program=BytecodeLoader.load(classes,"Fixture",limits);compare(program);
            var result=new BytecodeExplorer(program,BytecodeScheduler.Mode.BATCHED).explore(100000);
            var kinds=result.witnesses().stream().map(w->w.outcome().kind()).collect(java.util.stream.Collectors.toSet());
            if(i==0 || i==2)assertEquals(Set.of(BytecodeExplorer.Kind.CUTOFF,BytecodeExplorer.Kind.CHECK_FALSE),kinds);
            if(i==1 || i==6)assertEquals(Set.of(BytecodeExplorer.Kind.UNCAUGHT_EXCEPTION),kinds);
            // The faulting actor releases its depths; a competitor may acquire before failure delivery.
            if(i==6)for(var witness:result.witnesses())assertTrue(witness.state().heap.values().stream().noneMatch(object->object.monitorOwner==0));
            if(i==7)assertEquals(Set.of(BytecodeExplorer.Kind.COMPLETED),kinds);
        }
    }

    /** Saved deterministic seed generates tiny programs; failures shrink by deleting actor statements.
     * @param directory compiler workspace
     * @throws Exception for fixture failures */
    @Test void generatedProgramsMatchIndependentInstructions(@TempDir Path directory) throws Exception {
        var random=new Random(94002L);var choices=List.of("x++;","x=1;","x=2;","if(x==0)x=3;","int local=1;local++;");
        for(int sample=0;sample<8;sample++) {
            var actors=new ArrayList<List<String>>();
            for(int actor=0;actor<2;actor++) {
                var statements=new ArrayList<String>();for(int operation=0;operation<2;operation++)statements.add(choices.get(random.nextInt(choices.size())));
                actors.add(statements);
            }
            // Each statement has its own lexical block, so generated local declarations never collide.
            if(!matchesGenerated(directory.resolve("seed94002-case"+sample),actors)) {
                int attempt=0;
                for(int actor=0;actor<2;actor++)for(int index=actors.get(actor).size()-1;index>=0;index--) {
                    String removed=actors.get(actor).remove(index);
                    if(matchesGenerated(directory.resolve("shrink"+sample+"-"+attempt++),actors))actors.get(actor).add(index,removed);
                }
                fail("Independent outcome mismatch; seed94002 case"+sample+" minimized actors="+actors);
            }
        }
    }

    /** Builds a generated fixture and compares outcomes, allowing deletion-based failure shrinking.
     * @param directory isolated compiler workspace
     * @param actors two ordered statement lists
     * @return whether independent/reference/batched observations agree
     * @throws Exception for fixture failures */
    private static boolean matchesGenerated(Path directory,List<List<String>> actors) throws Exception {
        var source=new StringBuilder("@Model public class Fixture {public int x;public Fixture(){}");
        for(int actor=0;actor<2;actor++) {
            source.append("@Actor(id=").append(actor).append(")public void actor").append(actor).append("(){");
            for(String statement:actors.get(actor))source.append('{').append(statement).append('}');source.append('}');
        }
        source.append('}');var classes=BytecodeFixtures.compile(directory,26,source.toString());
        var program=BytecodeLoader.load(classes,"Fixture",new BytecodeLimits(16,128,8192,32,64,256,4096,128,8192,1024));
        var expected=BytecodeInstructionOracle.enumerate(program);
        for(var mode:BytecodeScheduler.Mode.values()) {
            var result=new BytecodeExplorer(program,mode).explore(100000);
            if(!result.traversalComplete() || !expected.equals(independentProjection(result)))return false;
        }
        return true;
    }

    /** Compares both production granularities with the independently scheduled/encoded reference.
     * @param program actual interpreter fixture */
    static void compare(BytecodeProgram program) {
        var expected=BytecodeInstructionOracle.enumerate(program);
        for(var mode:BytecodeScheduler.Mode.values()) {
            var result=new BytecodeExplorer(program,mode).explore(100000);assertTrue(result.traversalComplete());
            assertEquals(expected,independentProjection(result),mode.toString());
        }
    }

    /** Projects public-to-this-package results into the oracle's observation vocabulary.
     * This reads existing evidence and does not build the reference's outcomes or state keys.
     * @param result actual search
     * @return independently comparable observations */
    static Set<BytecodeInstructionOracle.Observation> independentProjection(BytecodeExplorer.Result result) {
        var observations=new HashSet<BytecodeInstructionOracle.Observation>();
        for(var witness:result.witnesses()) {
            var outcome=witness.outcome();var fields=new TreeMap<String,BytecodeState.Value>();
            outcome.shared().forEach((cell,value)->fields.put(BytecodeInstructionOracle.cellKey(cell.object(),cell.field()),value));
            var waits=outcome.waits().stream().map(wait->BytecodeInstructionOracle.waitKey(wait.actor(),wait.owner(),wait.monitor(),wait.depth())).toList();
            var check=outcome.observer();String observer=check==null ? null : BytecodeInstructionOracle.checkKey(check.kind().toString(),check.check(),check.location(),check.fault(),check.cutoff());
            observations.add(new BytecodeInstructionOracle.Observation(outcome.kind().toString(),outcome.actor(),outcome.fault(),outcome.cutoff(),observer,waits,Map.copyOf(fields)));
        }
        return Set.copyOf(observations);
    }

    /** Normalizes through the search result's explicit observable outcome contract.
     * Independent enumeration below uses its own state keys and observation logic.
     * @param result actual run
     * @return normalized outcome set */
    private static Set<BytecodeExplorer.Outcome> outcomes(BytecodeExplorer.Result result) {
        return result.witnesses().stream().map(BytecodeExplorer.Witness::outcome).collect(java.util.stream.Collectors.toSet());
    }
}
