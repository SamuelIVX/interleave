/** Independent JVM checks and public replay for the widened primitive profile. */
package dev.samhb.interleave.bytecode;

import dev.samhb.interleave.InterleaveRunner;
import dev.samhb.interleave.TraceRecord;
import dev.samhb.interleave.format.BytecodeProgramLoader;
import dev.samhb.interleave.search.TraceReplayer;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

class BytecodePrimitiveTest {
    /** Category-two arguments retain their two local slots and shifts mask the int distance.
     * The public snapshot and replay must retain Long.MIN_VALUE without JSON numeric coercion.
     * @param directory compiler/JVM workspace
     * @throws Exception for fixture compilation or execution failures */
    @Test void longArgumentsAndShiftsReplayLosslessly(@TempDir Path directory) throws Exception {
        var classes=BytecodeFixtures.compile(directory,17,"""
            @Model public class Fixture {public long result;public Fixture(){}
              static long shift(long value,int distance){return (value<<1)>>>distance;}
              long select(long value,int marker){return marker==7 ? value : 0L;}
              @Actor(id=0)public void actor(){result=select(shift(-4611686018427387904L,64),7);}}
            """);
        // -2^62 shifted left once is -2^63; the long shift distance64 masks to zero.
        assertEquals("-9223372036854775808",BytecodeFixtures.oracle(directory,classes,
            "Fixture f=new Fixture();f.actor();System.out.print(f.result);"));
        var program=BytecodeProgramLoader.load(classes,"Fixture");
        var result=InterleaveRunner.builder().build().runBytecode(program);
        assertFalse(result.hasViolation());assertFalse(result.hasIncomplete());
        assertEquals(1,result.completedTraces().size());
        var trace=result.completedTraces().getFirst();
        var value=trace.bytecodeEvidence().snapshots().getLast().fields().get("setup:0.Fixture.result:J");
        assertNotNull(value);assertEquals("LONG",value.type());assertEquals("-9223372036854775808",value.raw());
        var decoded=TraceRecord.fromJson(trace.toJson());
        assertEquals(trace.bytecodeEvidence(),decoded.bytecodeEvidence());
        assertEquals(trace.states(),new TraceReplayer().replayBytecode(program,decoded).states());
    }
    /** Wide computation, array storage and JVM saturation remain exact through public evidence.
     * @param directory compiler/oracle workspace
     * @throws Exception for fixture failures */
    @Test void floatingConversionsAndWideArraysMatchJvm(@TempDir Path directory) throws Exception {
        var classes=BytecodeFixtures.compile(directory,17,"""
            @Model public class Fixture {
              public long result;public int saturated,comparison;public float zero;public double infinity,negative;
              public long[] longs;public float[] floats;public double[] doubles;
              public Fixture(){longs=new long[1];floats=new float[1];doubles=new double[1];}
              static double divide(double a,double b){return a/b;}
              @Actor(id=0)public void actor(){
                long value=-9223372036854775808L,minus=-1L;longs[0]=value/minus;
                floats[0]=-0.0f;doubles[0]=-0.0;negative=doubles[0];doubles[0]=divide(1.0,0.0);
                result=longs[0];zero=floats[0];infinity=doubles[0];
                saturated=(int)infinity;comparison=divide(0.0,0.0)<1.0 ? 1 : 0;
              }}
            """);
        assertEquals("-9223372036854775808:2147483647:0:80000000:7ff0000000000000:8000000000000000",
            BytecodeFixtures.oracle(directory,classes,"""
                Fixture f=new Fixture();f.actor();System.out.print(f.result+":"+f.saturated+":"+f.comparison+":"+
                  Integer.toHexString(Float.floatToRawIntBits(f.zero))+":"+Long.toHexString(Double.doubleToRawLongBits(f.infinity))+":"+Long.toHexString(Double.doubleToRawLongBits(f.negative)));
                """));
        var program=BytecodeProgramLoader.load(classes,"Fixture");
        var result=InterleaveRunner.builder().build().runBytecode(program);
        assertFalse(result.hasIncomplete());assertFalse(result.hasViolation());
        var trace=result.completedTraces().getFirst();var fields=trace.bytecodeEvidence().snapshots().getLast().fields();
        assertEquals("-9223372036854775808",fields.get("setup:0.Fixture.result:J").raw());
        assertEquals("2147483647",fields.get("setup:0.Fixture.saturated:I").raw());
        assertEquals("0",fields.get("setup:0.Fixture.comparison:I").raw());
        assertEquals("80000000",fields.get("setup:0.Fixture.zero:F").raw());
        assertEquals("7ff0000000000000",fields.get("setup:0.Fixture.infinity:D").raw());
        assertEquals("8000000000000000",fields.get("setup:0.Fixture.negative:D").raw());
        assertEquals(trace.states(),new TraceReplayer().replayBytecode(program,TraceRecord.fromJson(trace.toJson())).states());
    }
    /** ConstantValue numeric tags must match their declared field types before any setup/search.
     * @param directory malformed class workspace
     * @throws Exception for fixture failures */
    @Test void mismatchedNumericConstantValuesReject(@TempDir Path directory) throws Exception {
        var classes=BytecodeFixtures.compile(directory,26,"@Model public class Fixture {public static final long C=1L;public Fixture(){}@Actor(id=0)public void actor(){}}");
        var file=classes.resolve("Fixture.class");var api=java.lang.classfile.ClassFile.of();
        java.nio.file.Files.write(file,api.transformClass(api.parse(java.nio.file.Files.readAllBytes(file)),(builder,element)->{
            if(element instanceof java.lang.classfile.FieldModel field && field.fieldName().equalsString("C"))
                builder.transformField(field,(out,item)->{
                    if(item instanceof java.lang.classfile.attribute.ConstantValueAttribute)out.with(java.lang.classfile.attribute.ConstantValueAttribute.of(1));
                    else out.with(item);
                });
            else builder.with(element);
        }));
        assertThrows(BytecodeLoadException.class,()->BytecodeLoader.load(classes,"Fixture",BytecodeLimits.defaults()));
    }

