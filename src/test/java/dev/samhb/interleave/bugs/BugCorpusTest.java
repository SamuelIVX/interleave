package dev.samhb.interleave.bugs;

import org.junit.jupiter.api.Test;
import java.util.Set;
import static org.junit.jupiter.api.Assertions.*;

class BugCorpusTest {

    @Test
    void corpusHasAtLeastSixPrograms() {
        assertTrue(BugCorpus.all().size() >= 6,
            "Expected at least 6 programs in corpus, got " + BugCorpus.all().size());
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