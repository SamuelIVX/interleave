// Body failure and two close failures retain the primary and reverse-close suppression order.
/** Application exception whose message constructor remains interpreted up to its trusted superclass. */
class Failure extends RuntimeException {
    /** Preserves the explicit interned message reference.
     * @param message modeled literal message */
    Failure(String message) { super(message); }
}
/** Application callback; close effects and failure execute as ordinary interpreted bytecode. */
class Resource implements AutoCloseable {
    private final Fixture fixture;
    private final int id;
    /** Captures the shared fixture and deterministic close-order marker.
     * @param fixture owning shared fixture
     * @param id close-order marker */
    Resource(Fixture fixture,int id) { this.fixture=fixture;this.id=id; }
    /** Records close order, then raises the selected application failure. */
    public void close() { fixture.order=fixture.order*10+id;throw new Failure(id==2 ? "second" : "first"); }
}
/** One actor with observable primary/suppressed identities and exact literal-message results. */
@Model public class Fixture {
    public int order;
    public Throwable primary;
    public Throwable[] suppressed;
    public String first,second;
    /** Constructs the fixture without resource creation or executable static initialization. */
    public Fixture() {}
    /** Interprets resource callbacks in reverse order and retains their suppression order. */
    @Actor(id=0) public void actor() {
        try(Resource firstResource=new Resource(this,1);Resource secondResource=new Resource(this,2)) {
            throw new Failure("body");
        }catch(Failure failure) {
            primary=failure;suppressed=failure.getSuppressed();
            first=((Failure)suppressed[0]).getMessage();second=((Failure)suppressed[1]).getMessage();
        }
    }
    /** Observes final cleanup and explicit String identities without synchronization or allocation.
     * @return whether both close failures were retained in reverse close order */
    @Check public boolean valid() { return primary instanceof Failure && ((Failure)primary).getMessage()=="body" && order==21 && first=="second" && second=="first"; }
}