    /** Raw computational ints narrow at byte/char/short fields and IRETURN, not at invocation entry.
     * @param directory generated bytecode workspace
     * @throws Exception for fixture/oracle failures */
    @Test void narrowFieldsAndReturnsMatchJvm(@TempDir Path directory) throws Exception {
        String[] types={"byte","char","short"},descriptors={"B","C","S"};
        int[] inputs={1000,-1,65535},expected={-24,65535,-1};
        for(int index=0;index<types.length;index++)for(boolean field:java.util.List.of(false,true)) {
            int raw=inputs[index];String descriptor=descriptors[index];var work=directory.resolve(types[index]+field);
            var classes=BytecodeFixtures.compile(work,17,"@Model public class Fixture {public "+(field ? types[index] : "int")+" result;public Fixture(){} static "+types[index]+" helper(){return 0;}@Actor(id=0)public void actor(){result=helper();}}");
            if(field)BytecodeOpcodeTest.replace(classes,"actor",code->code.aload(0).loadConstant(raw).putfield(java.lang.constant.ClassDesc.of("Fixture"),"result",java.lang.constant.ClassDesc.ofDescriptor(descriptor)).return_());
            else BytecodeOpcodeTest.replace(classes,"helper",code->code.loadConstant(raw).ireturn());
            var program=BytecodeLoader.load(classes,"Fixture",BytecodeLimits.defaults());
            assertEquals(new BytecodeState.Int(expected[index]),BytecodeFixtures.run(program,program.initialState(),0).fixtureField("result"));
            assertEquals(Integer.toString(expected[index]),BytecodeFixtures.oracle(work,classes,"Fixture f=new Fixture();f.actor();System.out.print((int)f.result);"));
        }
    }

