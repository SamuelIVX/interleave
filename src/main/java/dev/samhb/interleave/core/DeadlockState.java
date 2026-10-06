/** Mutable intent flags and control value used by deadlock models. */
package dev.samhb.interleave.core;

import java.io.DataOutput;
import java.io.IOException;
import java.util.Objects;

/** Mutable intent flags and control value used by deadlock models. */
public final class DeadlockState implements SharedState {
    /** Flag. */
    private final boolean[] flag;
    /** Control. */
    private boolean control;

    /**
     * Creates deadlock state from the supplied values.
     * @param flag initial thread-intent flags
     */
    public DeadlockState(boolean[] flag) {
        this.flag = Objects.requireNonNull(flag, "flag must not be null");
        if (flag.length < 2) throw new IllegalArgumentException("flag must have length >= 2, got " + flag.length);
        this.control = false;
    }

    /**
     * Creates deadlock state with the supplied initial values.
     * @param t0Wants initial intent flag for thread zero
     * @param t1Wants initial intent flag for thread one
     * @return new modeled value with the supplied initial values
     */
    public static DeadlockState of(boolean t0Wants, boolean t1Wants) {
        return of(new boolean[]{t0Wants, t1Wants});
    }

    /**
     * Factory for any thread count.
     *
     * @param flag one flag per thread, at least two
     * @return a state with {@code control} unset
     * @throws IllegalArgumentException if fewer than two flags are supplied
     */
    public static DeadlockState of(boolean[] flag) {
        return new DeadlockState(flag);
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
     * Returns control for this deadlock state.
     * @return whether control
     */
    public boolean control() {
        return control;
    }

    /**
     * Updates control in this mutable modeled state.
     * @param value value to assign
     */
    public void setControl(boolean value) {
        control = value;
    }

    /** {@inheritDoc} */
    @Override
    public SharedState deepCopy() {
        DeadlockState copy = new DeadlockState(java.util.Arrays.copyOf(flag, flag.length));
        copy.control = this.control;
        return copy;
    }

    /** {@inheritDoc} */
    @Override
    public void encodeTo(DataOutput out) throws IOException {
        out.writeBoolean(control);
        // The count precedes the flags so the array's extent is explicit rather than implied by the
        // byte count, matching CounterState.registers.
        out.writeInt(flag.length);
        for (boolean f : flag) {
            out.writeBoolean(f);
        }
    }

    /** {@inheritDoc} */
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof DeadlockState that)) return false;
        return java.util.Arrays.equals(flag, that.flag) && control == that.control;
    }

    /** {@inheritDoc} */
    @Override
    public int hashCode() {
        int result = java.util.Arrays.hashCode(flag);
        result = 31 * result + Boolean.hashCode(control);
        return result;
    }

    /** {@inheritDoc} */
    @Override
    public String toString() {
        return String.format("DeadlockState{flag=%s, control=%b}", java.util.Arrays.toString(flag), control);
    }
}
