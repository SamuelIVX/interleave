package dev.samhb.interleave.core;

import java.io.DataOutput;
import java.io.IOException;
import java.util.Objects;

public final class PairState implements SharedState {
    private int high;
    private int low;
    private boolean control;

    public PairState(int high, int low) {
        this.high = high;
        this.low = low;
        this.control = false;
    }

    public static PairState of(int high, int low) {
        return new PairState(high, low);
    }

    public static PairState of(int high, int low, boolean control) {
        PairState ps = new PairState(high, low);
        ps.control = control;
        return ps;
    }

    public int high() {
        return high;
    }

    public void setHigh(int high) {
        this.high = high;
    }

    public int low() {
        return low;
    }

    public void setLow(int low) {
        this.low = low;
    }

    public boolean control() {
        return control;
    }

    public void setControl(boolean control) {
        this.control = control;
    }

    @Override
    public SharedState deepCopy() {
        PairState copy = new PairState(this.high, this.low);
        copy.control = this.control;
        return copy;
    }

    @Override
    public void encodeTo(DataOutput out) throws IOException {
        out.writeBoolean(control);
        out.writeInt(high);
        out.writeInt(low);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof PairState that)) return false;
        return high == that.high && low == that.low && control == that.control;
    }

    @Override
    public int hashCode() {
        return Objects.hash(high, low, control);
    }

    @Override
    public String toString() {
        return String.format("PairState{high=%d, low=%d, control=%b}", high, low, control);
    }
}