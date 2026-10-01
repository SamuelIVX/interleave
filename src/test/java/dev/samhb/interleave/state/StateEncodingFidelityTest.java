package dev.samhb.interleave.state;

import dev.samhb.interleave.core.CounterState;
import dev.samhb.interleave.core.DclState;
import dev.samhb.interleave.core.DeadlockState;
import dev.samhb.interleave.core.PairState;
import dev.samhb.interleave.core.PetersonState;
import dev.samhb.interleave.core.SharedState;
import dev.samhb.interleave.format.dsl.DynamicState;
import dev.samhb.interleave.format.dsl.FieldDecl;
import dev.samhb.interleave.format.dsl.LocalDecl;
import dev.samhb.interleave.format.dsl.StateDecl;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.IOException;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Encoding-fidelity tests for every {@link SharedState} implementation — Spec 12.07.
 *
 * <p>{@code encodeTo} is the leaf of both stores' visited-key, and a lossy leaf fails silently: two
 * distinct states collide, the store answers "already visited" for a configuration it has never seen,
 * and a reachable violation is pruned from the search. Nothing crashes and nothing reports an error.
 * That is why {@link DeadlockState} could omit {@code control} from its encoding while {@code equals},
 * {@code hashCode} and {@code deepCopy} all treated it as identity.
 *
 * <p>These tests pin the direction {@code SharedState}'s Javadoc originally did not state: distinct
 * states must encode distinctly.
 *
 * <p><b>What these tests do and do not establish.</b> They establish that every identity field is
 * represented in the encoding and that its encoding varies across a sampled domain, plus positional
 * and shape sensitivity for arrays. They do <em>not</em> prove injectivity over the whole {@code int}
 * domain, which no finite sample can. The injectivity requirement is a design invariant; these tests
 * are its sampling.
 */
class StateEncodingFidelityTest {

    /** Values used to sample an {@code int} field, chosen to catch the realistic lossy encodings. */
    private static final int[] INT_SPREAD = {0, 1, -1, 2, 255, 256, -255, 65535, 65536};

    /**
     * A base value chosen to be outside {@link #INT_SPREAD}.
     *
     * <p>If the base held a value the spread also contains, the probe for that value would mutate
     * nothing and the case would pass vacuously — asserting that a state encodes differently from
     * itself. {@link #assertEncodingDiffers} now rejects that, but the base still avoids it by
     * construction so every probe in the spread is a real mutation.
     */
    private static final int BASE_INT = 12_345;

    /**
     * {@link #INT_SPREAD} minus {@code -1}, for fields whose base value is already {@code -1}.
     *
     * <p>{@code DclState.lockOwner} is {@code -1} when unlocked, so probing it with {@code -1} mutates
     * nothing and the case passes vacuously. {@code assertEncodingDiffers} rejects that outright
     * ("probe is vacuous"), which is how this constant came to exist rather than a skipped assertion.
     *
     * <p>{@code -1} is not thereby excluded from coverage: it participates in the pairwise comparison
     * as the base state itself, so it is still required to encode distinctly from every owner id here.
     */
    private static final int[] LOCK_OWNER_SPREAD = {0, 1, 2, -255, 255, 256, 65535, 65536};

    /**
     * R3/R3a — fields recorded as tracked gaps: known to be absent from the encoding, escalated to
     * specs 09/10. {@code trackedGaps_areStillRealGaps} proves each entry is still genuinely a gap, so
     * the record cannot outlive its reason.
     */
    private static final Map<String, List<String>> TRACKED_GAPS = Map.of(
            "dev.samhb.interleave.format.dsl.DynamicState", List.of("decl", "threadCount"));

    private static byte[] encode(SharedState state) {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (DataOutputStream out = new DataOutputStream(bytes)) {
            state.encodeTo(out);
        } catch (IOException e) {
            throw new AssertionError("encoding failed", e);
        }
        return bytes.toByteArray();
    }

