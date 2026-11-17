/** Two real Java bytecode actors race through separate shared reads and writes. */
package examples.bytecode;

import dev.samhb.interleave.bytecode.Actor;
import dev.samhb.interleave.bytecode.Check;
import dev.samhb.interleave.bytecode.Model;

/** A final declared check distinguishes the lost increment from successful execution. */
@Model
public final class LostUpdate {
    /** Shared model counter. */
    public int counter;

    /** Initializes the fixture through interpreted setup. */
    public LostUpdate() {}

    /** Performs one nonatomic increment. */
    @Actor(id=0) public void first(){counter++;}

    /** Competes with the other increment. */
    @Actor(id=1) public void second(){counter++;}

    /** Checks only after both actors return normally.
     * @return whether neither update was lost */
    @Check public boolean bothUpdatesSurvive(){return counter==2;}
}
