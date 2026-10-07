/** Small models with hand-enumerated positions shared by API and search contract tests. */
package dev.samhb.interleave.testsupport;

import dev.samhb.interleave.core.*;
import java.util.List;

/** Independent fixtures whose expected positions do not come from an explorer or encoder. */
public final class TestPrograms {
    /** Prevents instantiation of this fixture collection. */
    private TestPrograms() {}

    /**
     * Each of two threads writes its own flag once.
     * The four positions are neither write, thread 0 only, thread 1 only, and both writes.
     * @return fresh two-thread model with four distinct reachable positions
     */
    public static Program independentFlags() {
        return new Program(PetersonState.of(false, false, 0), List.of(
            new ModelThread(0, List.of(new WriteFlagStep(0, true))),
            new ModelThread(1, List.of(new WriteFlagStep(1, true)))));
    }
}