    /**
     * Asserts two genuinely distinct states encode differently.
     *
     * <p>The {@code assertNotEquals} precondition is load-bearing. Without it a probe whose mutation
     * was a no-op — the spread containing the base value, a setter that did not write — would pass
     * vacuously, asserting that a state encodes differently from itself. That failure mode is
     * invisible in a passing suite and is exactly the kind of silent gap this spec exists to close, so
     * it is made loud here instead.
     */
    private static void assertEncodingDiffers(String what, SharedState a, SharedState b) {
        if (a.equals(b)) {
            throw new AssertionError(what + ": probe is vacuous — the two states are equal, so this "
                    + "case would pass without testing anything");
        }
        byte[] ea = encode(a);
        byte[] eb = encode(b);
        if (Arrays.equals(ea, eb)) {
            throw new AssertionError(
                    what + ": expected different encodings but both were " + Arrays.toString(ea)
                            + " (" + ea.length + " bytes)");
        }
    }

    /**
     * Asserts every variant encodes differently from <em>every other</em> variant, not merely from a
     * baseline.
     *
     * <p>This is strictly stronger than the baseline-relative probes, and the difference is not
     * academic. Measured 2026-10-01 against a deliberately lossy encoder that writes only an
     * {@code int}'s low byte: the baseline-relative style produced <b>0 failing assertions</b> while
     * this pairwise style produced <b>7</b> collisions. A field encoded as a single byte against a
     * baseline of 12_345 is invisible to baseline-relative probing, because every sampled value looks
     * different <em>from the base</em> while several of them are identical <em>to each other</em> —
     * exactly the shape {@code INT_SPREAD}'s 0/255/256/-255/65535/65536 entries are chosen to expose,
     * and the shape {@code equals} treats as distinct states. Since the stores key on these encodings,
     * a pairwise collision is a real pruning bug, so pairwise is the property worth pinning.
     *
     * <p>Variants are keyed by index so the failure message names the colliding pair rather than
     * dumping two opaque byte arrays.
     */
    private static void assertAllEncodingsDistinct(String what, List<SharedState> variants) {
        for (int i = 0; i < variants.size(); i++) {
            for (int j = i + 1; j < variants.size(); j++) {
                SharedState a = variants.get(i);
                SharedState b = variants.get(j);
                if (a.equals(b)) {
                    throw new AssertionError(what + ": probe is vacuous — variants " + i + " and " + j
                            + " are equal states, so this case would pass without testing anything");
                }
                byte[] ea = encode(a);
                byte[] eb = encode(b);
                if (Arrays.equals(ea, eb)) {
                    throw new AssertionError(what + ": variants " + i + " and " + j
                            + " are distinct states but share the encoding " + Arrays.toString(ea)
                            + " — the store will treat the second as already visited");
                }
            }
        }
    }

    /** R2 — the targeted regression. This is the test that guards the {@code control} fix. */
    @Test
    @DisplayName("R6: DeadlockState differing only in control encodes differently")
    void encodeDeadlockState_controlFlipped_changesEncoding() {
        DeadlockState off = DeadlockState.of(false, false);
        DeadlockState on = DeadlockState.of(false, false);
        on.setControl(true);

        assertFalse(off.equals(on), "precondition: equals must distinguish control");
        assertEncodingDiffers("control flipped", off, on);
    }

    @Test
    @DisplayName("R2: DeadlockState — flag and control each change the encoding")
    void encodeDeadlockState_eachField_changesEncoding() {
        DeadlockState base = DeadlockState.of(false, false);

        DeadlockState flag0 = copy(base);
        flag0.setFlag(0, true);
        assertEncodingDiffers("flag[0]", base, flag0);

        DeadlockState flag1 = copy(base);
        flag1.setFlag(1, true);
        assertEncodingDiffers("flag[1]", base, flag1);

        DeadlockState ctrl = copy(base);
        ctrl.setControl(true);
        assertEncodingDiffers("control", base, ctrl);

        // All four flag combinations, compared with one another. An encoder that wrote only the count
        // of set flags would pass every baseline-relative probe above and still collide here, since
        // {0,1} and {1,0} are distinct states that a count cannot tell apart.
        List<DeadlockState> flagCombos = new ArrayList<>();
        for (int mask = 0; mask < 4; mask++) {
            DeadlockState combo = DeadlockState.of((mask & 1) != 0, (mask & 2) != 0);
            flagCombos.add(combo);
            for (int existing = 0; existing < flagCombos.size() - 1; existing++) {
                assertEncodingDiffers("flag combination " + mask + " vs " + existing,
                        flagCombos.get(existing), combo);
            }
        }
        assertAllEncodingsDistinct("DeadlockState flag combinations", new ArrayList<>(flagCombos));
    }

