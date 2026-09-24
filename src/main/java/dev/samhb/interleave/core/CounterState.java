package dev.samhb.interleave.core;

import java.io.DataOutput;
import java.io.IOException;
import java.util.Arrays;
import java.util.Objects;

/**
 * Shared counter with thread-local registers.
 * Used by corpus templates and bug corpus.
 */
public final class CounterState implements SharedState {
    private int counter;
    private boolean control;
    private final int[] registers; // thread-local storage for read values

    /**
     * Creates state with given counter and 2 threads.
     *
     * @param counter initial counter
     */
    public CounterState(int counter) {
        this(counter, 2);
    }

    /**
     * Creates state with given counter and thread count.
     *
     * @param counter initial counter
     * @param threads number of threads (register size)
     */
    public CounterState(int counter, int threads) {
        this.counter = counter;
        this.control = false;
        this.registers = new int[Math.max(1, threads)];
    }

    private CounterState(int counter, boolean control, int[] registers) {
        this.counter = counter;
        this.control = control;
        this.registers = Arrays.copyOf(registers, registers.length);
    }

    /**
     * Factory for default 2-thread state.
     *
     * @param counter initial counter
     * @return state
     */
    public static CounterState of(int counter) {
        return new CounterState(counter);
    }

    /**
     * Factory with thread count.
     *
     * @param counter initial counter
     * @param threads thread count
     * @return state
     */
    public static CounterState of(int counter, int threads) {
        return new CounterState(counter, threads);
    }

    /** @return counter */
    public int counter() {
        return counter;
    }

    /**
     * Sets counter.
     *
     * @param counter new value
     */
    public void setCounter(int counter) {
        this.counter = counter;
    }

    /** @return control flag */
    public boolean control() {
        return control;
    }

    /**
     * Sets control flag.
     *
     * @param control flag
     */
    public void setControl(boolean control) {
        this.control = control;
    }

    /**
     * Gets thread-local register.
     *
     * @param threadId thread id
     * @return register value
     */
    public int getRegister(int threadId) {
        return registers[threadId];
    }

    /**
     * Sets thread-local register.
     *
     * @param threadId thread id
     * @param value value
     */
    public void setRegister(int threadId, int value) {
        registers[threadId] = value;
    }

    @Override
    public SharedState deepCopy() {
        return new CounterState(counter, control, registers);
    }

    @Override
    public void encodeTo(DataOutput out) throws IOException {
        out.writeBoolean(control);
        out.writeInt(counter);
        out.writeInt(registers.length);
        for (int r : registers) {
            out.writeInt(r);
        }
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof CounterState that)) return false;
        return counter == that.counter && control == that.control && Arrays.equals(registers, that.registers);
    }

    @Override
    public int hashCode() {
        return Objects.hash(counter, control, Arrays.hashCode(registers));
    }

    @Override
    public String toString() {
        return String.format("CounterState{counter=%d, control=%b, registers=%s}", counter, control, Arrays.toString(registers));
    }
}
