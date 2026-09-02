package dev.samhb.interleave.bugs;

import dev.samhb.interleave.core.*;
import dev.samhb.interleave.search.Invariant;
import java.util.List;

/**
 * Double-checked locking (DCL) broken singleton.
 * The bug: thread T0 initializes the instance, but another thread can see
 * a partially constructed object because the write to `instance` can be
 * reordered before the constructor completes.
 * 
 * In this model: T0 writes to `instance`, T1 reads it. Without proper
 * synchronization (happens-before), T1 may see a non-null but partially
 * initialized instance.
 */
public final class DoubleCheckedLocking {
    public static BenchmarkProgram program() {
        // State: instance = null initially, initialized = false
        DclState initial = DclState.of(false);

        // Thread 0: first check, then lock, second check, initialize
        List<Step> thread0Steps = List.of(
            // Read instance (first check)
            new DclReadInstanceStep(0),
            // If null, acquire lock
            new DclLockStep(0),
            // Second check inside lock
            new DclReadInstanceStep(0),
            // Initialize
            new DclInitSetInstanceStep(0),
            new DclInitSetInitializedStep(0),
            // Release lock
            new DclUnlockStep(0)
        );

        // Thread 1: read instance without lock (the bug)
        List<Step> thread1Steps = List.of(
            new DclReadInstanceStep(1),
            // Use instance without checking if fully initialized
            new DclUseInstanceStep(1)
        );

        ModelThread t0 = new ModelThread(0, thread0Steps);
        ModelThread t1 = new ModelThread(1, thread1Steps);

        Program program = new Program(initial, List.of(t0, t1));
        
        // Invariant: if instance is non-null, it must be fully initialized
        // The bug: T1 can see instance != null while initialized == false
        Invariant invariant = (s, config) -> {
            DclState state = (DclState) s;
            if (state.instance() != null && !state.initialized()) {
                return false; // VIOLATION: observed half-constructed object
            }
            return true;
        };
        
        return new BenchmarkProgram("double-checked-locking", program, "VIOLATION", invariant);
    }
}