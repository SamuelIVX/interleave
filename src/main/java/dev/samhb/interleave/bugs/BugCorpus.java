package dev.samhb.interleave.bugs;

import dev.samhb.interleave.format.ProgramLoader;
import dev.samhb.interleave.format.registry.RegistryException;
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
}