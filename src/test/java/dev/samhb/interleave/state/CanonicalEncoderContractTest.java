package dev.samhb.interleave.state;

import dev.samhb.interleave.bugs.BenchmarkProgram;
import dev.samhb.interleave.bugs.BugCorpus;
import dev.samhb.interleave.core.Configuration;
import dev.samhb.interleave.core.PetersonState;
import dev.samhb.interleave.core.SharedState;
import dev.samhb.interleave.search.DfsExplorer;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Base64;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Contract tests for {@link CanonicalEncoder} — Spec 12.01.
 *
 * <p>The encoder is upstream of both stores' visited-key, so a lossy encoder does not crash or
 * misbehave: two distinct states collide, a store answers "already visited" for an unvisited state,
 * and a reachable violation is pruned silently. No store-level differential test can catch that,
 * because both stores share the encoder — two wrong answers agree. These tests pin the properties
 * the stores depend on.
 *
 * <p><b>Scope note.</b> R1 asserts injectivity of the <em>full store key</em> —
 * {@code encode(state) + "|" + programCounters} — the shape
 * {@link HashingStateStore} actually builds, because that is what the store needs to be injective.
 * Injectivity of the state encoding <em>alone</em> is strictly stronger than either store requires and
 * is owned by Spec 12.07, which found {@code DeadlockState} silently omitting a field. Asserting only
 * the weaker property here is deliberate: it is the property the stores actually depend on.
 */
class CanonicalEncoderContractTest {

    private static final CanonicalEncoder ENCODER = new CanonicalEncoder();

    /** The exact key shape {@link HashingStateStore} builds, so R1 tests what the store really uses. */
    private static String storeKey(Configuration config) {
        return Base64.getEncoder().encodeToString(ENCODER.encode(config.state()))
                + "|" + config.programCounters().toString();
    }

    /**
     * A search position, identified <em>without</em> the encoder.
     *
     * <p>{@code Configuration} itself defines no {@code equals}/{@code hashCode}, so it compares by
     * identity — which is why the same position reached under a bug program and its JSON twin counted
     * as two configurations sharing one key. That is not a collision; it is one position, reached twice.
     *
     * <p>Position identity here comes from {@link SharedState} value equality (every implementation
     * overrides {@code equals}/{@code hashCode} — verified in Spec 12.07) plus the program counters.
     * Crucially this is computed <b>without</b> the encoder, so the R1 assertion below is not circular:
     * the count of positions is established independently, and the encoder is then measured against it.
     */
    private record Position(SharedState state, List<Integer> programCounters) {
    }

    /**
     * A {@link dev.samhb.interleave.search.StateStore} keyed by value equality, never by encoding.
     *
     * <p>This is the mechanism that keeps R1 honest. {@link DfsExplorer}'s default store keys on
     * {@code CanonicalEncoder.encode}, the same encoder R1 asserts injectivity for, so a lossy encoder
     * would prune the sample before the assertion ever sees it — the corruption would filter its own
     * evidence. This store keys on {@link SharedState} value equality (every implementation overrides
     * {@code equals}/{@code hashCode}, verified in Spec 12.07) plus the program counters, so the walk
     * explores the true reachable set no matter what the encoder does.
     *
     * <p>It is also not merely defensive. Measured 2026-10-01 with the encoder <em>correct</em>: the
     * encoder-keyed walk reaches **206** configurations across the corpus, the value-keyed walk
     * reaches **212**. Six positions are being pruned right now by an encoder that has no known
     * collision, so the default walk was already sampling a filtered subset — R1 could only ever
     * detect a collision that survived the filter keyed on the encoder it is testing.
     */
    private static final class ValueKeyedStore implements dev.samhb.interleave.search.StateStore {

        private final Set<Position> seen = new LinkedHashSet<>();

        @Override
        public boolean isVisited(Configuration config) {
            return seen.contains(new Position(config.state(), config.programCounters()));
        }

        @Override
        public void markVisited(Configuration config) {
            seen.add(new Position(config.state(), config.programCounters()));
        }

        @Override
        public void clear() {
            seen.clear();
        }
    }