    @Test
    @DisplayName("R2a: PetersonState — flag position matters, turn varies across the sampled domain")
    void encodePetersonState_eachField_changesEncoding() {
        PetersonState base = PetersonState.of(false, false, BASE_INT);

        PetersonState flag0 = copy(base);
        flag0.setFlag(0, true);
        assertEncodingDiffers("flag[0]", base, flag0);

        // Position matters: swapping the flags must not encode identically.
        PetersonState flag1 = copy(base);
        flag1.setFlag(1, true);
        assertEncodingDiffers("flag[1]", base, flag1);
        assertEncodingDiffers("flag permutation", flag0, flag1);

        for (int value : INT_SPREAD) {
            PetersonState turn = copy(base);
            turn.setTurn(value);
            assertEncodingDiffers("turn=" + value, base, turn);
        }

        // Pairwise across the spread: catches an encoding that collapses two sampled values which are
        // each distinct from the base but identical to one another.
        List<SharedState> turns = new ArrayList<>();
        for (int value : INT_SPREAD) {
            PetersonState turn = copy(base);
            turn.setTurn(value);
            turns.add(turn);
        }
        assertAllEncodingsDistinct("PetersonState.turn across INT_SPREAD", turns);

        // inCriticalSection across the whole sampled domain, not just 1: an encoding that wrote it as a
        // presence boolean would satisfy the single probe and still collide across thread indices.
        List<SharedState> criticalSections = new ArrayList<>();
        for (int thread : INT_SPREAD) {
            PetersonState ics = copy(base);
            ics.setInCriticalSection(thread);
            criticalSections.add(ics);
        }
        assertAllEncodingsDistinct("PetersonState.inCriticalSection across INT_SPREAD", criticalSections);
    }

    @Test
    @DisplayName("R2a: CounterState — counter sampled, registers positional and length sensitive")
    void encodeCounterState_eachField_changesEncoding() {
        CounterState base = CounterState.of(BASE_INT, 2);
        base.setRegister(0, BASE_INT);

        for (int value : INT_SPREAD) {
            CounterState counter = copy(base);
            counter.setCounter(value);
            assertEncodingDiffers("counter=" + value, base, counter);
        }

        List<SharedState> counters = new ArrayList<>();
        for (int value : INT_SPREAD) {
            CounterState counter = copy(base);
            counter.setCounter(value);
            counters.add(counter);
        }
        assertAllEncodingsDistinct("CounterState.counter across INT_SPREAD", counters);

        CounterState control = copy(base);
        control.setControl(true);
        assertEncodingDiffers("control", base, control);

        for (int i = 0; i < INT_SPREAD.length; i++) {
            CounterState reg = copy(base);
            reg.setRegister(0, INT_SPREAD[i]);
            assertEncodingDiffers("registers[0]=" + INT_SPREAD[i], base, reg);
        }

        List<SharedState> registers = new ArrayList<>();
        for (int value : INT_SPREAD) {
            CounterState reg = copy(base);
            reg.setRegister(0, value);
            registers.add(reg);
        }
        assertAllEncodingsDistinct("CounterState.registers[0] across INT_SPREAD", registers);

        // Position matters within the register array.
        CounterState swapped = CounterState.of(0, 2);
        swapped.setRegister(0, 11);
        swapped.setRegister(1, 22);
        CounterState permuted = CounterState.of(0, 2);
        permuted.setRegister(0, 22);
        permuted.setRegister(1, 11);
        assertEncodingDiffers("registers permutation", swapped, permuted);

        // Shape matters: a longer register array must not encode like a shorter one.
        CounterState twoThreads = CounterState.of(0, 2);
        CounterState threeThreads = CounterState.of(0, 3);
        assertEncodingDiffers("registers length", twoThreads, threeThreads);
    }

