package dev.samhb.interleave.core;

import java.io.DataOutput;
import java.io.IOException;
import java.util.Objects;

public final class PairState implements SharedState {
    private int high;
    private int low;
    private boolean control;
    private int observedHigh; // what T1 observed
    private int observedLow;  // what T1 observed
    private boolean hasObservation; // whether T1 has performed the read

    public PairState(int high, int low) {
        this.high = high;
        this.low = low;
        this.control = false;
        this.observedHigh = 0;
        this.observedLow = 0;
        this.hasObservation = false;
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

    public int observedHigh() {
        return observedHigh;
    }

    public int observedLow() {
        return observedLow;
    }

    public boolean hasObservation() {
        return hasObservation;
    }

    public void recordObservation(int high, int low) {
        this.observedHigh = high;
        this.observedLow = low;
        this.hasObservation = true;
    }

    @Override
    public SharedState deepCopy() {
        PairState copy = new PairState(this.high, this.low);
        copy.control = this.control;
        copy.observedHigh = this.observedHigh;
        copy.observedLow = this.observedLow;
        copy.hasObservation = this.hasObservation;
        return copy;
    }

    @Override
    public void encodeTo(DataOutput out) throws IOException {
        out.writeBoolean(control);
        out.writeInt(high);
        out.writeInt(low);
        out.writeInt(observedHigh);
        out.writeInt(observedLow);
        out.writeBoolean(hasObservation);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof PairState that)) return false;
        return high == that.high && low == that.low && control == that.control
            && observedHigh == that.observedHigh && observedLow == that.observedLow
            && hasObservation == that.hasObservation;
    }

    @Override
    public int hashCode() {
        return Objects.hash(high, low, control, observedHigh, observedLow, hasObservation);
    }

    @Override
    public String toString() {
        return String.format("PairState{high=%d, low=%d, control=%b, observedHigh=%d, observedLow=%d, hasObservation=%b}", 
            high, low, control, observedHigh, observedLow, hasObservation);
    }
}