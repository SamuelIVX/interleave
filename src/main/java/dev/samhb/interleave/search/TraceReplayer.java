/** Reconstructs a configuration from recorded choices and trusted step outcomes. */
package dev.samhb.interleave.search;

import dev.samhb.interleave.core.*;

/** Reconstructs a configuration from recorded choices and trusted step outcomes. */
public final class TraceReplayer {
    /** Creates trace replayer with its default configuration. */
    public TraceReplayer() {}

    /**
     * Replays thread choices while trusting the step outcomes recorded in the trace.
     *
     * <p>The program supplies a fresh initial configuration. Each chosen step mutates that
     * configuration’s state; the recorded outcome, rather than the returned execution outcome,
     * controls counter advancement. This method does not validate trace consistency or verdicts.
     * Callers must supply aligned thread/outcome lists and valid thread IDs and counters.
     *
     * <pre>{@code
     * Configuration end = new TraceReplayer().replay(program, trace);
     * }</pre>
     * @param program program whose initial state and steps are replayed
     * @param trace recorded thread choices and outcomes
     * @return configuration after the recorded prefix has been executed
     * @throws IndexOutOfBoundsException if a recorded thread, counter, or outcome index is invalid
     */
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
