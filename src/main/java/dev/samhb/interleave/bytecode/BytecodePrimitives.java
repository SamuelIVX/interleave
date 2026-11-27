/** JVM primitive values and arithmetic, shared by both scheduling modes. */
package dev.samhb.interleave.bytecode;

import java.lang.classfile.Opcode;
import java.util.EnumSet;
import java.util.Set;
import static dev.samhb.interleave.bytecode.BytecodeState.*;

/** Uses host primitive arithmetic only; no target methods or conversions are invoked. */
final class BytecodePrimitives {
    /** Prevents construction of this stateless opcode family. */
    private BytecodePrimitives() {}

    /** Closed numeric opcode set, also used for loader admission. */
    static final Set<Opcode> OPCODES=Set.copyOf(EnumSet.of(
        Opcode.IADD,Opcode.ISUB,Opcode.IMUL,Opcode.IDIV,Opcode.IREM,Opcode.INEG,
        Opcode.ISHL,Opcode.ISHR,Opcode.IUSHR,Opcode.IAND,Opcode.IOR,Opcode.IXOR,
        Opcode.LADD,Opcode.LSUB,Opcode.LMUL,Opcode.LDIV,Opcode.LREM,Opcode.LNEG,
        Opcode.LSHL,Opcode.LSHR,Opcode.LUSHR,Opcode.LAND,Opcode.LOR,Opcode.LXOR,Opcode.LCMP,
        Opcode.FADD,Opcode.FSUB,Opcode.FMUL,Opcode.FDIV,Opcode.FREM,Opcode.FNEG,Opcode.FCMPL,Opcode.FCMPG,
        Opcode.DADD,Opcode.DSUB,Opcode.DMUL,Opcode.DDIV,Opcode.DREM,Opcode.DNEG,Opcode.DCMPL,Opcode.DCMPG,
        Opcode.I2B,Opcode.I2C,Opcode.I2S,Opcode.I2L,Opcode.I2F,Opcode.I2D,
        Opcode.L2I,Opcode.L2F,Opcode.L2D,Opcode.F2I,Opcode.F2L,Opcode.F2D,Opcode.D2I,Opcode.D2L,Opcode.D2F));

    /** Returns the zero value of a primitive field/array or null for a reference descriptor.
     *
     * @param descriptor exact JVM descriptor
     *
     * @return typed default without allocation */
    static Value zero(String descriptor) {
        return switch(descriptor) {
            case "J" -> new LongValue(0);case "F" -> new FloatBits(0);case "D" -> new DoubleBits(0);
            case "Z","B","C","S","I" -> new Int(0);default -> NULL;
        };
    }

    /** Applies JVM field/return narrowing; invocation arguments keep computational int values.
     *
     * @param descriptor declared primitive or reference descriptor
     *
     * @param value computational value
     *
     * @return narrowed or unchanged value */
    static Value narrow(String descriptor,Value value) {
        return switch(descriptor) {
            case "Z" -> new Int(integer(value)&1);case "B" -> new Int((byte)integer(value));
            case "C" -> new Int((char)integer(value));case "S" -> new Int((short)integer(value));default -> value;
        };
    }

    /** Converts an already parsed non-floating constant without executing target code.
     * Floating pool constants must instead use their frozen raw bits.
     *
     * @param value nonnull JDK numeric constant value
     *
     * @return exact primitive value
     * @throws IllegalArgumentException for nonnumeric constants */
    static Value constant(Object value) {
        return switch(value) {
            case Integer number -> new Int(number);case Long number -> new LongValue(number);
            case Float number -> floating(number);case Double number -> floating(number);
            default -> throw new IllegalArgumentException("Not a primitive constant");
        };
    }

    /** Executes one admitted numeric operation after its fuel has been charged.
     * FP division follows IEEE semantics; integral zero divisors raise modeled ArithmeticException.
     *
     * @param frame owned operand stack
     *
     * @param code admitted numeric opcode */
    static void execute(BytecodeFrame frame,Opcode code) {
        Value right=frame.pop();
        switch(code) {
            case INEG -> frame.push(new Int(-integer(right)));
            case LNEG -> frame.push(new LongValue(-wide(right)));
            case FNEG -> frame.push(new FloatBits(((FloatBits)right).bits()^0x80000000));
            case DNEG -> frame.push(new DoubleBits(((DoubleBits)right).bits()^0x8000000000000000L));
            case I2B -> frame.push(new Int((byte)integer(right)));
            case I2C -> frame.push(new Int((char)integer(right)));
            case I2S -> frame.push(new Int((short)integer(right)));
            case I2L -> frame.push(new LongValue(integer(right)));
            case I2F -> frame.push(floating((float)integer(right)));
            case I2D -> frame.push(floating((double)integer(right)));
            case L2I -> frame.push(new Int((int)wide(right)));
            case L2F -> frame.push(floating((float)wide(right)));
            case L2D -> frame.push(floating((double)wide(right)));
            case F2I -> frame.push(new Int((int)single(right)));
            case F2L -> frame.push(new LongValue((long)single(right)));
            case F2D -> frame.push(floating((double)single(right)));
            case D2I -> frame.push(new Int((int)dual(right)));
            case D2L -> frame.push(new LongValue((long)dual(right)));
            case D2F -> frame.push(floating((float)dual(right)));
            case LSHL,LSHR,LUSHR -> {
                long left=wide(frame.pop());int distance=integer(right);
                frame.push(new LongValue(switch(code){case LSHL->left<<distance;case LSHR->left>>distance;default->left>>>distance;}));
            }
            default -> binary(frame,code,frame.pop(),right);
        }
    }

