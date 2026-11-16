/** Two explicit actors race through separate shared read/write bytecodes. */
@Model public class Fixture {
    public int x;
    /** Starts with x=0 and no actor allocations. */
    public Fixture() {}
    /** Reads and writes the shared counter. */
    @Actor(id=0) public void first() { x++; }
    /** Competes with the first actor's increment. */
    @Actor(id=1) public void second() { x++; }
}
