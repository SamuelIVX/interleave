/** Literal arithmetic/stack expectations plus independent JVM checks for hand-built bytecode. */
package dev.samhb.interleave.bytecode;

import java.nio.file.*;
import java.lang.classfile.*;
import java.lang.constant.*;
import java.util.*;
import java.util.function.Consumer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;
import static dev.samhb.interleave.bytecode.BytecodeState.*;

class BytecodeOpcodeTest {
    /** All admitted integer operations have cheap literal regressions independent of the executor.
     *
     * @param directory compiler workspace
     * @throws Exception for fixture failures */
    @Test void integerOpcodeEdgesHaveLiteralExpectations(@TempDir Path directory) throws Exception {
        Path classes=BytecodeFixtures.compile(directory,26,"""
            @Model public class Fixture { public int left; public int right; public int operation; public int value; public Fixture() {}
              @Actor(id=0) public void actor() {
                switch(operation) {
                  case 0:value=left+right;break; case 1:value=left-right;break; case 2:value=left*right;break;
                  case 3:value=left/right;break; case 4:value=left%right;break; case 5:value=left<<right;break;
                  case 6:value=left>>right;break; case 7:value=left>>>right;break; case 8:value=left&right;break;
                  case 9:value=left|right;break; case 10:value=left^right;break; case 11:value=-left;break;
                  case 12:value=(byte)left;break; case 13:value=(char)left;break; default:value=(short)left;
                }
              }
            }
            """);
        int[][] examples={{2147483647,1,-2147483648},{-2147483648,1,2147483647},{65536,65536,0},
            {-2147483648,-1,-2147483648},{-2147483648,-1,0},{1,33,2},{-2147483648,33,-1073741824},
            {-2147483648,33,1073741824},{3855,255,15},{3840,255,4095},{3855,255,4080},
            {-2147483648,0,-2147483648},{255,0,-1},{-1,0,65535},{65535,0,-1}};
        var program=BytecodeLoader.load(classes,"Fixture",BytecodeLimits.defaults());
        for(int operation=0;operation<examples.length;operation++) {
            var state=program.initialState(); set(state,"left",examples[operation][0]); set(state,"right",examples[operation][1]); set(state,"operation",operation);
            var result=BytecodeFixtures.run(program,state,0);
            assertEquals(new Int(examples[operation][2]),result.fixtureField("value"),"operation"+operation);
            assertEquals(Phase.TERMINATED,result.executions.get(0).phase);
        }
    }

    /** Unary/two-operand comparisons cover all six signed relations on both branch outcomes.
     *
     * @param directory compiler workspace
     * @throws Exception for fixture failures */
    @Test void signedComparisonsHaveLiteralMasks(@TempDir Path directory) throws Exception {
        Path classes=BytecodeFixtures.compile(directory,17,"""
            @Model public class Fixture { public int left; public int right; public int value; public int unary; public Fixture() {}
              @Actor(id=0) public void actor() { unary=one(left);value=two(left,right); }
              static int one(int a){int r=0;if(a==0)r+=1;if(a!=0)r+=2;if(a<0)r+=4;if(a>=0)r+=8;if(a>0)r+=16;if(a<=0)r+=32;return r;}
              static int two(int a,int b){int r=0;if(a==b)r+=1;if(a!=b)r+=2;if(a<b)r+=4;if(a>=b)r+=8;if(a>b)r+=16;if(a<=b)r+=32;return r;}
            }
            """);
        int[][] examples={{-1,0,38,38},{0,0,41,41},{1,0,26,26},{0,-1,41,26},{0,1,41,38}};
        var program=BytecodeLoader.load(classes,"Fixture",BytecodeLimits.defaults());
        for(int[] example:examples) {
            var state=program.initialState(); set(state,"left",example[0]); set(state,"right",example[1]);
            var result=BytecodeFixtures.run(program,state,0);
            assertEquals(new Int(example[2]),result.fixtureField("unary")); assertEquals(new Int(example[3]),result.fixtureField("value"));
        }
    }


    /** Boolean field stores and method returns use the JVM low-bit rule even without compiler normalization.
     * @param directory compiler workspace
     * @throws Exception for fixture/oracle failures */
    @Test void booleanStoresAndReturnsUseJvmLowBit(@TempDir Path directory) throws Exception {
        for(String operation:List.of("field","return"))for(int[] example:new int[][]{{2,0},{3,1}}) {
            Path work=directory.resolve(operation+example[0]);var classes=BytecodeFixtures.compile(work,26,
                "@Model public class Fixture {public boolean flag;public Fixture(){} @Actor(id=0)public void actor(){flag=truth();} static boolean truth(){return false;} }");
            if(operation.equals("field"))replace(classes,"actor",code->code.aload(0).loadConstant(example[0])
                .putfield(ClassDesc.of("Fixture"),"flag",ConstantDescs.CD_boolean).return_());
            else replace(classes,"truth",code->code.loadConstant(example[0]).ireturn());
            var program=BytecodeLoader.load(classes,"Fixture",BytecodeLimits.defaults());
            assertEquals(new Int(example[1]),BytecodeFixtures.run(program,program.initialState(),0).fixtureField("flag"));
            assertEquals(example[1]==0 ? "false" : "true",BytecodeFixtures.oracle(work,classes,
                "Fixture f=new Fixture();f.actor();System.out.println(f.flag);"));
        }
    }

