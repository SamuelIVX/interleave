package dev.samhb.interleave.bugs;

import dev.samhb.interleave.core.*;
import dev.samhb.interleave.search.Invariant;
import java.util.List;

public final class LostUpdate {
    public static BenchmarkProgram program() {
        CounterState initial = CounterState.of(0);

        // Each thread: read counter, then write counter+1
        // The lost update happens when both threads read 0, both write 1
        List<Step> thread0Steps = List.of(
            new ReadCounterStep(0),
            new WriteCounterStep(0)
        );

        List<Step> thread1Steps = List.of(
            new ReadCounterStep(1),
            new WriteCounterStep(1)
        );

        ModelThread t0 = new ModelThread(0, thread0Steps);
        ModelThread t1 = new ModelThread(1, thread1Steps);

        Program program = new Program(initial, List.of(t0, t1));

        // Invariant: after both threads complete, counter should be 2
        // (each thread increments once, so 0 + 1 + 1 = 2)
        // Lost update bug: both threads read 0, both write 1, final counter = 1
        Invariant invariant = (state, config) -> {
            CounterState cs = (CounterState) state;
            // Check at termination
            if (config.allTerminated()) {
                return cs.counter() == 2;
            }
            return true;
        };

        return new BenchmarkProgram("lost-update", program, "VIOLATION", invariant);
    }
}