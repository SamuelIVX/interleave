/** Literal JVM numeric boundary vectors, independent of the arithmetic implementation. */
package dev.samhb.interleave.bytecode;

import java.lang.classfile.ClassFile;
import java.lang.constant.ClassDesc;
import java.lang.constant.MethodTypeDesc;
import java.util.List;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static java.lang.classfile.Opcode.*;
import static dev.samhb.interleave.bytecode.BytecodeState.*;

class BytecodePrimitiveArithmeticTest {
    /** Empty frame metadata for isolated instruction vectors; target execution stays interpreted. */
    private static final BytecodeMethod METHOD=method();

    /** Literal arithmetic/IEEE/cast expectations exercise every widened numeric opcode. */
    @Test void primitiveBoundaryVectors() {
        var cases=List.of(
            new Example(INEG,i(-7),i(7)),new Example(INEG,i(7),i(-7)),
            new Example(INEG,i(-2147483648),i(-2147483648)),
            new Example(LADD,j(-9223372036854775808L),j(9223372036854775807L),j(1)),
            new Example(LSUB,j(9223372036854775807L),j(-9223372036854775808L),j(1)),
            new Example(LMUL,j(0),j(4294967296L),j(4294967296L)),
            new Example(LDIV,j(-9223372036854775808L),j(-9223372036854775808L),j(-1)),
            new Example(LREM,j(0),j(-9223372036854775808L),j(-1)),
            new Example(LNEG,j(-9223372036854775808L),j(-9223372036854775808L)),
            new Example(LNEG,j(-7),j(7)),new Example(LNEG,j(7),j(-7)),
            new Example(LSHL,j(2),j(1),i(65)),new Example(LSHR,j(-1),j(-2),i(-1)),
            new Example(LUSHR,j(1),j(-2),i(-1)),
            new Example(LAND,j(15),j(3855),j(255)),new Example(LOR,j(4095),j(3840),j(255)),
            new Example(LXOR,j(4080),j(3855),j(255)),
            new Example(LCMP,i(-1),j(-9223372036854775808L),j(9223372036854775807L)),
            new Example(LCMP,i(0),j(5),j(5)),new Example(LCMP,i(1),j(6),j(5)),
            new Example(FADD,f(0x40700000),f(0x3fc00000),f(0x40100000)),
            new Example(FSUB,f(0xbf400000),f(0x3fc00000),f(0x40100000)),
            new Example(FMUL,f(0xc0400000),f(0xbfc00000),f(0x40000000)),
            new Example(FDIV,f(0x7f800000),f(0x3f800000),f(0)),
            new Example(FDIV,f(0xff800000),f(0x3f800000),f(0x80000000)),
            new Example(FREM,f(0x3fc00000),f(0x40b00000),f(0x40000000)),
            new Example(FNEG,f(0),f(0x80000000)),new Example(FNEG,f(0xffc12345),f(0x7fc12345)),
            new Example(FCMPL,i(-1),f(0x7fc12345),f(0x3f800000)),
            new Example(FCMPG,i(1),f(0x3f800000),f(0x7fc12345)),
            new Example(FCMPG,i(1),f(0x7fc12345),f(0x3f800000)),
            new Example(FCMPL,i(0),f(0),f(0x80000000)),new Example(FCMPG,i(0),f(0x80000000),f(0)),
            new Example(FCMPL,i(1),f(0x40000000),f(0x3f800000)),new Example(FCMPG,i(-1),f(0x3f800000),f(0x40000000)),
            new Example(DADD,d(0x400e000000000000L),d(0x3ff8000000000000L),d(0x4002000000000000L)),
            new Example(DSUB,d(0xbfe8000000000000L),d(0x3ff8000000000000L),d(0x4002000000000000L)),
            new Example(DMUL,d(0xc008000000000000L),d(0xbff8000000000000L),d(0x4000000000000000L)),
            new Example(DDIV,d(0x7ff0000000000000L),d(0x3ff0000000000000L),d(0)),
            new Example(DDIV,d(0xfff0000000000000L),d(0x3ff0000000000000L),d(0x8000000000000000L)),
            new Example(DREM,d(0x3ff8000000000000L),d(0x4016000000000000L),d(0x4000000000000000L)),
            new Example(DNEG,d(0),d(0x8000000000000000L)),new Example(DNEG,d(0xfff823456789abcdL),d(0x7ff823456789abcdL)),
            new Example(DCMPL,i(-1),d(0x7ff823456789abcdL),d(0x3ff0000000000000L)),
            new Example(DCMPG,i(1),d(0x3ff0000000000000L),d(0x7ff823456789abcdL)),
            new Example(DCMPG,i(1),d(0x7ff823456789abcdL),d(0x3ff0000000000000L)),
            new Example(DCMPL,i(0),d(0),d(0x8000000000000000L)),new Example(DCMPG,i(0),d(0x8000000000000000L),d(0)),
            new Example(DCMPL,i(1),d(0x4000000000000000L),d(0x3ff0000000000000L)),new Example(DCMPG,i(-1),d(0x3ff0000000000000L),d(0x4000000000000000L)),
            new Example(I2L,j(-2147483648L),i(-2147483648)),new Example(I2F,f(0x4b800000),i(16777217)),
            new Example(I2D,d(0x41dfffffffc00000L),i(2147483647)),new Example(L2I,i(-1),j(9223372036854775807L)),
            new Example(L2F,f(0x5f000000),j(9223372036854775807L)),new Example(L2D,d(0x43e0000000000000L),j(9223372036854775807L)),
            new Example(F2I,i(0),f(0x7fc12345)),new Example(F2L,j(0),f(0x7fc12345)),
            new Example(F2I,i(2147483647),f(0x7f800000)),new Example(F2I,i(-2147483648),f(0xff800000)),
            new Example(F2L,j(9223372036854775807L),f(0x7f800000)),new Example(F2L,j(-9223372036854775808L),f(0xff800000)),
            new Example(F2I,i(-1),f(0xbfe00000)),new Example(F2L,j(-1),f(0xbfe00000)),
            new Example(F2D,d(0x3ff8000000000000L),f(0x3fc00000)),
            new Example(D2I,i(0),d(0x7ff823456789abcdL)),new Example(D2L,j(0),d(0x7ff823456789abcdL)),
            new Example(D2I,i(2147483647),d(0x7ff0000000000000L)),new Example(D2I,i(-2147483648),d(0xfff0000000000000L)),
            new Example(D2L,j(9223372036854775807L),d(0x7ff0000000000000L)),new Example(D2L,j(-9223372036854775808L),d(0xfff0000000000000L)),
            new Example(D2I,i(-1),d(0xbffc000000000000L)),new Example(D2L,j(-1),d(0xbffc000000000000L)),
            new Example(D2F,f(0x7f800000),d(0x7fefffffffffffffL)),new Example(D2F,f(0),d(1)),
            new Example(D2F,f(0x3fc00000),d(0x3ff8000000000000L)));
        for(var example:cases) {
            var frame=new BytecodeFrame(METHOD,List.of());frame.stack.addAll(List.of(example.operands()));
            BytecodePrimitives.execute(frame,example.opcode());
            assertEquals(List.of(example.expected()),frame.stack,example.opcode().toString());
        }
    }

