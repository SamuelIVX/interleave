/** Opposing setup-allocated monitors admit a wait cycle and successful serialized schedules. */
@Model public class Fixture {
    public final Object left=new Object();
    public final Object right=new Object();
    public int x;
    /** Creates both distinct lock identities during interpreted setup. */
    public Fixture() {}
    /** Acquires left before right. */
    @Actor(id=0) public void first() { synchronized(left) { synchronized(right) { x++; } } }
    /** Acquires right before left. */
    @Actor(id=1) public void second() { synchronized(right) { synchronized(left) { x++; } } }
    /** Declares the final shared counter property; deadlocked prefixes do not run FINAL checks. */
    @Check public boolean valid() { return x==2; }
}
