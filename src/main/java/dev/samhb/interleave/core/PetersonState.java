/** Mutable Peterson intent flags, turn variable, and critical-section owner. */
package dev.samhb.interleave.core;

import java.io.DataOutput;
import java.io.IOException;
import java.util.Objects;

/** Mutable Peterson intent flags, turn variable, and critical-section owner. */
public final class PetersonState implements SharedState {
    /** Flag. */
    private boolean[] flag;
    /** Turn. */
    private int turn;
    /** In critical section. */
    private int inCriticalSection;

    /**
     * Creates peterson state from the supplied values.
     * @param flag initial thread-intent flags
     * @param turn thread ID favored by the turn variable
     * @param inCriticalSection owner ID, or -1 when the critical section is free
     */
    public PetersonState(boolean[] flag, int turn, int inCriticalSection) {
        this.flag = Objects.requireNonNull(flag, "flag must not be null");
        if (flag.length < 2) throw new IllegalArgumentException("flag must have length >= 2, got " + flag.length);
        this.turn = turn;
        this.inCriticalSection = inCriticalSection;
    }

    /**
     * Creates peterson state with the supplied initial values.
     * @param t0Wants initial intent flag for thread zero
     * @param t1Wants initial intent flag for thread one
     * @param turn thread ID favored by the turn variable
     * @return new modeled value with the supplied initial values
     */
    public static PetersonState of(boolean t0Wants, boolean t1Wants, int turn) {
        return of(new boolean[]{t0Wants, t1Wants}, turn);
    }

    /**
     * Factory for any thread count.
     *
     * @param flag one flag per thread, at least two
     * @param turn the initial turn value
     * @return a state with {@code inCriticalSection} unset
     * @throws IllegalArgumentException if fewer than two flags are supplied
     */
    public static PetersonState of(boolean[] flag, int turn) {
        return new PetersonState(flag, turn, -1);
    }

    /**
     * Returns the intent flag at the supplied thread index.
     * @param threadId zero-based modeled thread ID
     * @return whether flag
     */
    public boolean flag(int threadId) {
        return flag[threadId];
    }

    /**
     * Updates flag in this mutable modeled state.
     * @param threadId zero-based modeled thread ID
     * @param value value to assign
     */
    public void setFlag(int threadId, boolean value) {
        flag[threadId] = value;
    }

    /**
     * Returns turn for this peterson state.
     * @return thread ID favored by the turn variable
     */
    public int turn() {
        return turn;
    }

    /**
     * Updates turn in this mutable modeled state.
     * @param turn thread ID favored by the turn variable
     */
    public void setTurn(int turn) {
        this.turn = turn;
    }

    /**
     * Returns in critical section for this peterson state.
     * @return current owner ID, or -1 when no thread owns the critical section
     */
    public int inCriticalSection() {
        return inCriticalSection;
    }

    /**
     * Updates in critical section in this mutable modeled state.
     * @param threadId zero-based modeled thread ID
     */
    public void setInCriticalSection(int threadId) {
        this.inCriticalSection = threadId;
    }

    /** {@inheritDoc} */
    @Override
    public SharedState deepCopy() {
        return new PetersonState(java.util.Arrays.copyOf(flag, flag.length), turn, inCriticalSection);
    }

    /** {@inheritDoc} */
    @Override
    public void encodeTo(DataOutput out) throws IOException {
        // The count is written before the flags: without it, a two-thread and a three-thread state
        // differ only in byte count, which keeps them apart today but for an incidental reason. The
        // prefix makes the array's extent explicit, matching CounterState.registers.
        out.writeInt(flag.length);
        for (boolean f : flag) {
            out.writeBoolean(f);
        }
        out.writeInt(turn);
        out.writeInt(inCriticalSection);
    }

    /** {@inheritDoc} */
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof PetersonState that)) return false;
        return turn == that.turn &&
               inCriticalSection == that.inCriticalSection &&
               java.util.Arrays.equals(flag, that.flag);
    }

    /** {@inheritDoc} */
    @Override
    public int hashCode() {
        int result = java.util.Arrays.hashCode(flag);
        result = 31 * result + turn;
        result = 31 * result + inCriticalSection;
        return result;
    }

    /** {@inheritDoc} */
    @Override
    public String toString() {
        String cs = inCriticalSection == -1 ? "none" : ("t" + inCriticalSection);
        return String.format("PetersonState{flag=%s, turn=%d, cs=%s}",
            java.util.Arrays.toString(flag), turn, cs);
    }
}