    /** New arithmetic retains read/write races, fault competition and compiler cleanup in both modes.
     * @param directory compiler workspace
     * @throws Exception for fixture failures */
    @Test void wideConcurrentOutcomesMatchInstructionEnumeration(@TempDir Path directory) throws Exception {
        var bodies=java.util.List.of(
            "public long x;@Actor(id=0)public void a(){x++;}@Actor(id=1)public void b(){x++;}@Check public boolean valid(){return x==2L;}",
            "public double[] x=new double[1];@Actor(id=0)public void a(){x[0]+=1.0;}@Actor(id=1)public void b(){x[0]+=1.0;}@Check public boolean valid(){return x[0]==2.0;}",
            "public int bad;public long result;@Actor(id=0)public void a(){long a=1,b=0;result=a/b;}@Actor(id=1)public void b(){bad=1;}@Check(when=Check.When.ALWAYS)public boolean valid(){return bad==0;}",
            "public Object lock=new Object();public long result;@Actor(id=0)public void a(){synchronized(lock){long a=1,b=0;result=a/b;}}@Actor(id=1)public void b(){synchronized(lock){result=7;}}");
        for(int sample=0;sample<bodies.size();sample++) {
            var classes=BytecodeFixtures.compile(directory.resolve("concurrent"+sample),17,"@Model public class Fixture {public Fixture(){}"+bodies.get(sample)+"}");
            BytecodeBatchingOracleTest.compare(BytecodeLoader.load(classes,"Fixture",BytecodeLimits.defaults()));
        }
    }

    /** A wide store can discard a reference from its second destination slot.
     * That root loss remains schedulable because another actor can trigger sharing closure.
     * @param directory generated bytecode workspace
     * @throws Exception for fixture failures */
    @Test void wideStoreRetainsSecondSlotRootLossBoundary(@TempDir Path directory) throws Exception {
        var classes=BytecodeFixtures.compile(directory,26,"""
            class Cell {int value=7;Cell(){}}
            @Model public class Fixture {public int flag;public Fixture(){}
              @Actor(id=0)public void a(){Cell c=new Cell();flag=1;}
              @Actor(id=1)public void b(){if(flag==1){Object o=new Object();}}}
            """);
        BytecodeOpcodeTest.replace(classes,"a",code->code.new_(java.lang.constant.ClassDesc.of("Cell")).dup()
            .invokespecial(java.lang.constant.ClassDesc.of("Cell"),"<init>",java.lang.constant.MethodTypeDesc.ofDescriptor("()V")).astore(2)
            .aload(0).iconst_1().putfield(java.lang.constant.ClassDesc.of("Fixture"),"flag",java.lang.constant.ConstantDescs.CD_int)
            .lconst_0().lstore(1).return_());
        var program=BytecodeLoader.load(classes,"Fixture",BytecodeLimits.defaults());BytecodeBatchingOracleTest.compare(program);
        for(var mode:BytecodeScheduler.Mode.values()) {
            var outcomes=new BytecodeExplorer(program,mode).explore(100000).witnesses();
            assertTrue(outcomes.stream().anyMatch(w->w.state().heap.get(new BytecodeState.Ref(0,0)).shared));
            assertTrue(outcomes.stream().anyMatch(w->!w.state().heap.get(new BytecodeState.Ref(0,0)).shared));
        }
    }

    /** Wide return at the exact final instruction succeeds; one less omits the actor return.
     * @param directory compiler workspace
     * @throws Exception for fixture failures */
    @Test void wideHelperHonorsExactInstructionLimit(@TempDir Path directory) throws Exception {
        var classes=BytecodeFixtures.compile(directory,17,"@Model public class Fixture {public long result;public Fixture(){} static long one(){return 1;} @Actor(id=0)public void actor(){result=one();}}");
        for(int allowance:new int[]{5,6}) {
            var limits=new BytecodeLimits(allowance,128,8192,32,64,256,4096,128,8192,1024);
            var program=BytecodeLoader.load(classes,"Fixture",limits);var end=BytecodeFixtures.run(program,program.initialState(),0);
            var execution=end.executions.get(0);assertEquals(allowance,execution.instructions);assertEquals(new BytecodeState.LongValue(1),end.fixtureField("result"));
            if(allowance==6)assertEquals(BytecodeState.Phase.TERMINATED,execution.phase);
            else {assertEquals(BytecodeState.Phase.CUTOFF,execution.phase);assertEquals(new BytecodeLocation("Fixture","actor","()V",7),execution.cutoff.location());}
        }
    }