    /** Hand-built category-one stack forms agree with literal digit ordering and a real JVM.
     *
     * @param directory compiler workspace
     * @throws Exception for fixture/oracle failures */
    @Test void stackFormsAndWideLocalsMatchJvm(@TempDir Path directory) throws Exception {
        List<StackCase> cases=List.of(new StackCase(new int[]{1,2},CodeBuilder::pop,1,1),
            new StackCase(new int[]{1,2,3},CodeBuilder::pop2,1,1),new StackCase(new int[]{2},CodeBuilder::dup,2,22),
            new StackCase(new int[]{2,3},CodeBuilder::dup_x1,3,323),new StackCase(new int[]{2,3,4},CodeBuilder::dup_x2,4,4234),
            new StackCase(new int[]{2,3},CodeBuilder::dup2,4,2323),new StackCase(new int[]{2,3,4},CodeBuilder::dup2_x1,5,34234),
            new StackCase(new int[]{2,3,4,5},CodeBuilder::dup2_x2,6,452345),new StackCase(new int[]{2,3},CodeBuilder::swap,2,32));
        for(int index=0;index<cases.size();index++) {
            Path work=directory.resolve("stack"+index); Path classes=BytecodeFixtures.compile(work,26,
                "@Model public class Fixture {public int value;public Fixture() {} @Actor(id=0) public void actor(){value=kernel();} static int kernel(){return 0;} }");
            StackCase example=cases.get(index); replace(classes,"kernel",code->{
                for(int input:example.inputs()) code.loadConstant(input); example.operation().accept(code);
                for(int slot=0;slot<example.remaining();slot++) code.istore(slot);
                code.iconst_0(); int place=1;
                for(int slot=0;slot<example.remaining();slot++) {code.iload(slot).loadConstant(place).imul().iadd();place*=10;}
                code.ireturn();
            });
            var program=BytecodeLoader.load(classes,"Fixture",BytecodeLimits.defaults());
            var result=BytecodeFixtures.run(program,program.initialState(),0);
            assertEquals(new Int(example.expected()),result.fixtureField("value"));
            assertEquals(Integer.toString(example.expected()),BytecodeFixtures.oracle(work,classes,"Fixture f=new Fixture();f.actor();System.out.println(f.value);"));
        }
        Path classes=BytecodeFixtures.compile(directory.resolve("wide"),26,
            "@Model public class Fixture {public int value;public Fixture() {} @Actor(id=0) public void actor(){value=0;} }");
        replace(classes,"actor",code->code.iconst_5().istore(65534).iinc(65534,300).aload(0).iload(65534)
            .putfield(ClassDesc.of("Fixture"),"value",ConstantDescs.CD_int).return_());
        var program=BytecodeLoader.load(classes,"Fixture",BytecodeLimits.defaults());
        assertEquals(65535,program.methods.get(program.actors.getFirst()).code.maxLocals());
        assertEquals(new Int(305),BytecodeFixtures.run(program,program.initialState(),0).fixtureField("value"));
        assertEquals("305",BytecodeFixtures.oracle(directory.resolve("wide"),classes,"Fixture f=new Fixture();f.actor();System.out.println(f.value);"));
    }

    /**
     * @param inputs bottom-to-top operands
     * @param operation JVM stack form
     * @param remaining result depth
     * @param expected independent decimal ordering */
    private record StackCase(int[] inputs,Consumer<CodeBuilder> operation,int remaining,int expected) {}

    /** Replaces only one code attribute while preserving actor annotations and class identity.
     *
     * @param classes compiled directory
     * @param name method name
     * @param body test-owned bytecode generator
     *
     * @throws Exception for fixture failures */
    private static void replace(Path classes,String name,Consumer<CodeBuilder> body) throws Exception {
        Path file=classes.resolve("Fixture.class"); var parser=ClassFile.of();
        byte[] result=parser.transformClass(parser.parse(Files.readAllBytes(file)),(builder,element)-> {
            if(element instanceof MethodModel method && method.methodName().equalsString(name))
                builder.transformMethod(method,(output,item)-> {if(item instanceof CodeModel) output.withCode(body);else output.with(item);});
            else builder.with(element);
        });
        Files.write(file,result);
    }

    /**
     * @param state owned fixture state
     * @param field declared int field
     * @param value input value */
    private static void set(BytecodeState state,String field,int value) {
        state.heap.get(FIXTURE).fields.put(new BytecodeProgram.FieldId("Fixture",field,"I"),new Int(value));
    }
}
