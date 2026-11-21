// Actor-local cycle promoted through a shared root, with a competing shared write.
/** Private actor graph with a cycle and one value cell. */
class Node {
    Node next; int value;
    /** Creates default field storage; actor instructions install the private cycle. */
    Node() {}
}
@Model public class Fixture {
    Node published;
    int seen;
    /** Constructs the shared fixture without target allocations. */
    public Fixture() {}
    /** Builds and initializes a private object cycle before publishing its shared root. */
    @Actor(id=0) public void publish() { Node n=new Node(); n.next=n; n.value=7; published=n; }
    /** Performs an independent shared write competing with graph publication. */
    @Actor(id=1) public void compete() { seen=1; }
}
