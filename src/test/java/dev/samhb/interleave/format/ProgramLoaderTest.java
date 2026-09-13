package dev.samhb.interleave.format;

import dev.samhb.interleave.bugs.BenchmarkProgram;
import dev.samhb.interleave.bugs.BugCorpus;
import dev.samhb.interleave.core.Program;
import dev.samhb.interleave.search.Invariant;
import dev.samhb.interleave.format.registry.RegistryException;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class ProgramLoaderTest {

    private final ProgramLoader loader = new ProgramLoader();

    @Test
    void loadAllSevenPrograms_equivalentToJava() {
        // Load all 7 programs from JSON and compare with Java BugCorpus equivalents
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

        assertEquals(7, jsonPrograms.size());
        assertEquals(7, javaPrograms.size());

        for (int i = 0; i < 7; i++) {
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

    private dev.samhb.interleave.search.DfsResult runDfs(BenchmarkProgram program) {
        var explorer = new dev.samhb.interleave.search.DfsExplorer();
        return explorer.explore(program.program(), program.invariant().orElse(null), new dev.samhb.interleave.state.HashingStateStore(), null);
    }

    @Test
    void loadFromFile_lostUpdate_matchesBugCorpusResult() {
        BenchmarkProgram program = loader.loadFromFile(
            java.nio.file.Paths.get("src/main/resources/programs/lost-update.json")
        );
        assertEquals("lost-update", program.name());
        assertEquals("VIOLATION", program.expectedVerdict());
        assertTrue(program.invariant().isPresent());
    }

    @Test
    void loadInvalidJson_throwsRegistryException() {
        String invalidJson = "{ invalid json";
        RegistryException ex = assertThrows(dev.samhb.interleave.format.registry.RegistryException.class,
            () -> loader.load(invalidJson));
        assertTrue(ex.getMessage().contains("Invalid JSON"));
    }

    @Test
    void loadUnknownStepType_throwsRegistryException() {
        String json = """
            {
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
    void loadIncompatibleStep_throwsRegistryException() {
        String json = """
            {
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
    void loadNonSequentialThreadIds_throwsRegistryException() {
        String json = """
            {
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
    void loadMissingThreadId_throwsRegistryException() {
        String json = """
            {
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
    void loadThreadParamMismatch_throwsRegistryException() {
        String json = """
            {
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
    void loadOtherOutOfRange_throwsRegistryException() {
        String json = """
            {
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
    void loadMissingState_throwsRegistryException() {
        String json = """
            {
              "name": "test",
              "threads": [{"id": 0, "steps": [{"type": "read_counter"}]}]
            }
            """;
        RegistryException ex = assertThrows(dev.samhb.interleave.format.registry.RegistryException.class,
            () -> loader.load(json));
        assertTrue(ex.getMessage().contains("State definition is required"));
    }

    @Test
    void loadMissingThreads_throwsRegistryException() {
        String json = """
            {
              "name": "test",
              "state": {"type": "counter", "counter": 0}
            }
            """;
        RegistryException ex = assertThrows(dev.samhb.interleave.format.registry.RegistryException.class,
            () -> loader.load(json));
        assertTrue(ex.getMessage().contains("At least one thread"));
    }

    @Test
    void loadEmptyThreads_throwsRegistryException() {
        String json = """
            {
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
    void loadThreadWithNoSteps_throwsRegistryException() {
        String json = """
            {
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
    void loadInvalidExpectedVerdict_throwsRegistryException() {
        String json = """
            {
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
    void loadInvariantIncompatibleWithState_throwsRegistryException() {
        String json = """
            {
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
    void loadProgramWithoutExpectedVerdict_succeeds() {
        String json = """
            {
              "name": "test",
              "state": {"type": "counter", "counter": 0},
              "threads": [{"id": 0, "steps": [{"type": "read_counter"}]}]
            }
            """;
        BenchmarkProgram program = loader.load(json);
        assertNull(program.expectedVerdict());
    }

    @Test
    void loadProgramWithoutInvariant_succeeds() {
        String json = """
            {
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
    void threadParameterDefaultsToOwnId() {
        String json = """
            {
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