package dev.samhb.interleave.format;

import dev.samhb.interleave.bugs.BenchmarkProgram;
import dev.samhb.interleave.bugs.BugCorpus;
import dev.samhb.interleave.core.Program;
import dev.samhb.interleave.search.Invariant;
import dev.samhb.interleave.format.registry.RegistryException;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class ProgramLoaderTest {

    private final ProgramLoader loader = new ProgramLoader();

    /**
     * Every corpus program that has a Java fixture is verified against it.
     *
     * <p>The fixtures exist to prove the JSON resources faithfully re-encode the original Java
     * implementations, so only programs that were <em>migrated</em> can be checked this way. Spec 12.05
     * added {@code lost-update-3t}, which is newly authored in the declarative format and has no Java
     * predecessor, so there is nothing for it to be equivalent to.
     *
     * <p>That exclusion is stated here rather than left implicit, and
     * {@link #everyCorpusProgramWithoutAJavaFixtureIsDeclarative} asserts the two sets partition the
     * corpus. So a future <em>typed</em> program added without a fixture fails that test instead of
     * quietly escaping equivalence checking, which is the silent-omission failure Spec 12.05 R8 names.
     */
    /**
     * The corpus partitions into migrated programs, which have a Java fixture and are checked against
     * it, and newly authored declarative programs, which do not.
     *
     * <p>This is what keeps {@link #everyMigratedProgram_equivalentToJava} honest. That test iterates a
     * hand-written list, so a program added to {@code BugCorpus.all()} but not to that list would be
     * silently unchecked. Here the two sets are compared by name against the whole corpus, so such an
     * omission fails instead of passing quietly -- the silent-omission failure Spec 12.05 R8 names.
     */
    @Test
    void everyCorpusProgramWithoutAJavaFixtureIsDeclarative() {
        Set<String> fixtureNames = new HashSet<>();
        for (BenchmarkProgram fixture : BugCorpus.allJavaFixtures()) {
            fixtureNames.add(fixture.name());
        }

        Set<String> withoutFixture = new HashSet<>();
        for (BenchmarkProgram program : BugCorpus.all()) {
            if (!fixtureNames.contains(program.name())) {
                withoutFixture.add(program.name());
            }
        }

        // Naming the expected set, rather than just its size, so an added program forces a decision.
        assertEquals(Set.of("lost-update-3t"), withoutFixture,
            "Exactly the declarative, newly authored programs should lack a Java fixture. A typed program "
                + "must have one, because the fixture is what verifies its JSON encoding. If this set "
                + "changes, update everyMigratedProgram_equivalentToJava and its javadoc.");

        assertEquals(BugCorpus.all().size(), fixtureNames.size() + withoutFixture.size(),
            "Fixtures and non-fixtures must partition the corpus; a program in neither set is unchecked");
    }

    @Test
    void everyMigratedProgram_equivalentToJava() {
        // Load the migrated (typed) programs from JSON and compare with Java BugCorpus equivalents
        // Use Java fixtures as baseline (NOT BugCorpus.all() which loads from JSON)
        List<BenchmarkProgram> jsonPrograms = List.of(
            loader.loadFromResource("programs/peterson.json"),
            loader.loadFromResource("programs/broken-peterson.json"),
            loader.loadFromResource("programs/broken-peterson-v2.json"),
            loader.loadFromResource("programs/deadlock.json"),
            loader.loadFromResource("programs/double-checked-locking.json"),
            loader.loadFromResource("programs/lost-update.json"),
            loader.loadFromResource("programs/torn-counter.json")
        );

        List<BenchmarkProgram> javaPrograms = BugCorpus.allJavaFixtures();

        assertEquals(javaPrograms.size(), jsonPrograms.size(),
            "Every migrated program needs a Java fixture; these two lists must stay the same length");

        for (int i = 0; i < jsonPrograms.size(); i++) {
            BenchmarkProgram jsonProg = jsonPrograms.get(i);
            BenchmarkProgram javaProg = javaPrograms.get(i);

            // Same name
            assertEquals(javaProg.name(), jsonProg.name(),
                "Program " + i + " name mismatch");

            // Same expected verdict
            assertEquals(javaProg.expectedVerdict(), jsonProg.expectedVerdict(),
                "Program " + i + " expectedVerdict mismatch");

            // Same invariant presence
            assertEquals(javaProg.invariant().isPresent(), jsonProg.invariant().isPresent(),
                "Program " + i + " invariant presence mismatch");

            // Run through DFS and compare results
            var jsonResult = runDfs(jsonProg);
            var javaResult = runDfs(javaProg);

            assertEquals(javaResult.statesExplored(), jsonResult.statesExplored(),
                "Program " + i + " statesExplored mismatch");

            // Same trace outcomes
            var jsonTraces = jsonResult.traces();
            var javaTraces = javaResult.traces();
            assertEquals(javaTraces.size(), jsonTraces.size(),
                "Program " + i + " trace count mismatch");

            long javaViolations = javaTraces.stream().filter(t -> t.outcome().name().equals("VIOLATION")).count();
            long jsonViolations = jsonTraces.stream().filter(t -> t.outcome().name().equals("VIOLATION")).count();
            assertEquals(javaViolations, jsonViolations, "Program " + i + " violation count mismatch");

            long javaDeadlocks = javaTraces.stream().filter(t -> t.outcome().name().equals("DEADLOCK")).count();
            long jsonDeadlocks = jsonTraces.stream().filter(t -> t.outcome().name().equals("DEADLOCK")).count();
            assertEquals(javaDeadlocks, jsonDeadlocks, "Program " + i + " deadlock count mismatch");

            // Same verdict
            assertEquals(javaProg.expectedVerdict(), jsonProg.expectedVerdict(),
                "Program " + i + " expectedVerdict mismatch");
        }
    }

    /** Tests runDfs. */
    private dev.samhb.interleave.search.DfsResult runDfs(BenchmarkProgram program) {
        var explorer = new dev.samhb.interleave.search.DfsExplorer();
        return explorer.explore(program.program(), program.invariant().orElse(null), new dev.samhb.interleave.state.HashingStateStore(), null);
    }

    @Test
    /** Tests loadFromFile_lostUpdate_matchesBugCorpusResult. */
    void loadFromFile_lostUpdate_matchesBugCorpusResult() {
        BenchmarkProgram program = loader.loadFromFile(
            java.nio.file.Paths.get("src/main/resources/programs/lost-update.json")
        );
        assertEquals("lost-update", program.name());
        assertEquals("VIOLATION", program.expectedVerdict());
        assertTrue(program.invariant().isPresent());
    }

    @Test
    /** Tests loadInvalidJson_throwsRegistryException. */
    void loadInvalidJson_throwsRegistryException() {
        String invalidJson = "{ invalid json";
        RegistryException ex = assertThrows(dev.samhb.interleave.format.registry.RegistryException.class,
            () -> loader.load(invalidJson));
        assertTrue(ex.getMessage().contains("Invalid JSON"));
    }

    @Test
    /** Tests loadUnknownStepType_throwsRegistryException. */
    void loadUnknownStepType_throwsRegistryException() {
        String json = """
            {
              "format": "typed",
              "name": "test",
              "state": {"type": "counter", "counter": 0},
              "threads": [{"id": 0, "steps": [{"type": "unknown_step"}]}]
            }
            """;
        RegistryException ex = assertThrows(dev.samhb.interleave.format.registry.RegistryException.class,
            () -> loader.load(json));
        assertTrue(ex.getMessage().contains("Unknown step type"));
    }

    @Test
    /** Tests loadIncompatibleStep_throwsRegistryException. */
    void loadIncompatibleStep_throwsRegistryException() {
        String json = """
            {
              "format": "typed",
              "name": "test",
              "state": {"type": "peterson", "flags": [false, false], "turn": 0},
              "threads": [{"id": 0, "steps": [{"type": "write_counter"}]}]
            }
            """;
        RegistryException ex = assertThrows(dev.samhb.interleave.format.registry.RegistryException.class,
            () -> loader.load(json));
        assertTrue(ex.getMessage().contains("not compatible"));
    }

    @Test
    /** Tests loadNonSequentialThreadIds_throwsRegistryException. */
    void loadNonSequentialThreadIds_throwsRegistryException() {
        String json = """
            {
              "format": "typed",
              "name": "test",
              "state": {"type": "counter", "counter": 0},
              "threads": [
                {"id": 0, "steps": [{"type": "read_counter"}]},
                {"id": 2, "steps": [{"type": "read_counter"}]}
              ]
            }
            """;
        RegistryException ex = assertThrows(dev.samhb.interleave.format.registry.RegistryException.class,
            () -> loader.load(json));
        assertTrue(ex.getMessage().contains("Thread ID at index 1 must be 1"));
    }

    @Test
    /** Tests loadMissingThreadId_throwsRegistryException. */
    void loadMissingThreadId_throwsRegistryException() {
        String json = """
            {
              "format": "typed",
              "name": "test",
              "state": {"type": "counter", "counter": 0},
              "threads": [
                {"id": 0, "steps": [{"type": "read_counter"}]},
                {"steps": [{"type": "read_counter"}]}
              ]
            }
            """;
        RegistryException ex = assertThrows(dev.samhb.interleave.format.registry.RegistryException.class,
            () -> loader.load(json));
        assertTrue(ex.getMessage().contains("missing required 'id'"));
    }

    @Test
    /** Tests loadThreadParamMismatch_throwsRegistryException. */
    void loadThreadParamMismatch_throwsRegistryException() {
        String json = """
            {
              "format": "typed",
              "name": "test",
              "state": {"type": "counter", "counter": 0},
              "threads": [{"id": 0, "steps": [{"type": "read_counter", "thread": 5}]}]
            }
            """;
        RegistryException ex = assertThrows(dev.samhb.interleave.format.registry.RegistryException.class,
            () -> loader.load(json));
        assertTrue(ex.getMessage().contains("thread") && ex.getMessage().contains("5"));
    }

    @Test
    /** Tests loadOtherOutOfRange_throwsRegistryException. */
    void loadOtherOutOfRange_throwsRegistryException() {
        String json = """
            {
              "format": "typed",
              "name": "test",
              "state": {"type": "peterson", "flags": [false, false], "turn": 0},
              "threads": [{"id": 0, "steps": [{"type": "busy_wait", "other": 5}]}]
            }
            """;
        RegistryException ex = assertThrows(dev.samhb.interleave.format.registry.RegistryException.class,
            () -> loader.load(json));
        assertTrue(ex.getMessage().contains("other") && ex.getMessage().contains("5"));
    }

    @Test
    /** Tests loadMutualExclusionPeterson_oneThread_throwsRegistryException. */
    void loadMutualExclusionPeterson_oneThread_throwsRegistryException() {
        String json = """
            {
              "format": "typed",
              "name": "test",
              "state": {"type": "peterson", "flags": [false, false], "turn": 0},
              "threads": [{"id": 0, "steps": [{"type": "write_flag", "value": true}]}],
              "invariant": {"type": "mutual_exclusion_peterson", "thread0_cs_pc": 1, "thread1_cs_pc": 1}
            }
            """;
        RegistryException ex = assertThrows(dev.samhb.interleave.format.registry.RegistryException.class,
            () -> loader.load(json));
        assertTrue(ex.getMessage().contains("mutual_exclusion_peterson") && ex.getMessage().contains("two threads"));
    }

    @Test
    /** Tests loadMutualExclusionPeterson_threeThreads_throwsRegistryException. */
    void loadMutualExclusionPeterson_threeThreads_throwsRegistryException() {
        String json = """
            {
              "format": "typed",
              "name": "test",
              "state": {"type": "peterson", "flags": [false, false], "turn": 0},
              "threads": [
                {"id": 0, "steps": [{"type": "write_flag", "value": true}]},
                {"id": 1, "steps": [{"type": "write_flag", "value": true}]},
                {"id": 2, "steps": [{"type": "write_flag", "value": true}]}
              ],
              "invariant": {"type": "mutual_exclusion_peterson", "thread0_cs_pc": 1, "thread1_cs_pc": 1}
            }
            """;
        RegistryException ex = assertThrows(dev.samhb.interleave.format.registry.RegistryException.class,
            () -> loader.load(json));
        assertTrue(ex.getMessage().contains("mutual_exclusion_peterson") && ex.getMessage().contains("two threads"));
    }

    @Test
    /** Tests loadMutualExclusionPeterson_twoThreads_succeeds. */
    void loadMutualExclusionPeterson_twoThreads_succeeds() {
        String json = """
            {
              "format": "typed",
              "name": "test",
              "state": {"type": "peterson", "flags": [false, false], "turn": 0},
              "threads": [
                {"id": 0, "steps": [{"type": "write_flag", "value": true}]},
                {"id": 1, "steps": [{"type": "write_flag", "value": true}]}
              ],
              "invariant": {"type": "mutual_exclusion_peterson", "thread0_cs_pc": 1, "thread1_cs_pc": 1}
            }
            """;
        BenchmarkProgram program = loader.load(json);
        assertNotNull(program);
        assertEquals("test", program.name());
    }

    @Test
    /** Tests loadMissingState_throwsRegistryException. */
    void loadMissingState_throwsRegistryException() {
        String json = """
            {
              "format": "typed",
              "name": "test",
              "threads": [{"id": 0, "steps": [{"type": "read_counter"}]}]
            }
            """;
        RegistryException ex = assertThrows(dev.samhb.interleave.format.registry.RegistryException.class,
            () -> loader.load(json));
        assertTrue(ex.getMessage().contains("State definition is required"));
    }

    @Test
    /** Tests loadMissingThreads_throwsRegistryException. */
    void loadMissingThreads_throwsRegistryException() {
        String json = """
            {
              "format": "typed",
              "name": "test",
              "state": {"type": "counter", "counter": 0}
            }
            """;
        RegistryException ex = assertThrows(dev.samhb.interleave.format.registry.RegistryException.class,
            () -> loader.load(json));
        assertTrue(ex.getMessage().contains("At least one thread"));
    }

    @Test
    /** Tests loadEmptyThreads_throwsRegistryException. */
    void loadEmptyThreads_throwsRegistryException() {
        String json = """
            {
              "format": "typed",
              "name": "test",
              "state": {"type": "counter", "counter": 0},
              "threads": []
            }
            """;
        RegistryException ex = assertThrows(dev.samhb.interleave.format.registry.RegistryException.class,
            () -> loader.load(json));
        assertTrue(ex.getMessage().contains("At least one thread"));
    }

    @Test
    /** Tests loadThreadWithNoSteps_throwsRegistryException. */
    void loadThreadWithNoSteps_throwsRegistryException() {
        String json = """
            {
              "format": "typed",
              "name": "test",
              "state": {"type": "counter", "counter": 0},
              "threads": [{"id": 0, "steps": []}]
            }
            """;
        RegistryException ex = assertThrows(dev.samhb.interleave.format.registry.RegistryException.class,
            () -> loader.load(json));
        assertTrue(ex.getMessage().contains("Thread 0 must have at least one step"));
    }

    @Test
    /** Tests loadInvalidExpectedVerdict_throwsRegistryException. */
    void loadInvalidExpectedVerdict_throwsRegistryException() {
        String json = """
            {
              "format": "typed",
              "name": "test",
              "state": {"type": "counter", "counter": 0},
              "threads": [{"id": 0, "steps": [{"type": "read_counter"}]}],
              "expected_verdict": "MAYBE"
            }
            """;
        RegistryException ex = assertThrows(dev.samhb.interleave.format.registry.RegistryException.class,
            () -> loader.load(json));
        assertTrue(ex.getMessage().contains("Invalid expected_verdict"));
    }

    @Test
    /** Tests loadInvariantIncompatibleWithState_throwsRegistryException. */
    void loadInvariantIncompatibleWithState_throwsRegistryException() {
        String json = """
            {
              "format": "typed",
              "name": "test",
              "state": {"type": "peterson", "flags": [false, false], "turn": 0},
              "threads": [{"id": 0, "steps": [{"type": "write_flag", "value": true}]}],
              "invariant": {"type": "counter_equals", "expected": 2}
            }
            """;
        RegistryException ex = assertThrows(dev.samhb.interleave.format.registry.RegistryException.class,
            () -> loader.load(json));
        assertTrue(ex.getMessage().contains("not compatible"));
    }

    @Test
    /** Tests loadProgramWithoutExpectedVerdict_succeeds. */
    void loadProgramWithoutExpectedVerdict_succeeds() {
        String json = """
            {
              "format": "typed",
              "name": "test",
              "state": {"type": "counter", "counter": 0},
              "threads": [{"id": 0, "steps": [{"type": "read_counter"}]}]
            }
            """;
        BenchmarkProgram program = loader.load(json);
        assertNull(program.expectedVerdict());
    }

    @Test
    /** Tests loadProgramWithoutInvariant_succeeds. */
    void loadProgramWithoutInvariant_succeeds() {
        String json = """
            {
              "format": "typed",
              "name": "test",
              "state": {"type": "counter", "counter": 0},
              "threads": [{"id": 0, "steps": [{"type": "read_counter"}]}],
              "expected_verdict": "PASS"
            }
            """;
        BenchmarkProgram program = loader.load(json);
        assertFalse(program.invariant().isPresent());
        assertEquals("PASS", program.expectedVerdict());
    }

    @Test
    /** Tests threadParameterDefaultsToOwnId. */
    void threadParameterDefaultsToOwnId() {
        String json = """
            {
              "format": "typed",
              "name": "test",
              "state": {"type": "counter", "counter": 0},
              "threads": [
                {"id": 0, "steps": [{"type": "read_counter"}]},
                {"id": 1, "steps": [{"type": "read_counter"}]}
              ]
            }
            """;
        BenchmarkProgram program = loader.load(json);
        // Should not throw - thread defaults to owning thread's ID
        assertEquals("test", program.name());
    }
}
