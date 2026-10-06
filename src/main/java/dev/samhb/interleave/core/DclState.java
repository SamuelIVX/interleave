/** Mutable double-checked-locking instance, initialization, lock, and observation state. */
package dev.samhb.interleave.core;

import java.io.DataOutput;
import java.io.IOException;
import java.util.Objects;

/** Mutable double-checked-locking instance, initialization, lock, and observation state. */
public final class DclState implements SharedState {
    /** Initialized. */
    private boolean initialized;
    /** Instance. */
    private Object instance; // null or "instance"
    /** Locked. */
    private boolean locked;
    /** Lock owner. */
    private int lockOwner; // -1 if unlocked, 0 or 1 for thread ID
    /** Control. */
    private boolean control;
    /** Observed instance. */
    private Object observedInstance; // what T1 has observed via DclUseInstanceStep

    /**
     * Creates dcl state from the supplied values.
     * @param initialized initial initialization marker
     */
    public DclState(boolean initialized) {
        this.initialized = initialized;
        this.instance = null;
        this.locked = false;
        this.lockOwner = -1;
        this.control = false;
        this.observedInstance = null;
    }

    /**
     * Creates dcl state with the supplied initial values.
     * @param initialized initial initialization marker
     * @return new modeled value with the supplied initial values
     */
    public static DclState of(boolean initialized) {
        return new DclState(initialized);
    }

    /**
     * Returns initialized for this dcl state.
     * @return whether initialized
     */
    public boolean initialized() {
        return initialized;
    }

    /**
     * Updates initialized in this mutable modeled state.
     * @param initialized initial initialization marker
     */
    public void setInitialized(boolean initialized) {
        this.initialized = initialized;
    }

    /**
     * Returns instance for this dcl state.
     * @return current modeled instance value
     */
    public Object instance() {
        return instance;
    }

    /**
     * Updates instance in this mutable modeled state.
     * @param instance instance value to publish
     */
    public void setInstance(Object instance) {
        this.instance = instance;
    }

    /**
     * Returns observed instance for this dcl state.
     * @return instance value last read by a thread
     */
    public Object observedInstance() {
        return observedInstance;
    }

    /**
     * Updates observed instance in this mutable modeled state.
     * @param observedInstance instance value last sampled by a reader
     */
    public void setObservedInstance(Object observedInstance) {
        this.observedInstance = observedInstance;
    }

    /**
     * Returns locked for this dcl state.
     * @return whether locked
     */
    public boolean locked() {
        return locked;
    }

    /**
     * Returns lock owner for this dcl state.
     * @return owning thread ID, or -1 when unlocked
     */
    public int lockOwner() {
        return lockOwner;
    }

    /**
     * Returns control for this dcl state.
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
     * Marks the lock held by the supplied thread; callers must establish that acquisition is enabled.
     * @param threadId zero-based modeled thread ID
     */
    public void lock(int threadId) {
        this.locked = true;
        this.lockOwner = threadId;
    }

    /**
     * Releases the lock only when the supplied thread owns it.
     * @param threadId zero-based modeled thread ID
     */
    public void unlock(int threadId) {
        if (lockOwner == threadId) {
            this.locked = false;
            this.lockOwner = -1;
        }
    }

    /** {@inheritDoc} */
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

    /** {@inheritDoc} */
    @Override
    public void encodeTo(DataOutput out) throws IOException {
        out.writeBoolean(initialized);
        out.writeBoolean(instance != null);
        out.writeBoolean(locked);
        out.writeInt(lockOwner);
        out.writeBoolean(control);
        out.writeBoolean(observedInstance != null);
    }

    /** {@inheritDoc} */
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

    /** {@inheritDoc} */
    @Override
    public int hashCode() {
        return Objects.hash(initialized, instance != null, locked, lockOwner, control, observedInstance != null);
    }

    /** {@inheritDoc} */
    @Override
    public String toString() {
        return String.format("DclState{initialized=%b, instance=%s, locked=%b, lockOwner=%d, control=%b, observedInstance=%s}",
            initialized, instance, locked, lockOwner, control, observedInstance);
    }
}
