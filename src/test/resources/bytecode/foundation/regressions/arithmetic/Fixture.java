/** Saved sequential regression: MIN division, signed shifts and narrowing remain JVM int operations. */
@Model public class Fixture {
    public int value;
    /** Starts with the JVM-default integer value. */
    public Fixture() {}
    /** Writes value using overflow, signed/unsigned shifts and primitive narrowing. */
    @Actor(id=0) public void actor() {
        int x=-2147483648, minus=-1, shift=33;
        value=(x/minus)+(x%minus)+(minus>>>shift)+(byte)minus+(char)minus+(short)65535;
    }
}