    /**
     * R1 — injectivity of the store key over the reachable state space.
     *
     * <p>Configurations are collected by exploring, never hand-picked: hand-authored states can be
     * chosen to collide, which is the exact failure under test.
     *
     * <p>Two details make this falsifiable rather than decorative. The content key is a Base64
     * {@link String}, never a {@code byte[]} — {@code byte[]} uses identity equality, so a
     * {@code HashSet} of arrays reports N distinct objects for N references and can <em>never</em>
     * detect a collision. And deduplication is by {@link SharedState} value equality, never by encoded
     * bytes: deduplicating by output and then asserting outputs differ is circular, and would pass
     * even for a constant encoder.
     *
     * <p><b>The walk itself must not use the encoder.</b> {@link DfsExplorer} defaults to a
     * {@link HashingStateStore}, which prunes on the very encoding under test — so a colliding encoder
     * would shrink the very sample R1 measures it over. The corruption would then be partly invisible
     * to the assertion meant to detect it, which is the worst possible arrangement: the sample is
     * filtered by the thing under test, so the test can only notice collisions that survive the
     * filter. {@link ValueKeyedStore} below exists to break that circularity.
     */
    @Test
    @DisplayName("R1: distinct reachable configurations produce distinct store keys")
    void storeKey_isInjective_overReachableConfigurations() {
        Map<Position, String> keyByPosition = new LinkedHashMap<>();

        for (BenchmarkProgram program : BugCorpus.all()) {
            // The walk uses ValueKeyedStore so the sample is independent of the encoder under test.
            // Positions arrive through the visitor, which DfsExplorer calls for every configuration it
            // visits — including ones a lossy encoder would otherwise have pruned.
            new DfsExplorer().explore(program.program(), null, new ValueKeyedStore(),
                    config -> keyByPosition.putIfAbsent(
                            new Position(config.state(), config.programCounters()), storeKey(config)));
        }

        assertFalse(keyByPosition.isEmpty(), "the walk must reach some configurations");

        Set<String> keys = new HashSet<>(keyByPosition.values());
        assertEquals(keyByPosition.size(), keys.size(),
                "two distinct positions must not share a store key — a shared key means the store "
                        + "reports one as already visited and prunes the other, silently losing a "
                        + "branch that might hold the only reachable violation");

        // The count is recorded rather than floored at the spec's >=100 state-value floor, because
        // that floor is contingent on Spec 12.05 adding a higher-thread program: the corpus reaches
        // only 44 distinct states today (72 summed per program, largest single program 15), so
        // asserting it here would assert a precondition that does not hold. See 12.01 "R1's floor is
        // measured".
        //
        // The >=150 guard below is therefore NOT that deferred floor -- it is a drift tripwire on the
        // quantity R1 actually measures, store POSITIONS, which is 160. Three distinct quantities are
        // in play and conflating them is the easiest mistake to make here:
        //   44  distinct SharedState values, unioned across programs   (state only; counters ignored)
        //   72  the same states summed per program, so shared ones count once per program
        //  160  distinct (state, counters) pairs  <- what R1 asserts injectivity over, and this guard
        //
        // These are collected through a VALUE-KEYED walk (see ValueKeyedStore), not through
        // DfsExplorer's default encoder-keyed store, because a sample pruned by the encoder under test
        // is a sample the test cannot honestly measure. Measured 2026-10-01: the encoder-keyed walk
        // reached 206 configurations where this one reaches 212, so the default store was already
        // dropping positions with the encoder working correctly. See 12.01 "Count glossary".
        assertTrue(keyByPosition.size() >= 150,
                "reachable position count dropped from the recorded 160 — the corpus changed, so the "
                        + "recorded measurement and Spec 12.05's assumptions need re-checking");
    }

    /** R2 — the same instance encodes to the same bytes on every call. */
    @Test
    @DisplayName("R2: repeated encoding of one instance is stable")
    void encode_isDeterministicAcrossCalls() {
        SharedState state = PetersonState.of(true, false, 1);

        byte[] first = ENCODER.encode(state);
        byte[] second = ENCODER.encode(state);
        byte[] third = ENCODER.encode(state);

        assertArrayEquals(first, second, "two calls on the same instance must agree");
        assertArrayEquals(second, third, "three calls on the same instance must agree");
    }

    /** R3 — no per-instance state affects output, so a second encoder agrees with the first. */
    @Test
    @DisplayName("R3: two encoder instances produce equal bytes")
    void encode_isDeterministicAcrossInstances() {
        SharedState state = PetersonState.of(false, true, 2);

        assertArrayEquals(new CanonicalEncoder().encode(state), ENCODER.encode(state),
                "the encoder must hold no per-instance state");
    }

    /**
     * R5 — the flush verdict, pinned as a test rather than left to silence.
     *
     * <p>The alternative verdict is a recorded PIT suppression, and which one applies was settled
     * empirically rather than assumed: with {@code out.flush()} deleted the entire suite still passes,
     * because {@link java.io.ByteArrayOutputStream} ignores {@code flush()} and {@code toByteArray()}
     * returns the whole buffer regardless. The mutant is therefore <em>equivalent</em>, and this test
     * exists to catch the day that stops being true — if {@code baos} ever became a stream whose
     * {@code flush()} mattered, this assertion would be the thing that notices.
     *
     * <p>The expected length is derived from the field list, not from a count:
     * {@code PetersonState} encodes {@code flag[0]}, {@code flag[1]} (1 byte each) and {@code turn},
     * {@code inCriticalSection} (4 bytes each) = 10 bytes.
     */
    @Test
    @DisplayName("R5: the encoding is complete without relying on the flush")
    void encode_flushRemovalIsUnobservable() {
        SharedState state = PetersonState.of(false, false, 0);

        byte[] encoded = ENCODER.encode(state);

        // 4 (flag count) + 2 (two flags) + 4 (turn) + 4 (inCriticalSection) = 14. Spec 13.06 added
        // the count prefix so a longer flag array is separated by an explicit extent rather than by an
        // incidental difference in byte count; it was 10 before that.
        assertEquals(14, encoded.length,
                "a PetersonState encoding must contain all four fields; a short read means bytes were "
                        + "lost, which is the failure mode a missing flush would cause on a buffering "
                        + "stream");
    }

    /**
     * R1's human-readable complement, kept because the whole-corpus injectivity check reports its
     * failure as two opaque hex keys with no indication of which field collided. Here it is one field,
     * one difference, one assertion. This is the shape every future R1 failure report should take.
     */
    @Test
    @DisplayName("R1 complement: a single-field difference is visible in the store key")
    void storeKey_distinguishesSingleFieldDifference() {
        Configuration a = Configuration.initial(PetersonState.of(false, false, 0), List.of());
        Configuration b = Configuration.initial(PetersonState.of(true, false, 0), List.of());

        assertFalse(storeKey(a).equals(storeKey(b)),
                "a difference in one flag must be visible in the store key");
    }
}