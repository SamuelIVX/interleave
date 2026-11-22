/** Compiled two-resource cleanup: body failure remains primary and close failures are suppressed. */
package examples.bytecode;

import dev.samhb.interleave.bytecode.Actor;
import dev.samhb.interleave.bytecode.Check;
import dev.samhb.interleave.bytecode.Model;

/** A bounded sequential exception example whose resource callbacks remain interpreted. */
@Model
public class ResourceCleanup {
    /** Reverse resource close order. */
    public int order;
    /** Preserved body exception. */
    public Throwable primary;
    /** Ordered close failures. */
    public Throwable[] suppressed;
    /** Explicit interned close messages. */
    public String first,second;

    /** Creates the shared fixture without executable class initialization. */
    public ResourceCleanup() {}

    /** Runs real compiled try-with-resources cleanup and catches the body failure. */
    @Actor(id=0)
    public void actor() {
        try(Resource a=new Resource(this,1);Resource b=new Resource(this,2)) {
            throw new Failure("body");
        }catch(Failure failure) {
            primary=failure;suppressed=failure.getSuppressed();
            first=((Failure)suppressed[0]).getMessage();second=((Failure)suppressed[1]).getMessage();
        }
    }

    /** Checks reverse cleanup, suppression order and exact message reference identity.
     * @return whether both close failures accompany the original body exception */
    @Check
    public boolean valid() {return primary instanceof Failure && ((Failure)primary).getMessage()=="body" && order==21 && suppressed.length==2 && first=="second" && second=="first";}

    /** Application exception constructed through the narrow trusted superclass intrinsic. */
    private static class Failure extends RuntimeException {
        /** Preserves a literal message reference.
         * @param message explicit modeled message */
        Failure(String message){super(message);}
    }

    /** Application close callback with observable effects and a modeled throw. */
    private static class Resource implements AutoCloseable {
        /** Shared fixture receiving cleanup effects. */
        final ResourceCleanup fixture;
        /** Stable resource order. */
        final int id;
        /** Stores callback inputs without executing host target code.
         * @param fixture shared fixture
         * @param id close-order marker */
        Resource(ResourceCleanup fixture,int id){this.fixture=fixture;this.id=id;}
        /** Records reverse close order and raises the selected close failure. */
        @Override public void close(){fixture.order=fixture.order*10+id;throw new Failure(id==2 ? "second" : "first");}
    }
}
