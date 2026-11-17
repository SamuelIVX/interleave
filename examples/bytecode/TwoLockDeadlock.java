/** Opposing setup-allocated Java monitors admit a concrete two-actor wait cycle. */
package examples.bytecode;

import dev.samhb.interleave.bytecode.Actor;
import dev.samhb.interleave.bytecode.Check;
import dev.samhb.interleave.bytecode.Model;

/** Uses constructor initialization because executable application class initializers are unsupported. */
@Model
public final class TwoLockDeadlock {
    /** First deterministic setup monitor. */
    public final Object left;
    /** Second deterministic setup monitor. */
    public final Object right;
    /** Shared completion counter. */
    public int counter;

    /** Allocates distinct monitors in interpreted setup, before actors start. */
    public TwoLockDeadlock(){left=new Object();right=new Object();}

    /** Acquires left before right. */
    @Actor(id=0) public void first(){synchronized(left){synchronized(right){counter++;}}}

    /** Acquires right before left. */
    @Actor(id=1) public void second(){synchronized(right){synchronized(left){counter++;}}}

    /** Normal schedules complete both increments; deadlocked schedules never run FINAL checks.
     * @return whether both actors completed their protected work */
    @Check public boolean bothActorsFinish(){return counter==2;}
}
