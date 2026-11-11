/** Immutable source execution captured at an external resource interruption, with explicit replay. */
package dev.samhb.interleave;

import dev.samhb.interleave.core.*;
import dev.samhb.interleave.search.*;
import dev.samhb.interleave.state.CanonicalEncoder;
import java.io.Serializable;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Actual committed source path at a runner resource stop, distinct from an omitted-action witness.
 * The reason describes the original run; replay never reproduces its time/state accounting.
 * Contains no mutable model, search configuration, store or callback. Native object streams are
 * build-specific as documented by {@link TestResult}; JSON presentation belongs to Spec 88.
 */
public final class ResourcePrefix implements Serializable {
    /** Internal search-prefix carrier, never exposed as an ordinary cutoff/failure trace. */
    private final Trace prefix;
    /** Resource check that actually stopped the runner. */
    private final ResourceLimitReason reason;
    /** Frozen actual last committed state, including typed locals and array values. */
    private final StateSnapshot terminalState;
    /** Actual owner counters at interruption. */
    private final List<Integer> programCounters;
    /** Exact private end-position identity, not a hash or stable wire format. */
    private final String terminalKey;

    /** Freezes an actual source interruption supplied synchronously by the runner's visitor.
     * @param prefix immutable committed source path, with no modeled failure/cutoff
     * @param terminal actual last visited configuration, read only during this constructor
     * @param reason known resource cause observed by the runner
     * @throws NullPointerException if an argument is null
     * @throws IllegalArgumentException if the carrier is not an unclassified committed source prefix
     */
    ResourcePrefix(Trace prefix, Configuration terminal, ResourceLimitReason reason) {
        this.prefix = Objects.requireNonNull(prefix, "prefix");
        this.reason = Objects.requireNonNull(reason, "reason");
        Objects.requireNonNull(terminal, "terminal");
        if (prefix.outcome() != TraceOutcome.INCOMPLETE || prefix.evidence() == null
                || prefix.evidence().sourceDigest() == null || prefix.failure() != null || prefix.cutoff() != null
                || prefix.outcomes().contains(StepOutcome.ASSERTION_FAILED)) {
            throw new IllegalArgumentException("Resource prefix requires committed source evidence without a cutoff or failure");
        }
        terminalState = StateSnapshotExtractor.extract(terminal.state());
        programCounters = List.copyOf(terminal.programCounters());
        terminalKey = new CanonicalEncoder().configurationKey(terminal);
    }

    /** Returns the resource check that interrupted the original run.
     * @return resource cause from the original interrupted run, not a replayed event
     */
    public ResourceLimitReason reason() { return reason; }

    /** Returns the actual committed owner choices in execution order.
     * @return immutable actual committed owner choices
     */
    public List<Integer> threadIds() { return prefix.threadIds(); }

    /** Returns the outcomes observed for those committed choices.
     * @return immutable outcomes aligned with the owner choices
     */
    public List<StepOutcome> outcomes() { return prefix.outcomes(); }

    /** Returns the captured source identity without assigning modeled terminal evidence.
     * @return immutable source identity/steps, with no failure or omitted cutoff
     */
    public TraceEvidence evidence() { return prefix.evidence(); }

    /** Returns every owner’s captured continuation counter.
     * @return immutable actual counters for every owner at interruption
     */
    public List<Integer> programCounters() { return programCounters; }

    /** Returns the final shared and owner-local values frozen at interruption.
     * @return deeply immutable final shared/local values captured without replay
     */
    public StateSnapshot terminalState() { return terminalState; }

    /**
     * Authenticates this source path and observes its execution under the supplied property.
     * Reuses existing replay/driver passes; only explicit callers pay that diagnostic work.
     * Supply the actual pure deterministic source property. Null performs no property checking.
     * A terminal event at the final position is reported separately from the original resource stop;
     * an earlier event that prevents consuming the captured choices rejects authentication.
     * <pre>{@code
     * ResourcePrefix prefix = result.resourcePrefix().orElseThrow();
     * ResourcePrefix.Replay observed = prefix.replay(program, invariant);
     * // observed.kind() == COMPLETED is one completed path, never exhaustive PASS.
     * }</pre>
     * @param program original deterministic source program
     * @param invariant actual source property, or null for step-only observation
     * @return immutable explicit observation and fresh snapshots; original result is unchanged
     * @throws NullPointerException if program is null
     * @throws IllegalArgumentException if program is not a source model
     * @throws CorruptedTraceException if identity, outcomes, consumed choices or final position differ
     * @throws IllegalScheduleException if a recorded choice is impossible or disabled
     * @throws IndexOutOfBoundsException if a recorded owner/counter is invalid

     */
    public Replay replay(Program program, Invariant invariant) {
        Objects.requireNonNull(program, "program");
        if (!program.isSource()) throw new IllegalArgumentException("Resource prefix replay requires a source program");
        new TraceReplayer().replay(program, prefix);
        ScheduleExecution execution = new ExecutionDriver().runDetailed(program, new Schedule(threadIds()), invariant);
        // The driver consumes the supplied choices in order; equal outcomes also require full consumption.
        if (!execution.outcomes().equals(outcomes())
                || !terminalKey.equals(new CanonicalEncoder().configurationKey(execution.configuration()))) {
            throw new CorruptedTraceException("Resource prefix execution does not match the captured committed position");
        }
        FailureEvidence failure = execution.trace() == null ? null : execution.trace().failure();
        return new Replay(execution.kind(), invariant != null, execution.states(), failure);
    }

    /** Immutable observation from explicit replay; never a replacement run-level verdict.
     */
    public static final class Replay {
        /** Actual execution classification at the consumed prefix. */
        private final ScheduleExecution.Kind kind;
        /** Whether an explicit property participated in this replay. */
        private final boolean propertyChecked;
        /** Fresh deeply immutable snapshots for the original choices. */
        private final List<StateSnapshot> states;
        /** Actual source failure, when replay observed one at the final position. */
        private final FailureEvidence failure;

        /** Freezes validated replay observations; only the enclosing module constructs these values.
         * @param kind actual driver classification
         * @param propertyChecked whether a property was supplied
         * @param states actual committed snapshots, initial through final
         * @param failure classified terminal failure, or null
         */
        private Replay(ScheduleExecution.Kind kind, boolean propertyChecked,
                       List<StateSnapshot> states, FailureEvidence failure) {
            this.kind = kind;
            this.propertyChecked = propertyChecked;
            this.states = List.copyOf(states);
            this.failure = failure;
        }

        /** Returns the observed execution classification for this one prefix.
         * @return actual path observation, never exhaustive PASS or a resource reproduction
         */
        public ScheduleExecution.Kind kind() { return kind; }

        /** Reports whether this replay evaluated a supplied property.
         * @return true only when the caller supplied a property to this replay
         */
        public boolean propertyChecked() { return propertyChecked; }

        /** Returns the fresh initial-through-final snapshots from explicit replay.
         * @return immutable snapshots aligned with the captured choices, including the initial state
         */
        public List<StateSnapshot> states() { return states; }

        /** Returns a failure discovered at the final captured position.
         * @return actual classified failure at the final position, or empty
         */
        public Optional<FailureEvidence> failure() { return Optional.ofNullable(failure); }
    }
}
