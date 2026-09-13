package dev.samhb.interleave.bugs;

import dev.samhb.interleave.core.*;
import dev.samhb.interleave.format.ProgramLoader;
import dev.samhb.interleave.format.registry.RegistryException;
import dev.samhb.interleave.search.Invariant;
import java.util.List;
import java.util.ArrayList;

public final class BugCorpus {
    private static final ProgramLoader LOADER = new ProgramLoader();

    public static List<BenchmarkProgram> all() {
        List<BenchmarkProgram> programs = new ArrayList<>();
        programs.add(peterson());
        programs.add(brokenPeterson());
        programs.add(brokenPetersonV2());
        programs.add(deadlock());
        programs.add(doubleCheckedLocking());
        programs.add(lostUpdate());
        programs.add(tornCounter());
        return programs;
    }

    // Public API: loads from JSON resources (migrated format)
    public static BenchmarkProgram peterson() {
        return LOADER.loadFromResource("programs/peterson.json");
    }

    public static BenchmarkProgram brokenPeterson() {
        return LOADER.loadFromResource("programs/broken-peterson.json");
    }

    public static BenchmarkProgram brokenPetersonV2() {
        return LOADER.loadFromResource("programs/broken-peterson-v2.json");
    }

    public static BenchmarkProgram deadlock() {
        return LOADER.loadFromResource("programs/deadlock.json");
    }

    public static BenchmarkProgram doubleCheckedLocking() {
        return LOADER.loadFromResource("programs/double-checked-locking.json");
    }

    public static BenchmarkProgram lostUpdate() {
        return LOADER.loadFromResource("programs/lost-update.json");
    }

    public static BenchmarkProgram tornCounter() {
        return LOADER.loadFromResource("programs/torn-counter.json");
    }

    // Java fixtures: original Java implementations used as independent test baseline
    // These are used by tests to verify JSON migration correctness.
    // They must NOT be modified when JSON resources change.

    static BenchmarkProgram petersonJava() {
        PetersonState initial = PetersonState.of(false, false, 0);

        List<Step> thread0Steps = List.of(
            new WriteFlagStep(0, true),
            new WriteTurnStep(1),
            new BusyWaitStep(0, 1),
            new CSEnterStep(0),
            new CSExitStep(),
            new WriteFlagStep(0, false)
        );

        List<Step> thread1Steps = List.of(
            new WriteFlagStep(1, true),
            new WriteTurnStep(0),
            new BusyWaitStep(1, 0),
            new CSEnterStep(1),
            new CSExitStep(),
            new WriteFlagStep(1, false)
        );

        ModelThread t0 = new ModelThread(0, thread0Steps);
        ModelThread t1 = new ModelThread(1, thread1Steps);

        Program program = new Program(initial, List.of(t0, t1));
        return new BenchmarkProgram("peterson", program, "PASS");
    }

    static BenchmarkProgram brokenPetersonJava() {
        PetersonState initial = PetersonState.of(false, false, 0);

        List<Step> thread0Steps = List.of(
            new WriteFlagStep(0, true),
            new WriteTurnStep(1),
            new BusyWaitStep(0, 1),
            new CSEnterStep(0),
            new CSExitStep(),
            new WriteFlagStep(0, false)
        );

        List<Step> thread1Steps = List.of(
            new WriteFlagStep(1, true),
            new WriteTurnStep(0),
            new CSEnterStep(1),
            new CSExitStep(),
            new WriteFlagStep(1, false)
        );

        ModelThread t0 = new ModelThread(0, thread0Steps);
        ModelThread t1 = new ModelThread(1, thread1Steps);

        Program program = new Program(initial, List.of(t0, t1));

        Invariant invariant = (state, config) -> {
            PetersonState ps = (PetersonState) state;
            List<Integer> pcs = config.programCounters();
            boolean bothFlagsTrue = ps.flag(0) && ps.flag(1);
            boolean t0InCsZone = pcs.get(0) >= 3;
            boolean t1InCsZone = pcs.get(1) >= 2;
            return !(bothFlagsTrue && t0InCsZone && t1InCsZone);
        };

        return new BenchmarkProgram("broken-peterson", program, "VIOLATION", invariant);
    }

