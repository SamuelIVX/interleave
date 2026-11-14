/** Saved sequential regression: nested reentrant compiler cleanup preserves ordinary successful writes. */
@Model public class Fixture {
    public int value;
    public Object lock=new Object();
    /** Initializes the lock through its field initializer and leaves value at zero. */
    public Fixture() {}
    /** Holds the fixture and reentrant setup lock while computing and writing value. */
    @Actor(id=0) public synchronized void actor() {
        synchronized(lock) { synchronized(lock) {value=helper(4);} }
    }
    /** Applies int multiplication and subtraction to the saved input.
     * @param input integer input
     * @return input multiplied by seven, minus three */
    static int helper(int input) {return input*7-3;}
}
