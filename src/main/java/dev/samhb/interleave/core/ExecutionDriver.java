/** Executes an explicit schedule over a fresh initial program configuration. */
package dev.samhb.interleave.core;

import dev.samhb.interleave.search.*;
import java.util.ArrayList;
import java.util.List;

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
        if (program.isSource()) {
            ScheduleExecution result = runDetailed(program, schedule, null);
            if (result.kind() == ScheduleExecution.Kind.VIOLATION || result.kind() == ScheduleExecution.Kind.INCOMPLETE) {
                throw new SourceExecutionException(result);
            }
            return result.configuration();
        }
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

    /**
     * Runs a schedule until its first terminal event, retaining actual outcomes and committed state.
     * @param program immutable model
     * @param schedule chosen prefix
     * @param invariant optional timed safety property
     * @return structured execution and snapshots
     * @throws IllegalScheduleException if a requested live choice is disabled or invalid
     */
    public ScheduleExecution runDetailed(Program program, Schedule schedule, Invariant invariant) {
        Configuration config = program.initialConfiguration();
        List<Integer> ids = new ArrayList<>();
        List<StepOutcome> outcomes = new ArrayList<>();
        List<StateSnapshot> states = new ArrayList<>();
        states.add(StateSnapshotExtractor.extract(config.state()));
        int position = 0;
        while (true) {
            TraceOutcome terminal = invariant != null && !invariant.holds(config.state(), config) ? TraceOutcome.VIOLATION
                : config.allTerminated() ? TraceOutcome.COMPLETED : config.isDeadlockCandidate() ? TraceOutcome.DEADLOCK : null;
            if (terminal != null) return detailedResult(program, config, ids, outcomes, states, terminal, invariant, null);
            if (position == schedule.threadIds().size()) {
                return new ScheduleExecution(config, null, ScheduleExecution.Kind.PREFIX, ids, outcomes, states);
            }
            int id = schedule.threadIds().get(position++);
            if (!config.enabledThreadIds().contains(id)) throw new IllegalScheduleException("Thread " + id + " is not enabled");
            StepTransition transition = TransitionExecutor.execute(program, config, id);
            if (transition.cutoff() != null) {
                return detailedResult(program, config, ids, outcomes, states, TraceOutcome.INCOMPLETE, invariant, transition.cutoff());
            }
            ids.add(id);
            outcomes.add(transition.outcome());
            if (transition.outcome() == StepOutcome.ASSERTION_FAILED) {
                states.add(StateSnapshotExtractor.extract(config.state()));
                return detailedResult(program, config, ids, outcomes, states, TraceOutcome.VIOLATION, invariant, null);
            }
            config = transition.successor();
            states.add(StateSnapshotExtractor.extract(config.state()));
        }
    }

    /** Creates a terminal result from aligned recorded evidence.
     * @param program immutable source or legacy definitions
     * @param config last committed configuration
     * @param ids actual choices, excluding a cutoff attempt
     * @param outcomes aligned actual outcomes
     * @param states committed snapshots, including a repeated snapshot for a step failure
     * @param terminal reached verdict
     * @param invariant optional timed property for failure classification
     * @param cutoff separate omitted-action witness, or null
     * @return immutable terminal execution with freshly derived trace identity
     */
    private static ScheduleExecution detailedResult(Program program, Configuration config, List<Integer> ids,
            List<StepOutcome> outcomes, List<StateSnapshot> states, TraceOutcome terminal, Invariant invariant,
            CutoffEvidence cutoff) {
        Trace trace = Trace.create(program, ids, outcomes, terminal, invariant, cutoff);
        return new ScheduleExecution(config, trace, ScheduleExecution.Kind.valueOf(terminal.name()), ids, outcomes, states);
    }
}
