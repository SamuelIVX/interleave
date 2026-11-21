// Actor-created reference array retains an alias cycle after publication.
@Model public class Fixture {
    Object[] published;
    int seen;
    /** Constructs the shared fixture without target allocations. */
    public Fixture() {}
    /** Builds a private reference-array self-cycle before publishing its shared root. */
    @Actor(id=0) public void publish() { Object[] a=new Object[1]; a[0]=a; published=a; }
    /** Performs an independent shared write competing with graph publication. */
    @Actor(id=1) public void compete() { seen=1; }
}
