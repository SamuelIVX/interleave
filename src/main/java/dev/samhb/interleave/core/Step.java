package dev.samhb.interleave.core;

import java.util.Set;

public interface Step {
    Set<MemoryLocation> reads();

    Set<MemoryLocation> writes();

    boolean enabled(SharedState state);

    StepOutcome execute(SharedState state);
}
