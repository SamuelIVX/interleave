package dev.samhb.interleave.core;

import java.io.DataOutput;
import java.io.IOException;
import java.util.Arrays;
import java.util.Objects;

public final class CounterState implements SharedState {
    private int counter;
    private boolean control;
    private final int[] registers; // thread-local storage for read values

    public CounterState(int counter) {
        this.counter = counter;
        this.control = false;
        this.registers = new int[2]; // 2 threads
    }

    private CounterState(int counter, boolean control, int[] registers) {
        this.counter = counter;
        this.control = control;
        this.registers = Arrays.copyOf(registers, registers.length);
    }

    public static CounterState of(int counter) {
        return new CounterState(counter);
    }

    public int counter() {
        return counter;
    }

    public void setCounter(int counter) {
        this.counter = counter;
    }

    public boolean control() {
        return control;
    }

    public void setControl(boolean control) {
        this.control = control;
    }

    public int getRegister(int threadId) {
        return registers[threadId];
    }

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