    /** Primitive type tokens must not collide with legal single-letter application class names.
     * @param directory compiler workspace
     * @throws Exception for fixture/oracle failures */
    @Test void primitiveTypeNamesDoNotChangeReferenceWidths(@TempDir Path directory) throws Exception {
        var classes=BytecodeFixtures.compile(directory,26,"""
            class D {} class J {}
            @Model public class Fixture {public int flag;public D saved,other;public J j,k;public Fixture(){}
              @Actor(id=0)public void actor(){D a=flag==0 ? new D() : new D();saved=(other=a);J b=flag==0 ? new J() : new J();j=(k=b);}}
            """);
        var program=BytecodeLoader.load(classes,"Fixture",BytecodeLimits.defaults());var state=BytecodeFixtures.run(program,program.initialState(),0);
        assertEquals(BytecodeState.Phase.TERMINATED,state.executions.get(0).phase);
        assertEquals(state.fixtureField("saved"),state.fixtureField("other"));assertEquals(state.fixtureField("j"),state.fixtureField("k"));
        assertEquals("true:true",BytecodeFixtures.oracle(directory,classes,"""
            Fixture f=new Fixture();f.actor();System.out.print((f.saved==f.other)+":"+(f.j==f.k));
            """));
    }

    /** Raw noncanonical NaNs survive ConstantValue and LDC transport without JDK accessor collapse.
     * Quiet payloads avoid platform-dependent signaling-NaN arithmetic in the independent oracle.
     * @param directory compiler/byte-patch workspace
     * @throws Exception for fixture/oracle/native stream failures */
    @Test void rawNanPayloadsSurviveAllTransports(@TempDir Path directory) throws Exception {
        var classes=BytecodeFixtures.compile(directory,26,"""
            @Model public class Fixture {
              public static final float F=1.00000011920928955078125f;
              public static final double D=1.0000000000000002;
              public float single;public double dual;public Fixture(){}
              static float f(){return F;}static double d(){return D;}
              @Actor(id=0)public void actor(){single=f();dual=d();}}
            """);
        var file=classes.resolve("Fixture.class");byte[] bytes=java.nio.file.Files.readAllBytes(file);
        var input=java.nio.ByteBuffer.wrap(bytes);input.position(8);int count=Short.toUnsignedInt(input.getShort()),patched=0;
        for(int entry=1;entry<count;entry++)switch(Byte.toUnsignedInt(input.get())) {
            case 1->input.position(input.position()+Short.toUnsignedInt(input.getShort())+2);
            case 3,9,10,11,12,17,18->input.position(input.position()+4);
            case 4->{int offset=input.position();if(input.getInt()==0x3f800001){input.putInt(offset,0x7fc12345);patched++;}}
            case 5->{input.position(input.position()+8);entry++;}
            case 6->{int offset=input.position();if(input.getLong()==0x3ff0000000000001L){input.putLong(offset,0x7ff823456789abcdL);patched++;}entry++;}
            case 7,8,16,19,20->input.position(input.position()+2);
            case 15->input.position(input.position()+3);
            default->throw new AssertionError("Unexpected fixture pool tag");
        }
        assertEquals(2,patched);java.nio.file.Files.write(file,bytes);
        assertEquals("7fc12345:7ff823456789abcd",BytecodeFixtures.oracle(directory,classes,"""
            Fixture f=new Fixture();f.actor();System.out.print(Integer.toHexString(Float.floatToRawIntBits(f.single))+":"+Long.toHexString(Double.doubleToRawLongBits(f.dual)));
            """));
        var program=BytecodeProgramLoader.load(classes,"Fixture");var result=InterleaveRunner.builder().build().runBytecode(program);
        assertFalse(result.hasIncomplete());assertFalse(result.hasViolation());var trace=result.completedTraces().getFirst();
        var fields=trace.bytecodeEvidence().snapshots().getLast().fields();
        assertEquals("7fc12345",fields.get("static.Fixture.F:F").raw());assertEquals("7fc12345",fields.get("setup:0.Fixture.single:F").raw());
        assertEquals("7ff823456789abcd",fields.get("static.Fixture.D:D").raw());assertEquals("7ff823456789abcd",fields.get("setup:0.Fixture.dual:D").raw());
        assertEquals(trace.states(),new TraceReplayer().replayBytecode(program,TraceRecord.fromJson(trace.toJson())).states());
        var changed=TraceRecord.fromJson(trace.toJson().replace("7fc12345","7fc12346"));
        assertThrows(dev.samhb.interleave.search.CorruptedTraceException.class,()->new TraceReplayer().replayBytecode(program,changed));
        var older=TraceRecord.fromJson(trace.toJson().replace("BYTECODE-SC-4","BYTECODE-SC-3"));
        assertThrows(dev.samhb.interleave.search.CorruptedTraceException.class,()->new TraceReplayer().replayBytecode(program,older));
        var output=new java.io.ByteArrayOutputStream();try(var stream=new java.io.ObjectOutputStream(output)){stream.writeObject(trace);}
        try(var stream=new java.io.ObjectInputStream(new java.io.ByteArrayInputStream(output.toByteArray()))) {
            assertEquals(trace.bytecodeEvidence(),((TraceRecord)stream.readObject()).bytecodeEvidence());
        }
        java.nio.file.Files.createDirectories(Path.of("build"));java.nio.file.Files.writeString(Path.of("build/bytecode-primitive-nan.json"),result.toJson());
    }

