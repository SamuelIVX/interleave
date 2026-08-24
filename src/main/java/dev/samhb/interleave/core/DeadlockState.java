package dev.samhb.interleave.core;

import java.io.DataOutput;
import java.io.IOException;
import java.util.Objects;

public final class DeadlockState implements SharedState {
    private final boolean[] flag;

    public DeadlockState(boolean[] flag) {
        this.flag = Objects.requireNonNull(flag, "flag must not be null");
        if (flag.length != 2) throw new IllegalArgumentException("flag must have length 2");
    }

    public static DeadlockState of(boolean t0Wants, boolean t1Wants) {
        return new DeadlockState(new boolean[]{t0Wants, t1Wants});
    }

    public boolean flag(int threadId) {
        return flag[threadId];
    }

    public void setFlag(int threadId, boolean value) {
        flag[threadId] = value;
    }

    @Override
    public SharedState deepCopy() {
        return new DeadlockState(new boolean[]{flag[0], flag[1]});
    }

    @Override
    public void encodeTo(DataOutput out) throws IOException {
        out.writeBoolean(flag[0]);
        out.writeBoolean(flag[1]);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof DeadlockState that)) return false;
        return java.util.Arrays.equals(flag, that.flag);
    }

    @Override
    public int hashCode() {
        return java.util.Arrays.hashCode(flag);
    }

    @Override
    public String toString() {
        return String.format("DeadlockState{flag=[%b, %b]}", flag[0], flag[1]);
    }
}