    /** Computes a binary operation with Java's specified primitive overflow and FP conversions.
     * Comparisons deliberately equate signed zeros; wrapper equality is a separate contract.
     *
     * @param frame destination stack
     *
     * @param code admitted binary opcode
     *
     * @param left left operand
     *
     * @param right right operand */
    private static void binary(BytecodeFrame frame,Opcode code,Value left,Value right) {
        if(left instanceof Int) {
            int a=integer(left),b=integer(right);
            if((code==Opcode.IDIV || code==Opcode.IREM) && b==0)throw new OpcodeExecutor.Trap("java/lang/ArithmeticException",null);
            frame.push(new Int(switch(code) {
                case IADD->a+b;case ISUB->a-b;case IMUL->a*b;case IDIV->a/b;case IREM->a%b;
                case ISHL->a<<b;case ISHR->a>>b;case IUSHR->a>>>b;case IAND->a&b;case IOR->a|b;case IXOR->a^b;
                default->throw new IllegalStateException("Unvalidated int opcode "+code);
            }));
        } else if(left instanceof LongValue) {
            long a=wide(left),b=wide(right);
            if((code==Opcode.LDIV || code==Opcode.LREM) && b==0)throw new OpcodeExecutor.Trap("java/lang/ArithmeticException",null);
            if(code==Opcode.LCMP){frame.push(new Int(Long.compare(a,b)));return;}
            frame.push(new LongValue(switch(code) {
                case LADD->a+b;case LSUB->a-b;case LMUL->a*b;case LDIV->a/b;case LREM->a%b;
                case LAND->a&b;case LOR->a|b;case LXOR->a^b;
                default->throw new IllegalStateException("Unvalidated long opcode "+code);
            }));
        } else if(left instanceof FloatBits) {
            float a=single(left),b=single(right);
            if(code==Opcode.FCMPL || code==Opcode.FCMPG) {
                frame.push(new Int(Float.isNaN(a) || Float.isNaN(b) ? code==Opcode.FCMPL ? -1 : 1 : a>b ? 1 : a==b ? 0 : -1));return;
            }
            frame.push(floating(switch(code) {
                case FADD->a+b;case FSUB->a-b;case FMUL->a*b;case FDIV->a/b;case FREM->a%b;
                default->throw new IllegalStateException("Unvalidated float opcode "+code);
            }));
        } else {
            double a=dual(left),b=dual(right);
            if(code==Opcode.DCMPL || code==Opcode.DCMPG) {
                frame.push(new Int(Double.isNaN(a) || Double.isNaN(b) ? code==Opcode.DCMPL ? -1 : 1 : a>b ? 1 : a==b ? 0 : -1));return;
            }
            frame.push(floating(switch(code) {
                case DADD->a+b;case DSUB->a-b;case DMUL->a*b;case DDIV->a/b;case DREM->a%b;
                default->throw new IllegalStateException("Unvalidated double opcode "+code);
            }));
        }
    }

    /** Reads an int computational value.
     * @param value verified int
     * @return signed int */
    static int integer(Value value){return ((Int)value).value();}
    /** Reads a long computational value.
     * @param value verified long
     * @return signed long */
    static long wide(Value value){return ((LongValue)value).value();}
    /** Decodes raw float bits for arithmetic only.
     * @param value verified float
     * @return primitive float */
    static float single(Value value){return Float.intBitsToFloat(((FloatBits)value).bits());}
    /** Decodes raw double bits for arithmetic only.
     * @param value verified double
     * @return primitive double */
    static double dual(Value value){return Double.longBitsToDouble(((DoubleBits)value).bits());}
    /** Captures an arithmetic result with a canonical NaN for replay across host architectures.
     * @param value result
     * @return raw float bits */
    static FloatBits floating(float value){return new FloatBits(Float.floatToIntBits(value));}
    /** Captures an arithmetic result with a canonical NaN for replay across host architectures.
     * @param value result
     * @return raw double bits */
    static DoubleBits floating(double value){return new DoubleBits(Double.doubleToLongBits(value));}
}