    /** Literal long edges exercise overflow, masked shifts, bit operations and comparisons.
     * @param directory compiler workspace
     * @throws Exception for fixture failures */
    @Test void longOpcodeEdgesHaveLiteralExpectations(@TempDir Path directory) throws Exception {
        var classes=BytecodeFixtures.compile(directory,26,"""
            @Model public class Fixture {public long a,b,result;public int operation;public Fixture(){}
              @Actor(id=0)public void actor(){switch(operation){
                case 0:result=a+b;break;case 1:result=a-b;break;case 2:result=a*b;break;
                case 3:result=a/b;break;case 4:result=a%b;break;case 5:result=a<<(int)b;break;
                case 6:result=a>>(int)b;break;case 7:result=a>>>(int)b;break;case 8:result=a&b;break;
                case 9:result=a|b;break;case 10:result=a^b;break;case 11:result=-a;break;
                default:result=a>b ? 1 : a==b ? 0 : -1;}}}
            """);
        long[][] cases={{9223372036854775807L,1,-9223372036854775808L},{-9223372036854775808L,1,9223372036854775807L},
            {4294967296L,4294967296L,0},{-9223372036854775808L,-1,-9223372036854775808L},{-9223372036854775808L,-1,0},
            {1,65,2},{-9223372036854775808L,65,-4611686018427387904L},{-9223372036854775808L,65,4611686018427387904L},
            {3855,255,15},{3840,255,4095},{3855,255,4080},{-9223372036854775808L,0,-9223372036854775808L},
            {-9223372036854775808L,9223372036854775807L,-1},{5,5,0},{6,5,1}};
        var program=BytecodeLoader.load(classes,"Fixture",BytecodeLimits.defaults());
        for(int operation=0;operation<cases.length;operation++) {
            var state=program.initialState();put(state,"a","J",new BytecodeState.LongValue(cases[operation][0]));
            put(state,"b","J",new BytecodeState.LongValue(cases[operation][1]));put(state,"operation","I",new BytecodeState.Int(operation));
            var end=BytecodeFixtures.run(program,state,0);
            assertEquals(BytecodeState.Phase.TERMINATED,end.executions.get(0).phase);
            assertEquals(new BytecodeState.LongValue(cases[operation][2]),end.fixtureField("result"),"operation"+operation);
        }
    }

