/** Legacy assertion failures still probe enabledness against their isolated tentative writes. */
package dev.samhb.interleave.por;

import dev.samhb.interleave.core.*;
import java.util.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import static org.junit.jupiter.api.Assertions.*;

class IndependenceFailureCompatibilityTest {
    /** Unchanged guards do not interfere; a failed legacy step's actual writes can still disable one. */
    @ParameterizedTest @ValueSource(booleans = {false, true})
    void assertionProbePreservesLegacyBeforeAfterComparison(boolean changesGuard) {
        Step failing = new Step() {
            /** No reads are needed for a literal write. */
            public Set<MemoryLocation> reads() { return Set.of(); }
            /** Declares the possible legacy tentative write. */
            public Set<MemoryLocation> writes() { return Set.of(MemoryLocation.of("counter")); }
            /** The failing step is always initially enabled. */
            public boolean enabled(SharedState state) { return true; }
            /** Preserves the legacy effect-before-failure contract. */
            public StepOutcome execute(SharedState state) {
                ((CounterState) state).setCounter(changesGuard ? 1 : 0);
                return StepOutcome.ASSERTION_FAILED;
            }
        };
        Step guarded = new Step() {
            /** Declares the guard's only read. */
            public Set<MemoryLocation> reads() { return Set.of(MemoryLocation.of("counter")); }
            /** The guarded no-op has no writes. */
            public Set<MemoryLocation> writes() { return Set.of(); }
            /** Observes whether the isolated failed step changed the counter. */
            public boolean enabled(SharedState state) { return ((CounterState) state).counter() == 0; }
            /** Supplies an ordinary successful no-op. */
            public StepOutcome execute(SharedState state) { return StepOutcome.ADVANCED; }
        };
        Program program = new Program(new CounterState(0), List.of(new ModelThread(0, List.of(failing)), new ModelThread(1, List.of(guarded))));
        Configuration parent = program.initialConfiguration();
        assertEquals(changesGuard, new IndependenceRelation().hasEnableDisableInterference(parent, 0, 1, program.threads()));
        assertEquals(0, ((CounterState) parent.state()).counter());
    }
}
