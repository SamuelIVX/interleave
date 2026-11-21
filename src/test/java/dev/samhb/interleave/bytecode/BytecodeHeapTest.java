/** Heap construction, JVM differential behavior and bounded allocation regressions. */
package dev.samhb.interleave.bytecode;

import java.nio.file.Path;
import java.nio.file.Files;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;
import static dev.samhb.interleave.bytecode.BytecodeState.*;

class BytecodeHeapTest {
    /** Actor construction, inherited hidden fields and aliases agree with the independent JVM.
     * @param directory isolated compiler workspace
     * @throws Exception for fixture/oracle failures */
    @Test void heapAliasesAndDispatchMatchJvm(@TempDir Path directory) throws Exception {
        var classes=BytecodeFixtures.compile(directory,26,"""
            class Base { int x=3; int value(){return x;} }
            class Child extends Base { int x=7; Child self; Child(){self=this;} int value(){return super.value()+x;} }
            @Model public class Fixture { public int result; public Fixture(){}
              @Actor(id=0) public void actor(){Child c=new Child();Base b=c;result=b.value()+c.self.x;}
            }
            """);
        var program=BytecodeLoader.load(classes,"Fixture",BytecodeLimits.defaults());
        var state=BytecodeFixtures.run(program,program.initialState(),0);
        assertEquals(Phase.TERMINATED,state.executions.get(0).phase);
        assertEquals(new Int(17),state.fixtureField("result"));
        assertEquals("17",BytecodeFixtures.oracle(directory,classes,"Fixture f=new Fixture();f.actor();System.out.println(f.result);"));
        assertEquals(1,state.executions.get(0).allocations);
        assertEquals(4,state.executions.get(0).cells);
    }
    /** Primitive/reference/ranked arrays retain JVM defaults, narrowing, aliases and dynamic types.
     * @param directory compiler workspace
     * @throws Exception for fixture/oracle failures */
    @Test void arraysAndCastsMatchJvm(@TempDir Path directory) throws Exception {
        var classes=BytecodeFixtures.compile(directory,26,"""
            @Model public class Fixture {public int result;public Object saved;public Fixture(){}
              @Actor(id=0) public void actor(){
                int[][] a=new int[2][3];a[1][2]=9;byte[] b=new byte[1];b[0]=(byte)255;
                char[] c=new char[1];c[0]=(char)65535;short[] s=new short[1];s[0]=(short)65535;
                boolean[] z=new boolean[1];z[0]=true;Object[] refs=new int[1][];refs[0]=a[1];
                Object v=refs;result=((int[])((Object[])v)[0])[2]+a.length+b[0]+c[0]+s[0]+(z[0]?1:0);
                if(v instanceof int[][])result++;saved=new long[2];
              }
            }
            """);
        var program=BytecodeLoader.load(classes,"Fixture",BytecodeLimits.defaults());
        var state=BytecodeFixtures.run(program,program.initialState(),0);
        assertEquals(Phase.TERMINATED,state.executions.get(0).phase);
        assertEquals(new Int(65546),state.fixtureField("result"));
        assertEquals("65546",BytecodeFixtures.oracle(directory,classes,"Fixture f=new Fixture();f.actor();System.out.println(f.result);"));
        assertEquals(9,state.executions.get(0).allocations);
        assertEquals(24,state.executions.get(0).cells);
    }

