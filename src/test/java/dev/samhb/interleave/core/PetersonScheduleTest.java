/** Peterson exclusion and turn-sensitive state evolution on real schedules. */
package dev.samhb.interleave.core;

import dev.samhb.interleave.search.DfsExplorer;
import dev.samhb.interleave.search.DfsResult;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class PetersonScheduleTest {

    @Test
    void petersonEncoded_handWrittenScheduleReproducesExpectedStateEvolution() {
        PetersonState initial = PetersonState.of(false, false, 0);

        List<Step> thread0Steps = List.of(
            new WriteFlagStep(0, true),
            new WriteTurnStep(1),
            new BusyWaitStep(0, 1),
            new CSEnterStep(0),
            new CSExitStep(),
            new WriteFlagStep(0, false)
        );

        List<Step> thread1Steps = List.of(
            new WriteFlagStep(1, true),
            new WriteTurnStep(0),
            new BusyWaitStep(1, 0),
            new CSEnterStep(1),
            new CSExitStep(),
            new WriteFlagStep(1, false)
        );

        ModelThread t0 = new ModelThread(0, thread0Steps);
        ModelThread t1 = new ModelThread(1, thread1Steps);

        Program program = new Program(initial, List.of(t0, t1));

        List<Integer> threadIds = List.of(0, 1, 0, 1, 0, 0, 0, 0, 1, 1, 1, 1);
        Schedule schedule = new Schedule(threadIds);

        ExecutionDriver driver = new ExecutionDriver();
        Configuration finalConfig = driver.run(program, schedule);

        assertTrue(finalConfig.allTerminated(),
            "Both threads should terminate. PCs: " + finalConfig.programCounters());
        assertFalse(finalConfig.isDeadlockCandidate());
    }

    @Test
    void petersonMutualExclusion_noConcurrentCriticalSection() {
        PetersonState initial = PetersonState.of(false, false, 0);

        List<Step> thread0Steps = List.of(
            new WriteFlagStep(0, true),
            new WriteTurnStep(1),
            new BusyWaitStep(0, 1),
            new CSEnterStep(0),
            new CSExitStep(),
            new WriteFlagStep(0, false)
        );

        List<Step> thread1Steps = List.of(
            new WriteFlagStep(1, true),
            new WriteTurnStep(0),
            new BusyWaitStep(1, 0),
            new CSEnterStep(1),
            new CSExitStep(),
            new WriteFlagStep(1, false)
        );

        ModelThread t0 = new ModelThread(0, thread0Steps);
        ModelThread t1 = new ModelThread(1, thread1Steps);

        Program program = new Program(initial, List.of(t0, t1));

        DfsExplorer explorer = new DfsExplorer();
        DfsResult result = explorer.explore(program, (state, config) -> {
            List<Integer> pcs = config.programCounters();
            return !(pcs.get(0) == 4 && pcs.get(1) == 4);
        });

        assertTrue(result.traces().stream().anyMatch(t -> t.outcome() == dev.samhb.interleave.search.TraceOutcome.COMPLETED));
        assertFalse(result.traces().stream().anyMatch(t -> t.outcome() == dev.samhb.interleave.search.TraceOutcome.VIOLATION));
    }

    @Test
    void petersonAlternation_firstEntererRespectsTurn() {
        PetersonState initial = PetersonState.of(false, false, 0);

        List<Step> thread0Steps = List.of(
            new WriteFlagStep(0, true),
            new WriteTurnStep(1),
            new BusyWaitStep(0, 1),
            new CSEnterStep(0),
            new CSExitStep(),
            new WriteFlagStep(0, false)
        );

        List<Step> thread1Steps = List.of(
            new WriteFlagStep(1, true),
            new WriteTurnStep(0),
            new BusyWaitStep(1, 0),
            new CSEnterStep(1),
            new CSExitStep(),
            new WriteFlagStep(1, false)
        );

        ModelThread t0 = new ModelThread(0, thread0Steps);
        ModelThread t1 = new ModelThread(1, thread1Steps);

        Program program = new Program(initial, List.of(t0, t1));

        List<Integer> threadIds = List.of(0, 1, 0, 1, 0, 0, 0, 0, 1, 1, 1, 1);
        Schedule schedule = new Schedule(threadIds);

        ExecutionDriver driver = new ExecutionDriver();
        Configuration finalConfig = driver.run(program, schedule);

        assertTrue(finalConfig.allTerminated());
        assertEquals(StepOutcome.ADVANCED, finalConfig.lastOutcome());
        Configuration firstEntry = driver.run(program, new Schedule(threadIds.subList(0, 6)));
        PetersonState first = (PetersonState) firstEntry.state();
        assertTrue(first.flag(0));
        assertTrue(first.flag(1));
        assertEquals(0, first.turn());
        assertEquals(0, first.inCriticalSection());
        assertEquals(List.of(4, 2), firstEntry.programCounters());
        assertEquals(List.of(0), firstEntry.enabledThreadIds());
        Configuration secondEntry = driver.run(program, new Schedule(threadIds.subList(0, 10)));
        assertEquals(1, ((PetersonState) secondEntry.state()).inCriticalSection());
        assertEquals(List.of(6, 4), secondEntry.programCounters());
    }
}
