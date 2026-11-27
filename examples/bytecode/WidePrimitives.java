/** Wide primitive execution with exact long values, signed zero and IEEE infinity. */
package examples.bytecode;

import dev.samhb.interleave.bytecode.Actor;
import dev.samhb.interleave.bytecode.Check;
import dev.samhb.interleave.bytecode.Model;

/** One fixed actor; no host execution or core-library calls are needed. */
@Model
public class WidePrimitives {
    /** Exact signed 64-bit result, outside JavaScript's exact integer range. */
    public long result;
    /** Stored binary32 negative zero. */
    public float negativeZero;
    /** Positive binary64 infinity, computed at runtime. */
    public double infinity;

    /** Creates the shared fixture. */
    public WidePrimitives() {}

    /** Exercises two-slot arguments, wide arrays, overflow and IEEE division. */
    @Actor(id=0)
    public void actor() {
        long[] values=new long[1];values[0]=shift(-4611686018427387904L,64);result=values[0];
        negativeZero=-0.0f;infinity=divide(1.0,0.0);
    }

    /** Checks exact long overflow and floating observations using supported pure bytecode.
     * @return whether all modeled arithmetic results have their expected values */
    @Check
    public boolean valid(){return result==-9223372036854775808L && 1.0f/negativeZero<0.0f && infinity>1.0e300;}

    /** Shifts once, then applies a JVM-masked long shift distance.
     * @param value signed long
     * @param distance distance following the wide argument's two local slots
     * @return shifted result */
    private static long shift(long value,int distance){return (value<<1)>>>distance;}

    /** Forces runtime IEEE division rather than a compiler-folded constant.
     * @param numerator dividend
     * @param denominator divisor
     * @return IEEE quotient, including infinity for zero divisor */
    private static double divide(double numerator,double denominator){return numerator/denominator;}
}
