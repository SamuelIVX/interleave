package dev.samhb.interleave.search;

import dev.samhb.interleave.core.*;

public final class TraceReplayer {
    public Configuration replay(Program program, Trace trace) {
        Configuration config = program.initialConfiguration();
        
        for (int i = 0; i < trace.threadIds().size(); i++) {
            int threadId = trace.threadIds().get(i);
            StepOutcome outcome = trace.outcomes().get(i);
            
            ModelThread thread = program.threads().get(threadId);
            int pc = config.programCounters().get(threadId);
            Step step = thread.steps().get(pc);
            
            step.execute(config.state());
            config = config.successor(threadId, outcome, program.threads(), config.state());
        }
        
        return config;
    }
}