    /** Dynamic array/cast/null failures consume fuel and run compiler monitor cleanup.
     * @param directory compiler workspace
     * @throws Exception for fixture/oracle failures */
    @Test void arrayFaultsRemainDynamic(@TempDir Path directory) throws Exception {
        String[][] cases={
            {"int[] a=new int[-1];","java/lang/NegativeArraySizeException"},
            {"int[] a=new int[1];result=a[1];","java/lang/ArrayIndexOutOfBoundsException"},
            {"int[] a=new int[1];a[-1]=7;","java/lang/ArrayIndexOutOfBoundsException"},
            {"int[] a=null;result=a.length;","java/lang/NullPointerException"},
            {"int[] a=null;result=a[0];","java/lang/NullPointerException"},
            {"int[] a=null;a[0]=7;","java/lang/NullPointerException"},
            {"Object[] a=new Fixture[1];a[0]=new Object();","java/lang/ArrayStoreException"},
            {"Object a=new Object();Fixture f=(Fixture)a;","java/lang/ClassCastException"},
            {"Fixture f=null;result=f.read();","java/lang/NullPointerException"}
        };
        for(int i=0;i<cases.length;i++) {
            var work=directory.resolve("fault"+i);
            var classes=BytecodeFixtures.compile(work,26,"@Model public class Fixture { public int result;public Fixture(){} int read(){return 1;}"
                +"@Actor(id=0) public void actor(){synchronized(this){result=3;"+cases[i][0]+"}}}");
            var program=BytecodeLoader.load(classes,"Fixture",BytecodeLimits.defaults());
            var state=program.initialState();boolean chargedFault=false;
            for(int event=0;event<16384;event++) {
                var before=state.executions.get(0);int instructions=before.instructions;
                var next=OpcodeExecutor.step(program,state,0);state=next.state();var after=state.executions.get(0);
                if(before.phase==Phase.RUNNING && before.fault==null && after.phase==Phase.EXCEPTION) {
                    assertEquals(instructions+1,after.instructions);assertEquals(cases[i][1],after.fault.type());chargedFault=true;
                }
                if(next.kind()==OpcodeExecutor.Kind.TERMINAL)break;
            }
            assertTrue(chargedFault);var actor=state.executions.get(0);
            assertEquals(Phase.FAILED,actor.phase);assertEquals(cases[i][1],actor.fault.type());
            assertEquals(new Int(3),state.fixtureField("result"));assertEquals(0,state.heap.get(FIXTURE).monitorDepth);
            assertTrue(actor.instructions>0);assertEquals("actor",actor.fault.location().name());
            assertEquals(cases[i][1],BytecodeFixtures.oracle(work,classes,
                "try{new Fixture().actor();System.out.print(\"NO FAULT\");}catch(RuntimeException e){System.out.print(e.getClass().getName().replace('.', '/'));}"));
        }
    }

    /** Exact lengths/counts/cells and partial dimensions retain earlier permanent identities.
     * @param directory compiler workspace
     * @throws Exception for fixture failures */
    @Test void perAllocatorBudgetAndPartialDimensions(@TempDir Path directory) throws Exception {
        var classes=BytecodeFixtures.compile(directory,26,"""
            @Model public class Fixture { public Object saved;public Fixture(){}
              @Actor(id=0)public void actor(){saved=new int[2][2];}
            }
            """);
        var count=new BytecodeLimits(256,2,8192,32,64,256,4096,128,8192,1024);
        var program=BytecodeLoader.load(classes,"Fixture",count);var state=BytecodeFixtures.run(program,program.initialState(),0);
        var actor=state.executions.get(0);assertEquals(Phase.CUTOFF,actor.phase);assertEquals("ALLOCATIONS",actor.cutoff.reason());
        assertEquals(2,actor.allocations);assertEquals(6,actor.cells);assertEquals(2,actor.nextSequence);
        assertEquals(NULL,state.fixtureField("saved"));assertTrue(state.heap.containsKey(new Ref(0,0)));assertTrue(state.heap.containsKey(new Ref(0,1)));
        assertFalse(state.heap.containsKey(new Ref(0,2)));assertEquals(new Ref(0,1),state.heap.get(new Ref(0,0)).elements.get(0));
        var cells=new BytecodeLimits(256,128,5,32,64,256,4096,128,8192,1024);
        program=BytecodeLoader.load(classes,"Fixture",cells);state=BytecodeFixtures.run(program,program.initialState(),0);
        assertEquals("HEAP_CELLS",state.executions.get(0).cutoff.reason());assertEquals(3,state.executions.get(0).cells);
        assertEquals(1,state.executions.get(0).nextSequence);assertEquals(3,state.executions.get(0).cutoff.required());assertEquals(2,state.executions.get(0).cutoff.remaining());
        for(int length:new int[]{0,2,3}) {
            var input=BytecodeFixtures.compile(directory.resolve("length"+length),26,
                "@Model public class Fixture {public Object saved;public Fixture(){} @Actor(id=0)public void actor(){saved=new int["+length+"];}}");
            var limits=new BytecodeLimits(256,128,8192,32,2,256,4096,128,8192,1024);
            program=BytecodeLoader.load(input,"Fixture",limits);state=BytecodeFixtures.run(program,program.initialState(),0);
            if(length==3){assertEquals("ARRAY_LENGTH",state.executions.get(0).cutoff.reason());assertEquals(0,state.executions.get(0).nextSequence);}
            else {assertEquals(Phase.TERMINATED,state.executions.get(0).phase);assertEquals(1+length,state.executions.get(0).cells);}
        }
    }

