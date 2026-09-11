package dev.samhb.interleave.core;

import java.io.DataOutput;
import java.io.IOException;
import java.util.Objects;

public final class DeadlockState implements SharedState {
    private final boolean[] flag;
    private boolean control;

    public DeadlockState(boolean[] flag) {
        this.flag = Objects.requireNonNull(flag, "flag must not be null");
        if (flag.length != 2) throw new IllegalArgumentException("flag must have length 2");
        this.control = false;
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

    public boolean control() {
        return control;
    }

    public void setControl(boolean value) {
        control = value;
    }

    @Override
    public SharedState deepCopy() {
        DeadlockState copy = new DeadlockState(new boolean[]{flag[0], flag[1]});
        copy.control = this.control;
        return copy;
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
        return java.util.Arrays.equals(flag, that.flag) && control == that.control;
    }

    @Override
    public int hashCode() {
        int result = java.util.Arrays.hashCode(flag);
        result = 31 * result + Boolean.hashCode(control);
        return result;
    }

    @Override
    public String toString() {
        return String.format("DeadlockState{flag=[%b, %b], control=%b}", flag[0], flag[1], control);
    }
}
