/** Executes an explicit schedule over a fresh initial program configuration. */
package dev.samhb.interleave.core;

/** Executes an explicit schedule over a fresh initial program configuration. */
public final class ExecutionDriver {
    /** Creates execution driver with its default configuration. */
    public ExecutionDriver() {}

    /**
     * Executes the supplied schedule against isolated modeled state.
     * <p>Execution stops when the schedule is consumed or all threads terminate. The caller’s
     * initial state is copied by the program; steps mutate the run’s configuration state.
     * <pre>{@code
     * Configuration end = new ExecutionDriver().run(program, new Schedule(java.util.List.of(0, 1)));
     * }</pre>
     * @param program modeled program whose threads are explored
     * @param schedule thread choices to execute in order
     * @return configuration after the executed prefix
     * @throws IllegalScheduleException if a scheduled thread is not enabled
     */
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
            int pc = config.programCounters().get(threadId);
            Step step = thread.steps().get(pc);

            StepOutcome outcome = step.execute(config.state());
            config = config.successor(threadId, outcome, program.threads(), config.state());
        }

        return config;
    }
}