    @Test
    @DisplayName("R2a: PairState — all six identity fields reach the encoding")
    void encodePairState_eachField_changesEncoding() {
        PairState base = PairState.of(BASE_INT, BASE_INT);

        for (int value : INT_SPREAD) {
            PairState high = copy(base);
            high.setHigh(value);
            assertEncodingDiffers("high=" + value, base, high);

            PairState low = copy(base);
            low.setLow(value);
            assertEncodingDiffers("low=" + value, base, low);
        }

        List<SharedState> highs = new ArrayList<>();
        List<SharedState> lows = new ArrayList<>();
        for (int value : INT_SPREAD) {
            PairState high = copy(base);
            high.setHigh(value);
            highs.add(high);

            PairState low = copy(base);
            low.setLow(value);
            lows.add(low);
        }
        assertAllEncodingsDistinct("PairState.high across INT_SPREAD", highs);
        assertAllEncodingsDistinct("PairState.low across INT_SPREAD", lows);

        PairState control = copy(base);
        control.setControl(true);
        assertEncodingDiffers("control", base, control);

        // observedHigh, observedLow and hasObservation have no public setter, so they are reached by
        // reflection over non-final instance fields. They are still identity fields per equals.
        for (int value : new int[]{1, 256}) {
            assertEncodingDiffers("observedHigh=" + value,
                    base, withField(base, "observedHigh", value));
            assertEncodingDiffers("observedLow=" + value,
                    base, withField(base, "observedLow", value));
        }

        // Full integer spread on the observed fields too — a projection that clamps them to presence
        // would satisfy the two-value probe above while colliding across the wider domain.
        List<SharedState> observedHighs = new ArrayList<>();
        List<SharedState> observedLows = new ArrayList<>();
        for (int value : INT_SPREAD) {
            observedHighs.add(withField(base, "observedHigh", value));
            observedLows.add(withField(base, "observedLow", value));
        }
        assertAllEncodingsDistinct("PairState.observedHigh across INT_SPREAD", observedHighs);
        assertAllEncodingsDistinct("PairState.observedLow across INT_SPREAD", observedLows);

        assertEncodingDiffers("hasObservation", base, withField(base, "hasObservation", true));
    }

    @Test
    @DisplayName("R2a: DclState — all six identity fields reach the encoding")
    void encodeDclState_eachField_changesEncoding() {
        DclState base = DclState.of(false);

        DclState initialized = copy(base);
        initialized.setInitialized(true);
        assertEncodingDiffers("initialized", base, initialized);

        // instance and observedInstance are Object-typed and encoded as presence booleans, which is a
        // faithful projection: equals compares presence only, so any two distinct instances suffice.
        DclState instance = copy(base);
        instance.setInstance("instance");
        assertEncodingDiffers("instance present", base, instance);

        DclState observed = copy(base);
        observed.setObservedInstance("instance");
        assertEncodingDiffers("observedInstance present", base, observed);

        DclState control = copy(base);
        control.setControl(true);
        assertEncodingDiffers("control", base, control);

        // locked and lockOwner have no public setter; reached by reflection over non-final fields.
        assertEncodingDiffers("locked", base, withField(base, "locked", true));

        List<SharedState> owners = new ArrayList<>();
        // The base (lockOwner == -1, unlocked) joins the pairwise set as a genuine variant. Excluded
        // from LOCK_OWNER_SPREAD only because probing it there would be a no-op mutation, not because
        // -1 is unimportant — "unlocked" must encode distinctly from every owner id, and the pairwise
        // form is what actually requires that.
        owners.add(base);
        for (int owner : LOCK_OWNER_SPREAD) {
            DclState state = withField(base, "lockOwner", owner);
            assertEncodingDiffers("lockOwner=" + owner, base, state);
            owners.add(state);
        }
        assertAllEncodingsDistinct("DclState.lockOwner across LOCK_OWNER_SPREAD", owners);
    }

