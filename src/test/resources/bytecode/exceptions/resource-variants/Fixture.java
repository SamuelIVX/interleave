/** Explicit close failures use interpreted application constructors and literal messages. */
class Failure extends RuntimeException {
    /** Retains an explicit message.
     * @param message modeled literal */
    Failure(String message){super(message);}
}
/** A body failure with suppression and stack-trace writing disabled. */
class Quiet extends Throwable {
    /** Selects the supported protected Throwable constructor. */
    Quiet(){super(null,null,false,false);}
}
/** Configurable callback whose effects always reveal reverse close order. */
class Resource implements AutoCloseable {
    final Fixture fixture;
    final int id;
    /** Stores callback inputs.
     * @param fixture shared fixture
     * @param id close-order marker */
    Resource(Fixture fixture,int id){this.fixture=fixture;this.id=id;}
    /** Records cleanup before optionally throwing its explicit failure. */
    public void close(){fixture.order=fixture.order*10+id;if((fixture.failures&(1<<id))!=0)throw new Failure(id==2 ? "second" : "first");}
}
/** Test substitutions select failures without changing the interpreted callback implementation. */
@Model public class Fixture {
    public int order,failures=6;
    public boolean bodyFail=true,disabled=false;
    public Throwable primary;
    public Throwable[] suppressed;
    public String message,first,second;
    /** Constructs bounded initial configuration. */
    public Fixture(){}
    /** Preserves normal completion or the selected body/close primary and suppressed failures. */
    @Actor(id=0)public void actor(){
        try(Resource a=new Resource(this,1);Resource b=new Resource(this,2)){
            if(disabled)throw new Quiet();
            if(bodyFail)throw new Failure("body");
        }catch(Throwable failure){
            primary=failure;suppressed=failure.getSuppressed();
            if(failure instanceof Failure)message=((Failure)failure).getMessage();
            if(suppressed.length>0)first=((Failure)suppressed[0]).getMessage();
            if(suppressed.length>1)second=((Failure)suppressed[1]).getMessage();
        }
    }
}