    static BenchmarkProgram brokenPetersonV2Java() {
        PetersonState initial = PetersonState.of(false, false, 0);

        List<Step> thread0Steps = List.of(
            new WriteFlagStep(0, true),
            new WriteTurnStep(1),
            new CSEnterStep(0),
            new CSExitStep(),
            new WriteFlagStep(0, false)
        );

        List<Step> thread1Steps = List.of(
            new WriteFlagStep(1, true),
            new WriteTurnStep(0),
            new BusyWaitStep(1, 0),
            new CSEnterStep(1),
            new CSExitStep(),
            new WriteFlagStep(1, false)
        );

        ModelThread t0 = new ModelThread(0, thread0Steps);
        ModelThread t1 = new ModelThread(1, thread1Steps);

        Program program = new Program(initial, List.of(t0, t1));

        Invariant invariant = (state, config) -> {
            PetersonState ps = (PetersonState) state;
            List<Integer> pcs = config.programCounters();
            boolean bothFlagsTrue = ps.flag(0) && ps.flag(1);
            boolean t0InCsZone = pcs.get(0) >= 2;
            boolean t1InCsZone = pcs.get(1) >= 3;
            return !(bothFlagsTrue && t0InCsZone && t1InCsZone);
        };

        return new BenchmarkProgram("broken-peterson-v2", program, "VIOLATION", invariant);
    }

    static BenchmarkProgram deadlockJava() {
        DeadlockState initial = DeadlockState.of(false, false);

        List<Step> thread0Steps = List.of(
            new DeadlockWriteFlagStep(0, true),
            new UnconditionalWaitStep(0, 1),
            new DeadlockWriteFlagStep(0, false)
        );

        List<Step> thread1Steps = List.of(
            new DeadlockWriteFlagStep(1, true),
            new UnconditionalWaitStep(1, 0),
            new DeadlockWriteFlagStep(1, false)
        );

        ModelThread t0 = new ModelThread(0, thread0Steps);
        ModelThread t1 = new ModelThread(1, thread1Steps);

        Program program = new Program(initial, List.of(t0, t1));

        return new BenchmarkProgram("deadlock", program, "DEADLOCK");
    }

    static BenchmarkProgram doubleCheckedLockingJava() {
        DclState initial = DclState.of(false);

        List<Step> thread0Steps = List.of(
            new DclReadInstanceStep(0),
            new DclLockStep(0),
            new DclReadInstanceStep(0),
            new DclInitSetInstanceStep(0),
            new DclInitSetInitializedStep(0),
            new DclUnlockStep(0)
        );

        List<Step> thread1Steps = List.of(
            new DclReadInstanceStep(1),
            new DclUseInstanceStep(1)
        );

        ModelThread t0 = new ModelThread(0, thread0Steps);
        ModelThread t1 = new ModelThread(1, thread1Steps);

        Program program = new Program(initial, List.of(t0, t1));

        Invariant invariant = (s, config) -> {
            DclState state = (DclState) s;
            if (state.observedInstance() != null && !state.initialized()) {
                return false;
            }
            return true;
        };

        return new BenchmarkProgram("double-checked-locking", program, "VIOLATION", invariant);
    }

    static BenchmarkProgram lostUpdateJava() {
        CounterState initial = CounterState.of(0);

        List<Step> thread0Steps = List.of(
            new ReadCounterStep(0),
            new WriteCounterStep(0)
        );

        List<Step> thread1Steps = List.of(
            new ReadCounterStep(1),
            new WriteCounterStep(1)
        );

        ModelThread t0 = new ModelThread(0, thread0Steps);
        ModelThread t1 = new ModelThread(1, thread1Steps);

        Program program = new Program(initial, List.of(t0, t1));

        Invariant invariant = (state, config) -> {
            CounterState cs = (CounterState) state;
            if (config.allTerminated()) {
                return cs.counter() == 2;
            }
            return true;
        };

        return new BenchmarkProgram("lost-update", program, "VIOLATION", invariant);
    }

    static BenchmarkProgram tornCounterJava() {
        PairState initial = PairState.of(0, 0);

        List<Step> thread0Steps = List.of(
            new WriteHighStep(0, 2),
            new WriteLowStep(0, 2)
        );

        List<Step> thread1Steps = List.of(
            new ReadSnapshotStep(1)
        );

        ModelThread t0 = new ModelThread(0, thread0Steps);
        ModelThread t1 = new ModelThread(1, thread1Steps);

        Program program = new Program(initial, List.of(t0, t1));

        Invariant invariant = (state, config) -> {
            PairState ps = (PairState) state;
            if (ps.hasObservation()) {
                if (ps.observedHigh() == 2 && ps.observedLow() != 2) {
                    return false;
                }
            }
            return true;
        };

        return new BenchmarkProgram("torn-counter", program, "VIOLATION", invariant);
    }

    // Test accessors for Java fixtures
    public static List<BenchmarkProgram> allJavaFixtures() {
        List<BenchmarkProgram> programs = new ArrayList<>();
        programs.add(petersonJava());
        programs.add(brokenPetersonJava());
        programs.add(brokenPetersonV2Java());
        programs.add(deadlockJava());
        programs.add(doubleCheckedLockingJava());
        programs.add(lostUpdateJava());
        programs.add(tornCounterJava());
        return programs;
    }
}