/** Strict resource-prefix JSON contracts and fresh Java artifacts consumed by browser CI. */
package dev.samhb.interleave;

import dev.samhb.interleave.bugs.BenchmarkProgram;
import dev.samhb.interleave.format.SourceProgramLoader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import static dev.samhb.interleave.testsupport.JsonAssertions.parseObject;
import static org.junit.jupiter.api.Assertions.*;

/** Checks exported values against literal wire contracts without reflecting private search state. */
class ResourcePrefixJsonTest {
    /** The raw wire shape excludes replay, private identities and ordinary terminal evidence. */
    @Test
    void exportsRawPrefixAndOptionalResultMember() {
        var model = model("shared int x = 0; thread 0 { atomic { x = 1; } atomic {} }");
        var result = limited(model, 2);
        var prefix = result.resourcePrefix().orElseThrow();
        var raw = parseObject(prefix.toJson());
        assertEquals(Set.of("outcome", "resourceLimitReason", "threads", "outcomes", "sourceDigest",
            "executedSteps", "programCounters", "terminalState"), raw.keySet());
        assertEquals("INCOMPLETE", raw.get("outcome").getAsString());
        assertEquals("MAX_STATES", raw.get("resourceLimitReason").getAsString());
        assertEquals("[0]", raw.get("threads").toString());
        assertEquals("[\"ADVANCED\"]", raw.get("outcomes").toString());
        assertEquals("[1]", raw.get("programCounters").toString());
        assertEquals(1, raw.getAsJsonObject("terminalState").getAsJsonObject("fields").get("x").getAsInt());
        assertEquals(model.program().sourceDigest(), raw.get("sourceDigest").getAsString());
        assertEquals(0, raw.getAsJsonArray("executedSteps").get(0).getAsJsonObject().get("instructionIndex").getAsInt());
        var run = parseObject(result.toJson());
        assertEquals(raw, run.get("resourcePrefix"));
        assertTrue(run.get("limitExceeded").getAsBoolean()); assertFalse(run.get("hasViolation").getAsBoolean());
        assertEquals("SOURCE", run.get("programKind").getAsString());
        assertEquals("EXACT", run.get("storeType").getAsString());
        for (String bucket : Set.of("failingTraces", "deadlockedTraces", "completedTraces", "incompleteTraces")) {
            assertTrue(run.getAsJsonArray(bucket).isEmpty());
        }
        assertFalse(parseObject(InterleaveRunner.builder().build().run(model.program()).toJson()).has("resourcePrefix"));
    }

    /** Root/time captures contain a real final snapshot despite having no committed steps. */
    @Test
    void exportsRootDeadlineWithoutInventingAnAction() {
        var model = model("shared int x = 4; thread 0 { atomic {} }");
        var result = InterleaveRunner.builder().maxTime(Duration.ZERO).build().run(model.program(), () -> 1000);
        var raw = parseObject(result.resourcePrefix().orElseThrow().toJson());
        assertEquals("MAX_TIME", raw.get("resourceLimitReason").getAsString());
        assertTrue(raw.getAsJsonArray("threads").isEmpty());
        assertTrue(raw.getAsJsonArray("outcomes").isEmpty());
        assertTrue(raw.getAsJsonArray("executedSteps").isEmpty());
        assertEquals("[0]", raw.get("programCounters").toString());
        assertEquals(4, raw.getAsJsonObject("terminalState").getAsJsonObject("fields").get("x").getAsInt());
    }

    /** A later resource stop preserves the earlier modeled cutoff and its run-level reason. */
    @Test
    void preservesPriorModeledCutoffBesideResourceEvidence() {
        var model = model("shared int x = 0; thread 0 { atomic { while (true) limit 0 {} } } thread 1 { atomic { x = 1; } atomic {} }");
        var result = limited(model, 2);
        var raw = parseObject(result.toJson());
        assertEquals("[\"LOOP_LIMIT\"]", raw.get("incompletenessReasons").toString());
        assertEquals(1, raw.getAsJsonArray("incompleteTraces").size());
        assertEquals("LOOP_LIMIT", raw.getAsJsonArray("incompleteTraces").get(0).getAsJsonObject().getAsJsonObject("cutoff").get("reason").getAsString());
        assertEquals("MAX_STATES", raw.get("resourceLimitReason").getAsString());
        var prefix = raw.getAsJsonObject("resourcePrefix");
        assertEquals("[1]", prefix.get("threads").toString());
        assertEquals("[0,1]", prefix.get("programCounters").toString());
        assertFalse(prefix.has("cutoff"));
    }