    /** Immediate numeric constants retain their computational type and exact finite value. */
    @Test void immediateConstantsKeepPrimitiveTypes() {
        assertEquals(i(7),BytecodePrimitives.constant(7));
        assertEquals(j(7),BytecodePrimitives.constant(7L));
        assertEquals(f(0x3f800000),BytecodePrimitives.constant(1.0f));
        assertEquals(d(0x3ff0000000000000L),BytecodePrimitives.constant(1.0d));
        assertThrows(IllegalArgumentException.class,()->BytecodePrimitives.constant("unsupported"));
    }

    /** Profile arithmetic NaNs are canonical even when an input carries a different payload.
     * This is a permitted JVM NaN choice, fixed here for replay across host architectures. */
    @Test void arithmeticNaNsHaveDeterministicProfileBits() {
        for(var code:List.of(FADD,FSUB,FMUL,FDIV,FREM)) {
            var frame=new BytecodeFrame(METHOD,List.of());frame.push(f(0x7fc12345));frame.push(f(0x3f800000));
            BytecodePrimitives.execute(frame,code);assertEquals(List.of(f(0x7fc00000)),frame.stack,code.toString());
        }
        for(var code:List.of(DADD,DSUB,DMUL,DDIV,DREM)) {
            var frame=new BytecodeFrame(METHOD,List.of());frame.push(d(0xfff823456789abcdL));frame.push(d(0x3ff0000000000000L));
            BytecodePrimitives.execute(frame,code);assertEquals(List.of(d(0x7ff8000000000000L)),frame.stack,code.toString());
        }
        var single=new BytecodeFrame(METHOD,List.of());single.push(f(0x7fc12345));BytecodePrimitives.execute(single,F2D);
        assertEquals(List.of(d(0x7ff8000000000000L)),single.stack);
        var dual=new BytecodeFrame(METHOD,List.of());dual.push(d(0xfff823456789abcdL));BytecodePrimitives.execute(dual,D2F);
        assertEquals(List.of(f(0x7fc00000)),dual.stack);
    }

