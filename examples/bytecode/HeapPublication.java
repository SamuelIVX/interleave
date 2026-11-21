/** Actor-created aliased arrays and application objects checked through closed interface dispatch. */
package examples.bytecode;

import dev.samhb.interleave.bytecode.Actor;
import dev.samhb.interleave.bytecode.Check;
import dev.samhb.interleave.bytecode.Model;

/** A published graph retains the same object and array identities in evidence and replay. */
@Model
public class HeapPublication {
    /** Pure supported interface dispatch over the closed application hierarchy. */
    interface Read {
        /** Reads the aliased payload.
         * @return payload value */
        int read();
    }
    /** An application object containing a reference cycle and aliased array. */
    static final class Node implements Read {
        /** Aliased value storage. */
        int[] values;
        /** Cycle retained by the published heap. */
        Node next;
        /** Initializes interpreted application fields. */
        Node(){values=new int[1];values[0]=7;next=this;}
        /** Reads a value through a preserved alias.
         * @return first payload cell */
        public int read(){return next.values[0];}
    }
    /** Shared graph root. */
    Read published;
    /** Public interpreted fixture constructor. */
    public HeapPublication(){}
    /** Builds privately and publishes the entire cycle at one reference write. */
    @Actor(id=0) public void publish(){published=new Node();}
    /** Observes fully initialized aliases after normal actor termination.
     * @return whether the shared graph has the expected value */
    @Check public boolean valid(){return published.read()==7;}
}