    @Test
    @DisplayName("R2a: DynamicState — the fields it does encode vary with their values")
    void encodeDynamicState_eachEncodedField_changesEncoding() {
        StateDecl decl = new StateDecl(
                List.of(FieldDecl.ofInt("count", BASE_INT), FieldDecl.ofBool("flag", false),
                        FieldDecl.ofArray("samples", new int[]{BASE_INT, BASE_INT, BASE_INT})),
                List.of());
        DynamicState base = new DynamicState(decl, 1);

        for (int value : INT_SPREAD) {
            DynamicState count = new DynamicState(decl, 1);
            count.setInt("count", value);
            assertEncodingDiffers("fieldValues[count]=" + value, base, count);
        }

        List<SharedState> counts = new ArrayList<>();
        for (int value : INT_SPREAD) {
            DynamicState count = new DynamicState(decl, 1);
            count.setInt("count", value);
            counts.add(count);
        }
        assertAllEncodingsDistinct("DynamicState.fieldValues[count] across INT_SPREAD", counts);

        DynamicState flag = new DynamicState(decl, 1);
        flag.setBool("flag", true);
        assertEncodingDiffers("fieldValues[flag]", base, flag);

        // The INT_ARRAY branch of encodeTo writes a length followed by each element. Three properties
        // have to hold independently, and a commutative summary satisfies none of them while still
        // encoding something: the element value must be visible, the element's position must be
        // visible, and the array's length must be visible. Each is probed separately below.
        DynamicState elemChanged = new DynamicState(decl, 1);
        elemChanged.setArrayElement("samples", 1, 7);
        assertEncodingDiffers("fieldValues[samples][1] value", base, elemChanged);

        // Position matters: swapping two elements must not encode identically, and the spread is chosen
        // so a sum-based encoding (7+1 == 1+7) would pass a value-only probe.
        DynamicState swapped = new DynamicState(decl, 1);
        swapped.setArrayElement("samples", 0, 7);
        swapped.setArrayElement("samples", 1, BASE_INT);
        assertEncodingDiffers("fieldValues[samples] permutation", elemChanged, swapped);

        // Length matters: a shorter array must not encode like a longer one.
        //
        // Array length is fixed by the declaration and setArrayElement only mutates elements in place,
        // so the only way to vary it is a different StateDecl. That decl differs ONLY in the array
        // length — every other field is held identical, so the length is the single variable and the
        // probe cannot pass for an unrelated reason. Both directions are checked so the property is
        // symmetric rather than an artifact of which side holds the larger array.
        //
        // Worth recording what this does and does not catch. Deleting the `out.writeInt(arr.length)`
        // call from encodeTo leaves this probe GREEN, and that is correct rather than a gap: every
        // element is a fixed-width 4-byte int, so arrays of different length already produce byte
        // sequences of different length and cannot collide. The length prefix is redundant *for
        // injectivity*. The probe is retained because it pins the observable property (distinct lengths
        // must not share an encoding) rather than one particular way of achieving it.
        //
        // What this probe does catch is a *summary* encoding: replacing the elements with their sum
        // collapses {12345,7,12345} and {7,12345,12345} to the same bytes, and the pairwise assertion
        // below fails as it should.
        StateDecl shorterDecl = new StateDecl(
                List.of(FieldDecl.ofInt("count", BASE_INT), FieldDecl.ofBool("flag", false),
                        FieldDecl.ofArray("samples", new int[]{BASE_INT})),
                List.of());
        DynamicState shorter = new DynamicState(shorterDecl, 1);
        assertFalse(base.equals(shorter), "precondition: the two declarations describe different "
                + "states, so the probe below is not comparing a state with itself");
        assertEncodingDiffers("fieldValues[samples] length", base, shorter);

        StateDecl longerDecl = new StateDecl(
                List.of(FieldDecl.ofInt("count", BASE_INT), FieldDecl.ofBool("flag", false),
                        FieldDecl.ofArray("samples", new int[]{BASE_INT, BASE_INT, BASE_INT, BASE_INT})),
                List.of());
        assertEncodingDiffers("fieldValues[samples] length (longer)",
                base, new DynamicState(longerDecl, 1));

        // Pairwise across every independently varied array, plus both length variants, so no two of
        // them may share an encoding. This is what catches the commutative-summary encoding.
        assertAllEncodingsDistinct("DynamicState.fieldValues[samples]",
                List.of(base, elemChanged, swapped, shorter, new DynamicState(longerDecl, 1)));

        // localValues: a declaration carrying one local per thread, varied one thread at a time.
        StateDecl withLocal = new StateDecl(
                List.of(FieldDecl.ofInt("count", BASE_INT)),
                List.of(LocalDecl.ofInt("t", 0)));
        DynamicState localsBase = new DynamicState(withLocal, 1);
        DynamicState localsChanged = new DynamicState(withLocal, 1);
        localsChanged.setLocalInt(0, "t", 7);
        assertEncodingDiffers("localValues[t]", localsBase, localsChanged);

        // Full integer spread on the local, not just the single 7 above.
        List<SharedState> locals = new ArrayList<>();
        for (int value : INT_SPREAD) {
            DynamicState state = new DynamicState(withLocal, 1);
            state.setLocalInt(0, "t", value);
            locals.add(state);
        }
        assertAllEncodingsDistinct("DynamicState.localValues[t] across INT_SPREAD", locals);

        // Position matters across threads, which the single-thread probes above cannot reach: a
        // two-thread state whose locals are [1, 2] is a different state from [2, 1], and an encoding
        // that is blind to which thread holds which value collides them.
        //
        // Demonstrated 2026-10-01: encoding each column in sorted order across threads — preserving
        // every individual value and every per-thread probe above, but losing which thread held it —
        // left the whole suite GREEN, because every prior local probe was single-threaded. The
        // commutative-sum mutation was caught, but only incidentally by the thread-count probe, which
        // compares one thread against two and so conflates shape with value.
        StateDecl twoThreadLocal = new StateDecl(
                List.of(FieldDecl.ofInt("count", BASE_INT)),
                List.of(LocalDecl.ofInt("t", 0)));
        DynamicState threadOrderA = new DynamicState(twoThreadLocal, 2);
        threadOrderA.setLocalInt(0, "t", 1);
        threadOrderA.setLocalInt(1, "t", 2);

        DynamicState threadOrderB = new DynamicState(twoThreadLocal, 2);
        threadOrderB.setLocalInt(0, "t", 2);
        threadOrderB.setLocalInt(1, "t", 1);

        assertEncodingDiffers("localValues thread order [1,2] vs [2,1]", threadOrderA, threadOrderB);

        // Same thread count, so this cannot be satisfied by writing the count of threads or of locals.
        List<SharedState> perThread = List.of(threadOrderA, threadOrderB);
        assertAllEncodingsDistinct("DynamicState.localValues thread order", perThread);

        // And the permuted pair must also differ from a state where both threads hold the same value.
        DynamicState threadSame = new DynamicState(twoThreadLocal, 2);
        threadSame.setLocalInt(0, "t", 1);
        threadSame.setLocalInt(1, "t", 1);
        assertEncodingDiffers("localValues [1,2] vs [1,1]", threadOrderA, threadSame);
        assertAllEncodingsDistinct("DynamicState.localValues thread order and repeats",
                List.of(threadOrderA, threadOrderB, threadSame));

        // Shape matters at the thread level: one thread versus two writes a different number of locals.
        assertEncodingDiffers("localValues thread count",
                new DynamicState(withLocal, 1), new DynamicState(withLocal, 2));
    }

