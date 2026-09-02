package dev.samhb.interleave.core;

import java.io.DataOutput;
import java.io.IOException;
import java.util.Objects;

public final class DclState implements SharedState {
    private boolean initialized;
    private Object instance; // null or "instance"
    private boolean locked;
    private int lockOwner; // -1 if unlocked, 0 or 1 for thread ID
    private boolean control;
    private Object observedInstance; // what T1 has observed via DclUseInstanceStep

    public DclState(boolean initialized) {
        this.initialized = initialized;
        this.instance = null;
        this.locked = false;
        this.lockOwner = -1;
        this.control = false;
        this.observedInstance = null;
    }

    public static DclState of(boolean initialized) {
        return new DclState(initialized);
    }

    public boolean initialized() {
        return initialized;
    }

    public void setInitialized(boolean initialized) {
        this.initialized = initialized;
    }

    public Object instance() {
        return instance;
    }

    public void setInstance(Object instance) {
        this.instance = instance;
    }

    public Object observedInstance() {
        return observedInstance;
    }

    public void setObservedInstance(Object observedInstance) {
        this.observedInstance = observedInstance;
    }

    public boolean locked() {
        return locked;
    }

    public int lockOwner() {
        return lockOwner;
    }

    public boolean control() {
        return control;
    }

    public void setControl(boolean control) {
        this.control = control;
    }

    public void lock(int threadId) {
        this.locked = true;
        this.lockOwner = threadId;
    }

    public void unlock(int threadId) {
        if (lockOwner == threadId) {
            this.locked = false;
            this.lockOwner = -1;
        }
    }

    @Override
    public SharedState deepCopy() {
        DclState copy = new DclState(this.initialized);
        copy.instance = this.instance;
        copy.locked = this.locked;
        copy.lockOwner = this.lockOwner;
        copy.control = this.control;
        copy.observedInstance = this.observedInstance;
        return copy;
    }

    @Override
    public void encodeTo(DataOutput out) throws IOException {
        out.writeBoolean(initialized);
        out.writeBoolean(instance != null);
        out.writeBoolean(locked);
        out.writeInt(lockOwner);
        out.writeBoolean(control);
        out.writeBoolean(observedInstance != null);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof DclState that)) return false;
        return initialized == that.initialized 
            && (instance != null) == (that.instance != null)
            && locked == that.locked
            && lockOwner == that.lockOwner
            && control == that.control
            && (observedInstance != null) == (that.observedInstance != null);
    }

    @Override
    public int hashCode() {
        return Objects.hash(initialized, instance != null, locked, lockOwner, control, observedInstance != null);
    }

    @Override
    public String toString() {
        return String.format("DclState{initialized=%b, instance=%s, locked=%b, lockOwner=%d, control=%b, observedInstance=%s}", 
            initialized, instance, locked, lockOwner, control, observedInstance);
    }
}