package dev.samhb.modelcheck.core;

import java.io.DataOutput;
import java.io.IOException;
import java.util.Objects;

public final class PetersonState implements SharedState {
    private boolean[] flag;
    private int turn;
    private int inCriticalSection;

    public PetersonState(boolean[] flag, int turn, int inCriticalSection) {
        this.flag = Objects.requireNonNull(flag, "flag must not be null");
        if (flag.length != 2) throw new IllegalArgumentException("flag must have length 2");
        this.turn = turn;
        this.inCriticalSection = inCriticalSection;
    }

    public static PetersonState of(boolean t0Wants, boolean t1Wants, int turn) {
        return new PetersonState(new boolean[]{t0Wants, t1Wants}, turn, -1);
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
        return new PetersonState(new boolean[]{flag[0], flag[1]}, turn, inCriticalSection);
    }

    @Override
    public void encodeTo(DataOutput out) throws IOException {
        out.writeBoolean(flag[0]);
        out.writeBoolean(flag[1]);
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
        return String.format("PetersonState{flag=[%b, %b], turn=%d, cs=%s}", 
            flag[0], flag[1], turn, cs);
    }
}