    /**
     * R3a — each tracked gap is still a genuine gap.
     *
     * <p>This is deliberately a separate test from {@link #everyStateField_hasAnEncodingCase}, which
     * never calls {@code encodeTo}. Here the omissions are asserted <em>directly</em>: two states
     * differing only in the allowlisted field must encode identically. The day {@code encodeTo} starts
     * writing that field, these encodings diverge and this test fails — so the allowlist cannot outlive
     * the reason it exists.
     */
    @Test
    @DisplayName("R3a: each tracked gap is still an encoding gap")
    void trackedGaps_areStillRealGaps() {
        // decl: two declarations identical in structure but differently named. Names are never
        // written, so the encodings must be identical — a fact that stops holding once decl is encoded.
        StateDecl namedX = new StateDecl(List.of(FieldDecl.ofInt("x", 1)), List.of());
        StateDecl namedY = new StateDecl(List.of(FieldDecl.ofInt("y", 1)), List.of());
        assertArrayEquals(encode(new DynamicState(namedX, 1)),
                encode(new DynamicState(namedY, 1)),
                "DynamicState.decl must still be absent from the encoding");

        // threadCount: with no locals declared, the local loop emits nothing, so threadCount leaves no
        // trace at all. Two states differing only in threadCount must therefore encode identically.
        StateDecl noLocals = new StateDecl(List.of(FieldDecl.ofInt("x", 1)), List.of());
        assertArrayEquals(encode(new DynamicState(noLocals, 1)),
                encode(new DynamicState(noLocals, 2)),
                "DynamicState.threadCount must still be absent from the encoding");
    }