    /** Every legal category-two stack form agrees with manual ordering and an independent JVM.
     * @param directory generated bytecode workspace
     * @throws Exception for fixture/oracle failures */
    @Test void categoryTwoStackFormsMatchJvm(@TempDir Path directory) throws Exception {
        var cases=java.util.List.of(
            new StackCase("IJ","I",java.lang.classfile.CodeBuilder::pop2,1),
            new StackCase("J","JJ",java.lang.classfile.CodeBuilder::dup2,11),
            new StackCase("JI","IJI",java.lang.classfile.CodeBuilder::dup_x2,212),
            new StackCase("IJ","JIJ",java.lang.classfile.CodeBuilder::dup2_x1,212),
            new StackCase("JII","IIJII",java.lang.classfile.CodeBuilder::dup2_x2,23123),
            new StackCase("IIJ","JIIJ",java.lang.classfile.CodeBuilder::dup2_x2,3123),
            new StackCase("JJ","JJJ",java.lang.classfile.CodeBuilder::dup2_x2,212),
            new StackCase("D","DD",java.lang.classfile.CodeBuilder::dup2,11),
            new StackCase("DI","IDI",java.lang.classfile.CodeBuilder::dup_x2,212));
        for(int index=0;index<cases.size();index++) {
            var example=cases.get(index);var work=directory.resolve("stack"+index);
            var classes=BytecodeFixtures.compile(work,26,"@Model public class Fixture {public int result;public Fixture(){} @Actor(id=0)public void actor(){result=kernel();} static int kernel(){return 0;}}");
            BytecodeOpcodeTest.replace(classes,"kernel",code->{
                for(int n=0;n<example.inputs().length();n++)switch(example.inputs().charAt(n)) {
                    case 'J'->code.loadConstant((long)n+1);case 'D'->code.loadConstant((double)n+1);default->code.loadConstant(n+1);
                }
                example.operation().accept(code);
                for(int n=example.outputs().length()-1;n>=0;n--)switch(example.outputs().charAt(n)) {
                    case 'J'->code.lstore(n*2);case 'D'->code.dstore(n*2);default->code.istore(n*2);
                }
                code.iconst_0();
                for(int n=0;n<example.outputs().length();n++) {
                    code.bipush(10).imul();
                    switch(example.outputs().charAt(n)){case 'J'->code.lload(n*2).l2i();case 'D'->code.dload(n*2).d2i();default->code.iload(n*2);}
                    code.iadd();
                }
                code.ireturn();
            });
            var program=BytecodeLoader.load(classes,"Fixture",BytecodeLimits.defaults());
            assertEquals(new BytecodeState.Int(example.expected()),BytecodeFixtures.run(program,program.initialState(),0).fixtureField("result"));
            assertEquals(Integer.toString(example.expected()),BytecodeFixtures.oracle(work,classes,"Fixture f=new Fixture();f.actor();System.out.print(f.result);"));
        }
    }

    /** @param inputs bottom-to-top computational types, numbered from one
     * @param outputs resulting bottom-to-top types
     * @param operation verified stack transformation
     * @param expected independently written decimal order */
    private record StackCase(String inputs,String outputs,java.util.function.Consumer<java.lang.classfile.CodeBuilder> operation,int expected) {}

    /** Assigns a test input through its exact declared field identity.
     * @param state owned test execution
     * @param name fixture field
     * @param descriptor declared JVM type
     * @param value independently specified input */
    private static void put(BytecodeState state,String name,String descriptor,BytecodeState.Value value) {
        state.heap.get(BytecodeState.FIXTURE).fields.put(new BytecodeProgram.FieldId("Fixture",name,descriptor),value);
    }
}