    /** Serialization uses captured values only; explicit replay and export cannot reclassify a run. */
    @Test
    void serializationDoesNotExecutePropertyAndKeepsReplaySeparate() {
        var model = model("shared int x = 0; thread 0 { atomic { x = 1; } atomic {} }");
        var calls = new AtomicInteger();
        var result = InterleaveRunner.builder().maxStates(2).invariant((s, c) -> { calls.incrementAndGet(); return true; }).build().run(model.program());
        var prefix = result.resourcePrefix().orElseThrow();
        assertEquals(1, calls.get());
        var before = parseObject(result.toJson());
        prefix.toJson(); result.toJson();
        assertEquals(1, calls.get());
        var replay = prefix.replay(model.program(), (s, c) -> { calls.incrementAndGet(); return true; });
        int afterReplay = calls.get();
        var raw = parseObject(replay.toJson());
        replay.toJson(); prefix.toJson(); result.toJson();
        assertEquals(afterReplay, calls.get());
        assertEquals(before, parseObject(result.toJson()));
        var observation = raw.getAsJsonObject("replay");
        assertEquals(Set.of("kind", "propertyChecked", "states"), observation.keySet());
        assertEquals("PREFIX", observation.get("kind").getAsString());
        assertTrue(observation.get("propertyChecked").getAsBoolean());
        assertEquals(2, observation.getAsJsonArray("states").size());
        assertEquals("INCOMPLETE", raw.get("outcome").getAsString());
        raw.remove("replay");
        assertEquals(parseObject(prefix.toJson()), raw);
    }

    /** Completed and deadlocked replay kinds are observations, never exhaustive PASS. */
    @Test
    void exportsTerminalObservationsWithoutAProperty() {
        for (String body : new String[]{"shared int x = 0; thread 0 { atomic {} }", "shared int x = 0; thread 0 { atomic when (false) { x = 1; } }"}) {
            var model = model(body);
            int limit = body.contains("when") ? 1 : 2;
            var raw = parseObject(limited(model, limit).resourcePrefix().orElseThrow().replay(model.program(), null).toJson());
            assertEquals("INCOMPLETE", raw.get("outcome").getAsString());
            var observation = raw.getAsJsonObject("replay");
            assertEquals(body.contains("when") ? "DEADLOCK" : "COMPLETED", observation.get("kind").getAsString());
            assertFalse(observation.get("propertyChecked").getAsBoolean());
            assertFalse(observation.has("failure"));
            assertEquals(limit, observation.getAsJsonArray("states").size());
        }
    }

    /** Actual final property errors retain their classified evidence and originating prefix.
     * @param runtime whether evaluation itself fails rather than returning false
     */
    @ParameterizedTest @ValueSource(booleans = {false, true})
    void exportsFailureAndDistinctReplayOwners(boolean runtime) {
        var first = model("shared int x = 0; thread 0 { atomic { x = 1; } atomic {} } invariant always (" +
            (runtime ? "1 % (1 - x) == 0" : "x != 1") + ");");
        var second = model("shared int x = 7; thread 0 { atomic {} }");
        var prefix = limited(first, 2).resourcePrefix().orElseThrow();
        var replay = prefix.replay(first.program(), first.invariant().orElseThrow());
        var other = limited(second, 1).resourcePrefix().orElseThrow().replay(second.program(), null);
        var raw = parseObject(replay.toJson());
        assertEquals(first.program().sourceDigest(), raw.get("sourceDigest").getAsString());
        assertEquals(second.program().sourceDigest(), parseObject(other.toJson()).get("sourceDigest").getAsString());
        var observation = raw.getAsJsonObject("replay");
        assertEquals(Set.of("kind", "propertyChecked", "states", "failure"), observation.keySet());
        assertEquals("VIOLATION", observation.get("kind").getAsString());
        assertEquals(runtime ? "RUNTIME_ERROR" : "INVARIANT", observation.getAsJsonObject("failure").get("kind").getAsString());
        if (runtime) assertEquals("MODULO_BY_ZERO", observation.getAsJsonObject("failure").get("category").getAsString());
        assertEquals("PROPERTY", observation.getAsJsonObject("failure").get("phase").getAsString());
        assertFalse(raw.has("failure")); assertFalse(raw.has("cutoff"));
    }

    /** Replaying no choices does not attempt a pending omitted action or invent its cutoff. */
    @Test
    void doesNotAttemptPendingBoundDuringEmptyPrefixReplay() {
        var model = model("shared int x = 0; thread 0 { while (true) limit 0 {} }");
        var raw = parseObject(limited(model, 1).resourcePrefix().orElseThrow().replay(model.program(), null).toJson());
        assertEquals("MAX_STATES", raw.get("resourceLimitReason").getAsString());
        assertEquals("PREFIX", raw.getAsJsonObject("replay").get("kind").getAsString());
        assertFalse(raw.has("cutoff")); assertFalse(raw.getAsJsonObject("replay").has("cutoff"));
        assertFalse(raw.getAsJsonObject("replay").has("failure"));
    }