    /** Integral division/remainder consume operands before raising their modeled fault. */
    @Test void integralZeroDivisorsTrap() {
        for(var code:List.of(IDIV,IREM,LDIV,LREM)) {
            var frame=new BytecodeFrame(METHOD,List.of());boolean wide=code==LDIV || code==LREM;
            frame.push(wide ? j(1) : i(1));frame.push(wide ? j(0) : i(0));
            var fault=assertThrows(OpcodeExecutor.Trap.class,()->BytecodePrimitives.execute(frame,code));
            assertEquals("java/lang/ArithmeticException",fault.getMessage());assertTrue(frame.stack.isEmpty());
        }
    }

    /** Raw representations and unusable wide locals remain distinct in canonical state. */
    @Test void rawValuesAndWideLocalOverlapRemainDistinct() {
        var keys=new java.util.HashSet<String>();
        for(Value value:List.of(i(0),j(0),f(0),f(0x80000000),f(0x7fc00000),f(0x7fc12345),d(0),d(0x8000000000000000L),d(0x7ff8000000000000L),d(0x7ff823456789abcdL))) {
            var state=new BytecodeState();state.start(0,METHOD,List.of());state.frame(0).push(value);
            assertTrue(keys.add(java.util.Base64.getEncoder().encodeToString(state.canonicalBytes())));
        }
        var frame=new BytecodeFrame(METHOD,List.of(j(1),d(2),i(3)));
        assertEquals(Top.SLOT,frame.locals.get(1));assertEquals(Top.SLOT,frame.locals.get(3));assertEquals(i(3),frame.locals.get(4));
        frame.store(1,i(7));assertFalse(frame.locals.containsKey(0));assertEquals(i(7),frame.locals.get(1));
        frame.store(1,j(8));assertFalse(frame.locals.containsKey(3));assertEquals(Top.SLOT,frame.locals.get(2));
        frame.store(1,i(9));assertFalse(frame.locals.containsKey(2));
    }

    /** @param opcode operation under test @param expected literal result @param operands bottom-to-top inputs */
    private record Example(java.lang.classfile.Opcode opcode,Value expected,Value... operands) {}
    /** Builds a static empty method solely to own unit-test frames. @return immutable method metadata */
    private static BytecodeMethod method() {
        var api=ClassFile.of();byte[] bytes=api.build(ClassDesc.of("PrimitiveUnit"),builder->builder.withMethodBody("unit",MethodTypeDesc.ofDescriptor("()V"),ClassFile.ACC_STATIC,code->code.return_()));
        return new BytecodeMethod("PrimitiveUnit",api.parse(bytes).methods().getFirst(),BytecodeInput.validate(bytes));
    }
    /** @param value literal signed int @return typed input/result */
    private static Int i(int value){return new Int(value);}
    /** @param value literal signed long @return typed input/result */
    private static LongValue j(long value){return new LongValue(value);}
    /** @param bits literal IEEE binary32 pattern @return bit-backed input/result */
    private static FloatBits f(int bits){return new FloatBits(bits);}
    /** @param bits literal IEEE binary64 pattern @return bit-backed input/result */
    private static DoubleBits d(long bits){return new DoubleBits(bits);}
}