    /**
     * R3 — every instance field is accounted for.
     *
     * <p>Pure accounting: this test never calls {@code encodeTo}. It asks only whether each declared
     * field has a parity case or a recorded tracked gap, so the failure mode it detects — a newly
     * declared field with no coverage — stays distinct from R2's, which asks whether the fields are
     * encoded correctly.
     */
    @Test
    @DisplayName("R3: every non-static state field has a case or a tracked gap")
    void everyStateField_hasAnEncodingCase() {
        Map<String, List<String>> covered = coveredFieldsByClass();
        List<String> problems = new ArrayList<>();

        for (Class<?> type : stateClasses()) {
            List<String> declared = instanceFieldNames(type);
            List<String> gaps = TRACKED_GAPS.getOrDefault(type.getName(), List.of());
            List<String> fields = covered.getOrDefault(type.getName(), List.of());

            for (String name : declared) {
                if (!fields.contains(name) && !gaps.contains(name)) {
                    problems.add(type.getSimpleName() + "." + name
                            + " has neither an encoding case nor a tracked-gap entry");
                }
            }
            for (String gap : gaps) {
                if (!declared.contains(gap)) {
                    problems.add("tracked gap " + type.getSimpleName() + "." + gap
                            + " names a field that no longer exists");
                }
            }
        }

        assertTrue(problems.isEmpty(), () -> String.join("\n", problems));
    }

    /**
     * R3 — static fields are filtered out.
     *
     * <p>No state class declares a static field today, so the filter cannot be exercised against the
     * real six. It is tested against a stand-in instead: without the filter, the first
     * {@code private static final} constant added to a state class would fail R3 demanding an encoding
     * case for it, and the natural response to that failure would be deleting the check. That trade
     * costs the recurrence guard to accommodate an unrelated constant, so the filter is pinned here.
     */
    @Test
    @DisplayName("R3: static fields are excluded from the completeness sweep")
    void staticFields_areExcludedFromTheSweep() {
        List<String> names = instanceFieldNames(WithAStaticConstant.class);
        assertEquals(List.of("instanceOnly"), names,
                "a static constant must not be swept, or the check becomes defeatable");
    }

    /** R2, converse direction — equal states must encode identically, or the fix over-separates. */
    @Test
    @DisplayName("R2: equal states encode identically for all five in-scope classes")
    void equalStates_encodeIdentically() {
        DeadlockState d1 = DeadlockState.of(true, false);
        d1.setControl(true);
        DeadlockState d2 = DeadlockState.of(true, false);
        d2.setControl(true);
        assertArrayEquals(encode(d1), encode(d2), "equal DeadlockStates must encode identically");

        assertArrayEquals(encode(PetersonState.of(true, false, 1)),
                encode(PetersonState.of(true, false, 1)), "equal PetersonStates");
        assertArrayEquals(encode(CounterState.of(5, 2)),
                encode(CounterState.of(5, 2)), "equal CounterStates");
        assertArrayEquals(encode(PairState.of(1, 2)),
                encode(PairState.of(1, 2)), "equal PairStates");
        assertArrayEquals(encode(DclState.of(true)),
                encode(DclState.of(true)), "equal DclStates");
    }

    /** Stand-in with one instance field and one static constant, used only to test the filter. */
    private static final class WithAStaticConstant {
        private static final int NOT_STATE = 7;
        private int instanceOnly;
    }

    /** The non-static declared fields of a class — the exact set R3 requires to be accounted for. */
    private static List<String> instanceFieldNames(Class<?> type) {
        List<String> names = new ArrayList<>();
        for (Field field : type.getDeclaredFields()) {
            if (java.lang.reflect.Modifier.isStatic(field.getModifiers())) {
                continue;
            }
            names.add(field.getName());
        }
        return names;
    }

/**
     * A typed deep copy.
     *
     * <p>{@code deepCopy} is declared to return {@link SharedState} and the implementations do not
     * narrow it, so the concrete type has to be recovered. The cast is sound because {@code deepCopy}
     * constructs the same runtime type it was called on.
     */
    @SuppressWarnings("unchecked")
    private static <T extends SharedState> T copy(T state) {
        return (T) state.deepCopy();
    }

    /** Sets a non-final instance field on a deep copy, for the fields with no public setter. */
    private static <T extends SharedState> T withField(T base, String fieldName, Object value) {
        T copy = copy(base);
        try {
            Field field = copy.getClass().getDeclaredField(fieldName);
            field.setAccessible(true);
            field.set(copy, value);
        } catch (ReflectiveOperationException e) {
            throw new AssertionError("could not set " + fieldName, e);
        }
        return copy;
    }

