package dev.samhb.modelcheck.search;

import dev.samhb.modelcheck.core.*;

public final class TraceReplayer {
    public Configuration replay(Program program, Trace trace) {
        Configuration config = program.initialConfiguration();
        
        for (int i = 0; i < trace.threadIds().size(); i++) {
            int threadId = trace.threadIds().get(i);
            StepOutcome outcome = trace.outcomes().get(i);
            
            ModelThread thread = program.threads().get(threadId);
            Step step = thread.nextStep();
            if (step == null) {
                continue;
            }
            
            step.execute(config.state());
            config = config.successor(threadId, outcome, program.threads());
        }
        
        return config;
    }
}
