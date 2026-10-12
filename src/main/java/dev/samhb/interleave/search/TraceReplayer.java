/** Reconstructs a configuration from recorded choices and trusted step outcomes. */
package dev.samhb.interleave.search;

import dev.samhb.interleave.core.*;

/** Reconstructs a configuration from recorded choices and trusted step outcomes. */
public final class TraceReplayer {
    /** Creates trace replayer with its default configuration. */
    public TraceReplayer() {}

    /**
     * Replays thread choices while validating each step outcome against the trace.
     *
     * <p>The program supplies a fresh initial configuration. Each chosen step mutates that
     * configuration’s state and must produce the outcome recorded in the trace.
     * An empty trace returns the initial configuration. Replay does not reject a chosen step
     * solely because it is disabled, or validate the trace's terminal verdict. Exceptions from
     * model callbacks propagate to the caller.
     *
     * <pre>{@code
     * Configuration end = new TraceReplayer().replay(program, trace);
     * }</pre>
     * @param program program whose initial state and steps are replayed
     * @param trace recorded thread choices and outcomes
     * @return configuration after the recorded prefix has been executed
     * @throws CorruptedTraceException if an executed step outcome differs from the trace outcome
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

            StepOutcome actualOutcome = step.execute(config.state());
            if (actualOutcome != outcome) {
                throw new CorruptedTraceException(
                    "Trace outcome mismatch at step " + i + " (thread " + threadId + "): "
                    + "recorded " + outcome + " but execution produced " + actualOutcome
                );
            }
            config = config.successor(threadId, outcome, program.threads(), config.state());
        }

        return config;
    }
}