    /** Formerly rejected constructor chaining and plain actor monitors are now fully interpreted.
     * @param directory compiler workspace
     * @throws Exception for fixture failures */
    @Test void constructorChainingAndActorMonitors(@TempDir Path directory) throws Exception {
        var classes=BytecodeFixtures.compile(directory,17,"""
            @Model public class Fixture {public int result;public Fixture(){this(4);}public Fixture(int x){result=x;}
              @Actor(id=0)public void actor(){Object lock=new Object();synchronized(lock){result+=read();}}
              private int read(){return 3;}
              @Check public boolean check(){return read()==3 && result==7;}
            }
            """);
        var program=BytecodeLoader.load(classes,"Fixture",BytecodeLimits.defaults());
        var state=BytecodeFixtures.run(program,program.initialState(),0);
        assertEquals(new Int(7),state.fixtureField("result"));assertEquals(1,state.executions.get(0).allocations);
        assertEquals(0,state.heap.get(new Ref(0,0)).monitorDepth);assertTrue(state.heap.get(new Ref(0,0)).initialized);
        assertEquals(BytecodeExplorer.Kind.COMPLETED,new BytecodeExplorer(program,BytecodeScheduler.Mode.BATCHED).explore(10000).witnesses().getFirst().outcome().kind());
    }

    /** Array-valued stores use the runtime component type and preserve the previous element on failure.
     * @param directory compiler workspace
     * @throws Exception for fixture/oracle failures */
    @Test void covariantArrayStorePreservesPriorElementOnFault(@TempDir Path directory) throws Exception {
        var classes=BytecodeFixtures.compile(directory,26,"""
            @Model public class Fixture{public Object[] saved;public Fixture(){}@Actor(id=0)public void actor(){
              Object[] a=new int[1][];a[0]=new int[2];saved=a;a[0]=new long[1];}}
            """);
        var program=BytecodeLoader.load(classes,"Fixture",BytecodeLimits.defaults());var state=BytecodeFixtures.run(program,program.initialState(),0);
        assertEquals(Phase.FAILED,state.executions.get(0).phase);assertEquals("java/lang/ArrayStoreException",state.executions.get(0).fault.type());
        var array=state.heap.get((Ref)state.fixtureField("saved"));var element=state.heap.get((Ref)array.elements.getFirst());
        assertEquals("[I",element.type);assertEquals(2,element.elements.size());
        assertEquals("java.lang.ArrayStoreException:2",BytecodeFixtures.oracle(directory,classes,
            "Fixture f=new Fixture();try{f.actor();}catch(RuntimeException e){System.out.print(e.getClass().getName()+\":\"+((int[])f.saved[0]).length);}"));
    }

    /** Successors and repeated runs own their array containers while retaining real alias identities.
     * @param directory compiler workspace
     * @throws Exception for fixture failures */
    @Test void arrayCopiesPreserveParentsAndCanonicalElements(@TempDir Path directory) throws Exception {
        var classes=BytecodeFixtures.compile(directory,26,"@Model public class Fixture{int[] saved=new int[1];public Fixture(){}@Actor(id=0)public void actor(){saved[0]=7;}}");
        var program=BytecodeLoader.load(classes,"Fixture",BytecodeLimits.defaults());var parent=program.initialState();var key=parent.canonicalBytes();
        var reference=(Ref)parent.fixtureField("saved");var result=BytecodeFixtures.run(program,parent,0);
        assertEquals(new Int(7),result.heap.get(reference).elements.getFirst());assertEquals(new Int(0),parent.heap.get(reference).elements.getFirst());
        assertArrayEquals(key,parent.canonicalBytes());assertArrayEquals(key,program.initialState().canonicalBytes());
        var changed=parent.copy();changed.heap.get(reference).elements.set(0,new Int(1));assertFalse(java.util.Arrays.equals(key,changed.canonicalBytes()));
        changed=parent.copy();changed.heap.get(reference).elements.add(new Int(0));assertFalse(java.util.Arrays.equals(key,changed.canonicalBytes()));
        assertEquals(new Int(7),BytecodeFixtures.run(program,program.initialState(),0).heap.get(reference).elements.getFirst());
    }

