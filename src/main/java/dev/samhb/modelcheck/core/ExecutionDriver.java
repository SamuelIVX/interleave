package dev.samhb.modelcheck.core;

public final class ExecutionDriver {
    public Configuration run(Program program, Schedule schedule) {
        Configuration config = program.initialConfiguration();
        
        for (int threadId : schedule.threadIds()) {
            if (config.allTerminated()) {
                break;
            }
            
            if (!config.enabledThreadIds().contains(threadId)) {
                throw new IllegalScheduleException(
                    "Thread " + threadId + " is not enabled in current configuration"
                );
            }
            
            ModelThread thread = program.threads().get(threadId);
            Step step = thread.nextStep();
            if (step == null) {
                continue;
            }
            
            StepOutcome outcome = step.execute(config.state());
            config = config.successor(threadId, outcome, program.threads());
        }
        
        return config;
    }
}
