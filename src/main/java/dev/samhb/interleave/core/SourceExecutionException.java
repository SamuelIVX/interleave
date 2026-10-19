/** Legacy fixed-schedule interface signal for source execution that stopped at failure or a bound. */
package dev.samhb.interleave.core;

/** Carries detailed evidence instead of returning a misleading successful prefix. */
public final class SourceExecutionException extends RuntimeException {
    /** Immutable terminal result. */
    private final ScheduleExecution result;
    /**
     * Creates a terminal source execution signal.
     * @param result failure or incomplete execution
     */
    public SourceExecutionException(ScheduleExecution result) {
        super("Source execution ended with " + result.kind());
        this.result = result;
    }
    /** Returns detailed terminal execution.
     * @return detailed terminal execution
     */
    public ScheduleExecution result() { return result; }
}
