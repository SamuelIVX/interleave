package dev.samhb.interleave.bugs;

import org.junit.jupiter.api.Test;
import java.util.LinkedHashSet;
import java.util.Set;
import static org.junit.jupiter.api.Assertions.*;

class BugCorpusTest {

    /**
     * The corpus size is asserted because a count is the only thing that catches a program added to
     * {@code BugCorpus.all()} but omitted from some other enumeration -- the silent-omission failure
     * Spec 12.05 R8 names. Spec 12.05 added {@code lost-update-3t}, taking the corpus from seven to
     * eight. It must be bumped deliberately whenever a program is added, alongside the benchmark table,
     * the attestation inputs, and the README listing.
     */
    @Test
    void corpusHasExactlyEightPrograms() {
        assertEquals(8, BugCorpus.all().size(),
            "Expected exactly 8 programs in corpus, got " + BugCorpus.all().size()
                + ". If a program was added or removed, update this, the README corpus listing, "
                + "StatesExploredTable, and the soundness attestation inputs in the same commit.");
    }

    /**
     * Guards the omission the count alone cannot see: two corpus entries resolving to the same program
     * name would keep the size right while the enumeration had drifted.
     */
    @Test
    void corpusProgramNamesAreDistinct() {
        Set<String> names = new LinkedHashSet<>();
        for (BenchmarkProgram program : BugCorpus.all()) {
            assertTrue(names.add(program.name()),
                "Duplicate corpus program name: " + program.name());
        }
        assertEquals(BugCorpus.all().size(), names.size());
    }

    @Test
    void everyProgramHasValidVerdict() {
        Set<String> validVerdicts = Set.of("PASS", "VIOLATION", "DEADLOCK");
        for (BenchmarkProgram program : BugCorpus.all()) {
            assertTrue(validVerdicts.contains(program.expectedVerdict()),
                program.name() + " has invalid expectedVerdict: " + program.expectedVerdict());
        }
    }

    @Test
    void containsDoubleCheckedLocking() {
        assertTrue(BugCorpus.all().stream()
                .anyMatch(p -> "double-checked-locking".equals(p.name())),
            "Corpus should contain double-checked-locking");
    }

    @Test
    void containsLostUpdate() {
        assertTrue(BugCorpus.all().stream()
                .anyMatch(p -> "lost-update".equals(p.name())),
            "Corpus should contain lost-update");
    }

    @Test
    void containsTornCounter() {
        assertTrue(BugCorpus.all().stream()
                .anyMatch(p -> "torn-counter".equals(p.name())),
            "Corpus should contain torn-counter");
    }
}