    /**
     * Every concrete {@link SharedState} implementation on the classpath, discovered rather than
     * listed.
     *
     * <p>This was a hardcoded list of six until the CodeRabbit pass on 2026-10-01, and a hardcoded
     * list is a recurrence hole: a seventh state class added later would simply not be checked, and
     * R3 would stay green while the new class's encoding went unpinned. That is precisely the failure
     * mode Spec 12.07 exists to prevent, reintroduced through the test that is supposed to catch it.
     *
     * <p>Discovery scans the compiled main-classes directory for classes implementing {@code SharedState}.
     * A jar would need a different walk, but this project has no packaged artifact — everything runs
     * from {@code build/classes/java/main} — so the directory scan is exact rather than approximate, and
     * it fails loudly if that directory is missing instead of quietly returning an empty list.
     */
    private static List<Class<?>> stateClasses() {
        Path root = Path.of("build", "classes", "java", "main");
        assertTrue(Files.isDirectory(root),
                "expected compiled main classes at " + root.toAbsolutePath()
                        + " — R3 cannot enumerate state classes without them, and returning an empty "
                        + "list here would make R3 pass vacuously");

        try (Stream<Path> files = Files.walk(root)) {
            return files
                    .filter(p -> p.toString().endsWith(".class"))
                    .map(p -> classNameFor(root, p))
                    .flatMap(StateEncodingFidelityTest::tryLoad)
                    .filter(c -> !c.isInterface() && !Modifier.isAbstract(c.getModifiers()))
                    .filter(c -> SharedState.class.isAssignableFrom(c))
                    .sorted((a, b) -> a.getName().compareTo(b.getName()))
                    .toList();
        } catch (IOException e) {
            throw new AssertionError("could not scan " + root.toAbsolutePath() + " for state classes", e);
        }
    }

    /** {@code build/classes/java/main/dev/samhb/…/Foo.class} to {@code dev.samhb.….Foo}. */
    private static String classNameFor(Path root, Path classFile) {
        String relative = root.relativize(classFile).toString();
        return relative.substring(0, relative.length() - ".class".length())
                .replace(File.separatorChar, '.')
                .replace('/', '.');
    }

    /** Classes that fail to link are skipped; they cannot be usable state implementations. */
    private static Stream<Class<?>> tryLoad(String className) {
        try {
            return Stream.of(Class.forName(className, false,
                    StateEncodingFidelityTest.class.getClassLoader()));
        } catch (ClassNotFoundException | LinkageError e) {
            return Stream.empty();
        }
    }

    /**
     * The R2 cases above, recorded as machine-readable field coverage for R3 to check.
     *
     * <p><b>This map is hand-maintained and duplicates the R2 parity cases by hand.</b> R3's
     * field-coverage check only verifies that every declared field is mentioned here or in
     * {@link #TRACKED_GAPS} — it cannot detect that an entry here has stopped corresponding to a real
     * parity case, because the two lists share no structure. A field listed here with its parity case
     * deleted would leave the field-coverage test green while the actual encoding went unpinned.
     *
     * <p>So any change to the R2 parity cases must change this map in the same commit. If the two ever
     * drift, the encoding gap 12.07 exists to prevent can re-enter through a deleted test rather than
     * through a bug. That coupling is the price of R3 being a static check rather than a reflective one;
     * the reflective alternative (reading the parity cases' field names out of the test bodies) is not
     * possible in JUnit 5.
     */
    private static Map<String, List<String>> coveredFieldsByClass() {
        Map<String, List<String>> covered = new TreeMap<>();
        covered.put(DeadlockState.class.getName(), List.of("flag", "control"));
        covered.put(PetersonState.class.getName(), List.of("flag", "turn", "inCriticalSection"));
        covered.put(CounterState.class.getName(), List.of("counter", "control", "registers"));
        covered.put(PairState.class.getName(),
                List.of("high", "low", "control", "observedHigh", "observedLow", "hasObservation"));
        covered.put(DclState.class.getName(),
                List.of("initialized", "instance", "locked", "lockOwner", "control", "observedInstance"));
        covered.put(DynamicState.class.getName(), List.of("fieldValues", "localValues"));
        return covered;
    }
}