    /** Null casts preserve null and null instanceof remains false without a target fault.
     * @param directory compiler workspace
     * @throws Exception for fixture/oracle failures */
    @Test void nullTypeChecksMatchJvm(@TempDir Path directory) throws Exception {
        var classes=BytecodeFixtures.compile(directory,17,"@Model public class Fixture{public int result;public Fixture(){}@Actor(id=0)public void actor(){Object a=null;Fixture f=(Fixture)a;result=(f==null?1:0)+(a instanceof Fixture?2:0)+(a instanceof int[]?4:0);}}");
        var program=BytecodeLoader.load(classes,"Fixture",BytecodeLimits.defaults());var state=BytecodeFixtures.run(program,program.initialState(),0);
        assertEquals(Phase.TERMINATED,state.executions.get(0).phase);assertEquals(new Int(1),state.fixtureField("result"));
        assertEquals("1",BytecodeFixtures.oracle(directory,classes,"Fixture f=new Fixture();f.actor();System.out.print(f.result);"));
    }

    /** Early constructor publication preserves effects, charges and an unfinished object after a genuine fault.
     * @param directory compiler workspace
     * @throws Exception for fixture/oracle failures */
    @Test void publishedConstructorFaultRetainsPartialState(@TempDir Path directory) throws Exception {
        var classes=BytecodeFixtures.compile(directory,26,"""
            class Helper{int value;Helper(Fixture root){value=7;root.saved=this;int zero=0;value=1/zero;}}
            @Model public class Fixture{public Object saved;public int mark;public Fixture(){}@Actor(id=0)public void actor(){mark=3;new Helper(this);}}
            """);
        var program=BytecodeLoader.load(classes,"Fixture",BytecodeLimits.defaults());
        for(var mode:BytecodeScheduler.Mode.values()) {
            var scheduler=new BytecodeScheduler(program,mode);var state=program.initialState();
            for(int step=0;step<200 && state.executions.get(0).phase!=Phase.FAILED;step++)state=scheduler.advance(state,0).state();
            var execution=state.executions.get(0);assertEquals(Phase.FAILED,execution.phase);assertEquals("java/lang/ArithmeticException",execution.fault.type());
            assertEquals("Helper",execution.fault.location().owner());assertEquals("<init>",execution.fault.location().name());assertEquals(2,execution.fault.stack().size());
            assertEquals(new Int(3),state.fixtureField("mark"));var helper=state.heap.get((Ref)state.fixtureField("saved"));
            assertTrue(helper.shared);assertFalse(helper.initialized);assertEquals(new Int(7),helper.fields.get(new BytecodeProgram.FieldId("Helper","value","I")));
            assertEquals(1,execution.allocations);assertEquals(2,execution.cells);
        }
        assertEquals("3:7",BytecodeFixtures.oracle(directory,classes,"Fixture f=new Fixture();try{f.actor();}catch(ArithmeticException e){System.out.print(f.mark+\":\"+((Helper)f.saved).value);}"));
    }

    /** Verified constructors may reuse local zero; setup and actor object identity must survive that store.
     * @param directory compiler workspace
     * @throws Exception for fixture/oracle failures */
    @Test void constructorReceiverSurvivesLocalZeroReuse(@TempDir Path directory) throws Exception {
        var classes=BytecodeFixtures.compile(directory,26,"class Helper{Helper(){}}@Model public class Fixture{public Object saved;public Fixture(){}@Actor(id=0)public void actor(){saved=new Helper();}}");
        var parser=java.lang.classfile.ClassFile.of();
        for(String name:java.util.List.of("Fixture","Helper")) {
            Path target=classes.resolve(name+".class");
            Files.write(target,parser.transformClass(parser.parse(Files.readAllBytes(target)),(builder,element)->{
                if(element instanceof java.lang.classfile.MethodModel method && method.methodName().equalsString("<init>"))
                    builder.transformMethod(method,java.lang.classfile.MethodTransform.transformingCode((code,item)->{
                        if(item instanceof java.lang.classfile.Instruction instruction && instruction.opcode()==java.lang.classfile.Opcode.RETURN)code.aconst_null().astore(0);
                        code.with(item);
                    }));else builder.with(element);
            }));
        }
        var program=BytecodeLoader.load(classes,"Fixture",BytecodeLimits.defaults());var parent=program.initialState();var before=parent.canonicalBytes();
        var state=BytecodeFixtures.run(program,parent,0);assertEquals(Phase.TERMINATED,state.executions.get(0).phase);
        assertTrue(state.heap.get(FIXTURE).initialized);assertTrue(state.heap.get((Ref)state.fixtureField("saved")).initialized);
        assertArrayEquals(before,parent.canonicalBytes());
        assertEquals("true",BytecodeFixtures.oracle(directory,classes,"Fixture f=new Fixture();f.actor();System.out.print(f.saved instanceof Helper);"));
    }

}
