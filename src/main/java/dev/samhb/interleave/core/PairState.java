/** Mutable high/low pair with a remembered reader snapshot for torn-read checks. */
package dev.samhb.interleave.core;

import java.io.DataOutput;
import java.io.IOException;
import java.util.Objects;

/** Mutable high/low pair with a remembered reader snapshot for torn-read checks. */
public final class PairState implements SharedState {
    /** High. */
    private int high;
    /** Low. */
    private int low;
    /** Control. */
    private boolean control;
    /** Observed high. */
    private int observedHigh; // what T1 observed
    /** Observed low. */
    private int observedLow;  // what T1 observed
    /** Has observation. */
    private boolean hasObservation; // whether T1 has performed the read

    /**
     * Creates pair state from the supplied values.
     * @param high high half of the modeled pair
     * @param low low half of the modeled pair
     */
    public PairState(int high, int low) {
        this.high = high;
        this.low = low;
        this.control = false;
        this.observedHigh = 0;
        this.observedLow = 0;
        this.hasObservation = false;
    }

    /**
     * Creates pair state with the supplied initial values.
     * @param high high half of the modeled pair
     * @param low low half of the modeled pair
     * @return new modeled value with the supplied initial values
     */
    public static PairState of(int high, int low) {
        return new PairState(high, low);
    }

    /**
     * Creates pair state with the supplied initial values.
     * @param high high half of the modeled pair
     * @param low low half of the modeled pair
     * @param control modeled control flag
     * @return new modeled value with the supplied initial values
     */
    public static PairState of(int high, int low, boolean control) {
        PairState ps = new PairState(high, low);
        ps.control = control;
        return ps;
    }

    /**
     * Returns high for this pair state.
     * @return current high half
     */
    public int high() {
        return high;
    }

    /**
     * Updates high in this mutable modeled state.
     * @param high high half of the modeled pair
     */
    public void setHigh(int high) {
        this.high = high;
    }

    /**
     * Returns low for this pair state.
     * @return current low half
     */
    public int low() {
        return low;
    }

    /**
     * Updates low in this mutable modeled state.
     * @param low low half of the modeled pair
     */
    public void setLow(int low) {
        this.low = low;
    }

    /**
     * Returns control for this pair state.
     * @return whether control
     */
    public boolean control() {
        return control;
    }

    /**
     * Updates control in this mutable modeled state.
     * @param control modeled control flag
     */
    public void setControl(boolean control) {
        this.control = control;
    }

    /**
     * Returns observed high for this pair state.
     * @return last sampled high half
     */
    public int observedHigh() {
        return observedHigh;
    }

    /**
     * Returns observed low for this pair state.
     * @return last sampled low half
     */
    public int observedLow() {
        return observedLow;
    }

    /**
     * Returns has observation for this pair state.
     * @return true after a snapshot has been recorded
     */
    public boolean hasObservation() {
        return hasObservation;
    }

    /**
     * Stores both sampled halves and marks the snapshot as observed.
     * @param high high half of the modeled pair
     * @param low low half of the modeled pair
     */
    public void recordObservation(int high, int low) {
        this.observedHigh = high;
        this.observedLow = low;
        this.hasObservation = true;
    }

    /** {@inheritDoc} */
    @Override
    public SharedState deepCopy() {
        PairState copy = new PairState(this.high, this.low);
        copy.control = this.control;
        copy.observedHigh = this.observedHigh;
        copy.observedLow = this.observedLow;
        copy.hasObservation = this.hasObservation;
        return copy;
    }

    /** {@inheritDoc} */
    @Override
    public void encodeTo(DataOutput out) throws IOException {
        out.writeBoolean(control);
        out.writeInt(high);
        out.writeInt(low);
        out.writeInt(observedHigh);
        out.writeInt(observedLow);
        out.writeBoolean(hasObservation);
    }

    /** {@inheritDoc} */
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof PairState that)) return false;
        return high == that.high && low == that.low && control == that.control
            && observedHigh == that.observedHigh && observedLow == that.observedLow
            && hasObservation == that.hasObservation;
    }

    /** {@inheritDoc} */
    @Override
    public int hashCode() {
        return Objects.hash(high, low, control, observedHigh, observedLow, hasObservation);
    }

    /** {@inheritDoc} */
    @Override
    public String toString() {
        return String.format("PairState{high=%d, low=%d, control=%b, observedHigh=%d, observedLow=%d, hasObservation=%b}",
            high, low, control, observedHigh, observedLow, hasObservation);
    }
}
