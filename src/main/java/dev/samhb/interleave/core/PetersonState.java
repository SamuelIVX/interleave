package dev.samhb.interleave.core;

import java.io.DataOutput;
import java.io.IOException;
import java.util.Objects;

public final class PetersonState implements SharedState {
    private boolean[] flag;
    private int turn;
    private int inCriticalSection;

    public PetersonState(boolean[] flag, int turn, int inCriticalSection) {
        this.flag = Objects.requireNonNull(flag, "flag must not be null");
        if (flag.length < 2) throw new IllegalArgumentException("flag must have length >= 2, got " + flag.length);
        this.turn = turn;
        this.inCriticalSection = inCriticalSection;
    }

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

    public boolean flag(int threadId) {
        return flag[threadId];
    }

    public void setFlag(int threadId, boolean value) {
        flag[threadId] = value;
    }

    public int turn() {
        return turn;
    }

    public void setTurn(int turn) {
        this.turn = turn;
    }

    public int inCriticalSection() {
        return inCriticalSection;
    }

    public void setInCriticalSection(int threadId) {
        this.inCriticalSection = threadId;
    }

    @Override
    public SharedState deepCopy() {
        return new PetersonState(java.util.Arrays.copyOf(flag, flag.length), turn, inCriticalSection);
    }

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

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof PetersonState that)) return false;
        return turn == that.turn &&
               inCriticalSection == that.inCriticalSection &&
               java.util.Arrays.equals(flag, that.flag);
    }

    @Override
    public int hashCode() {
        int result = java.util.Arrays.hashCode(flag);
        result = 31 * result + turn;
        result = 31 * result + inCriticalSection;
        return result;
    }

    @Override
    public String toString() {
        String cs = inCriticalSection == -1 ? "none" : ("t" + inCriticalSection);
        return String.format("PetersonState{flag=%s, turn=%d, cs=%s}",
            java.util.Arrays.toString(flag), turn, cs);
    }
}