    /** Typed arrays/locals and hostile source names retain exact JSON values without HTML escaping.
     * @param directory isolated source directory
     * @throws Exception if the source fixture cannot be written
     */
    @Test
    void escapesSourceNamesAndPreservesTypedSnapshots(@TempDir Path directory) throws Exception {
        String fileName = "wire\"\\\n<>&='雪.ilv";
        String name = directory.resolve(fileName).toString();
        Files.writeString(Path.of(name), "program wire { shared int[] a = [1,2]; shared bool flag = true; local int[] own = [3,4]; thread 0 { atomic { a[0] = 9; local.own[1] = 8; } atomic {} } }");
        var model = new SourceProgramLoader().loadFromFile(Path.of(name));
        var prefix = limited(model, 2).resourcePrefix().orElseThrow();
        var raw = parseObject(prefix.toJson());
        assertEquals(fileName, raw.getAsJsonArray("executedSteps").get(0).getAsJsonObject().getAsJsonObject("source").get("file").getAsString());
        var snapshot = raw.getAsJsonObject("terminalState");
        assertEquals("[9,2]", snapshot.getAsJsonObject("fields").get("a").toString());
        assertTrue(snapshot.getAsJsonObject("fields").get("flag").getAsBoolean());
        assertEquals("[3,8]", snapshot.getAsJsonObject("locals").getAsJsonObject("0").get("own").toString());
        assertTrue(prefix.toJson().contains("<>&='雪"));
        var replay = parseObject(prefix.replay(model.program(), null).toJson()).getAsJsonObject("replay");
        assertEquals(snapshot, replay.getAsJsonArray("states").get(1));
        write("hostile", prefix.toJson());
    }

    /** Produces current Java payloads for consumer tests rather than maintaining copied JSON fixtures.
     * @throws Exception if an artifact cannot be written
     */
    @Test
    void writesFreshBrowserArtifacts() throws Exception {
        var model = model("shared int x = 0; thread 0 { atomic { x = 1; } atomic {} } invariant always (x != 1);");
        var result = limited(model, 2);
        var prefix = result.resourcePrefix().orElseThrow();
        write("result", result.toJson()); write("raw", prefix.toJson());
        write("replayed", prefix.replay(model.program(), model.invariant().orElseThrow()).toJson());
        write("root", limited(model, 1).resourcePrefix().orElseThrow().toJson());
        write("time", InterleaveRunner.builder().maxTime(Duration.ZERO).build().run(model.program(), () -> 1000)
            .resourcePrefix().orElseThrow().toJson());
        var completed = model("shared int x = 0; thread 0 { atomic { x = 5; } }");
        write("completed", limited(completed, 2).resourcePrefix().orElseThrow().replay(completed.program(), null).toJson());
        var deadlock = model("shared int x = 0; thread 0 { atomic when (false) { x = 1; } }");
        write("deadlock", limited(deadlock, 1).resourcePrefix().orElseThrow().replay(deadlock.program(), null).toJson());
        var failure = model("shared int a = 0; shared int b = 0; thread 0 { atomic { a = 1; } } thread 1 { atomic { b = 1; } } invariant always (a == 0 || b == 0);");
        var earlier = InterleaveRunner.builder().maxStates(4).invariant(failure.invariant().orElseThrow()).build().run(failure.program());
        assertTrue(earlier.hasViolation()); assertTrue(earlier.resourcePrefix().isPresent());
        write("earlier-failure", earlier.toJson());
    }

    /** Loads deterministic source with a stable diagnostic name.
     * @param body source declarations and optional invariant
     * @return actual compiler-produced benchmark
     */
    private static BenchmarkProgram model(String body) {
        return new SourceProgramLoader().load("program wire { " + body + " }");
    }

    /** Runs the actual explorer to a state allowance without implicitly replaying its result.
     * @param model actual compiled source
     * @param maxStates state allowance
     * @return original resource-interrupted result
     */
    private static TestResult limited(BenchmarkProgram model, int maxStates) {
        return InterleaveRunner.builder().strategy(Strategy.DFS).maxStates(maxStates).build().run(model.program());
    }

    /** Writes a generated fixture in the ignored build directory.
     * @param suffix consumer scenario
     * @param json actual Java serialization
     * @throws Exception if the output cannot be written
     */
    private static void write(String suffix, String json) throws Exception {
        Files.writeString(Path.of("build/source-resource-" + suffix + ".json"), json);
    }
}
