package dev.samhb.modelcheck.core;

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
        
        Schedule schedule = new Schedule(List.of(0, 1, 0, 1, 0, 1, 0, 1, 0, 1, 0, 1));
        
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
        
        List<Integer> threadIds = new ArrayList<>();
        for (int i = 0; i < 12; i++) {
            threadIds.add(i % 2);
        }
        Schedule schedule = new Schedule(threadIds);
        
        ExecutionDriver driver = new ExecutionDriver();
        
        List<PetersonState> states = new ArrayList<>();
        states.add(initial);
        
        Configuration config = program.initialConfiguration();
        for (int threadId : schedule.threadIds()) {
            if (config.allTerminated()) break;
            
            ModelThread thread = program.threads().get(threadId);
            Step step = thread.nextStep();
            if (step == null) continue;
            
            StepOutcome outcome = step.execute(config.state());
            config = config.successor(threadId, outcome, program.threads());
            states.add((PetersonState) config.state());
        }
        
        for (int i = 0; i < states.size(); i++) {
            PetersonState state = states.get(i);
            int inCs = state.inCriticalSection();
            if (inCs != -1) {
                assertTrue(inCs == 0 || inCs == 1, 
                    "Invalid thread in CS: " + inCs);
            }
        }
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
        
        Schedule schedule = new Schedule(List.of(0, 1, 0, 1, 0, 1, 0, 1, 0, 1, 0, 1));
        
        ExecutionDriver driver = new ExecutionDriver();
        Configuration finalConfig = driver.run(program, schedule);
        
        assertTrue(finalConfig.allTerminated());
        assertEquals(StepOutcome.ADVANCED, finalConfig.lastOutcome());
    }
}
