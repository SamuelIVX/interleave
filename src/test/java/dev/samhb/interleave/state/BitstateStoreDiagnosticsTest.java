package dev.samhb.interleave.state;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.samhb.interleave.core.Configuration;
import dev.samhb.interleave.core.CounterState;
import dev.samhb.interleave.core.SharedState;
import dev.samhb.interleave.core.StepOutcome;
import java.lang.reflect.Constructor;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * Spec 12.03 — the {@link BitstateStore} diagnostics a user reads to decide whether
 * {@code --store bitstate} is a trustworthy trade, and the constructor's rejection boundaries.
 *
 * <p><b>Why these assertions are literal.</b> {@code estimatedFalsePositiveRate()} is
 * user-facing diagnostic output: a wrong value misleads the one decision the number exists to inform.
 * A property assertion cannot constrain it. {@code assertTrue(fpr >= 0.0)} — the shape the existing
 * PR #26 metric test uses — passes for every mutant in the method, as does any band around a
 * "plausible" load factor. All five surviving mutants produce numbers that look entirely reasonable
 * and are wrong by a sign or a factor, so only an expected value computed independently of the
 * implementation can distinguish them.
 *
 * <p><b>How the literals below were produced.</b> Two independent routes to 50 significant digits,
 * which agree to within 1e-45: a hand-summed Taylor series for {@code e^-x} with the final power
 * taken by binary exponentiation, and an arbitrary-precision library call. Neither route invokes
 * {@link BitstateStore#estimatedFalsePositiveRate()}, and the literals are written out rather than
 * recomputed in the test body — {@code Math.pow(1 - Math.exp(...))} written here would reproduce
 * the implementation line for line and validate nothing.
 *
 * <p><b>Why {@code vectorsInUse()} decides which cases are worth having.</b> The formula's {@code m}
 * is {@code size * vectorsInUse()}, and preemption vectors are allocated lazily — the constructor
 * only sizes the array. So a store built with {@code maxPreemptions = 3} that never marks a
 * preemption still reports {@code vectorsInUse() == 1}, and a mutant that flips {@code *} to {@code /}
 * there computes {@code size / 1}, which equals {@code size * 1}. The mutant is provably invisible.
 * Case B below marks at two distinct preemption levels for exactly this reason: it is the only case
 * that makes the multiplier observable.
 */
class BitstateStoreDiagnosticsTest {

    // ---------------------------------------------------------------- R1 — the empty store

    /**
     * R1 — a store with nothing marked reports exactly {@code 0.0}, never {@code NaN}.
     *
     * <p>The {@code n == 0} early return exists so {@code k*n/m} is never evaluated as {@code 0/0}.
     * Removing it produces {@code NaN}, which is worse than a merely wrong number: {@code NaN}
     * compares false against every bound, so it survives any range assertion and silently propagates
     * through formatting into a report the user reads.
     */
    @Test
    void estimatedFalsePositiveRate_emptyStore_isZeroNotNaN() {
        BitstateStore store = new BitstateStore(1000, 3, 2);

        double fpr = store.estimatedFalsePositiveRate();

        assertFalse(Double.isNaN(fpr), "an empty store must not report NaN");
        assertEquals(0.0, fpr, "an empty store reports no false positives, exactly 0.0");
    }

    // ---------------------------------------------------------------- R2/R4 — the closed form

    /**
     * R2/R4 — the reported FPR equals {@code (1 - e^(-kn/m))^k} against literal expected doubles.
     *
     * <p>Three cases, chosen so the formula's inputs land on clean ratios and so the five surviving
     * mutants diverge as far as possible:
     *
     * <table border="1">
     *   <caption>Cases and the mutant each one is load-bearing for</caption>
     *   <tr><th>case</th><th>size</th><th>k</th><th>n</th><th>vectors</th><th>m</th>
     *       <th>k*n/m</th><th>expected</th></tr>
     *   <tr><td>A</td><td>1000</td><td>3</td><td>100</td><td>1</td><td>1000</td><td>0.3</td>
     *       <td>0.017410586496326586</td></tr>
     *   <tr><td>B</td><td>1000</td><td>3</td><td>200</td><td>3</td><td>3000</td><td>0.2</td>
     *       <td>0.0059562427789458935</td></tr>
     *   <tr><td>C</td><td>1000</td><td>4</td><td>250</td><td>1</td><td>1000</td><td>1.0</td>
     *       <td>0.15966130015118526</td></tr>
     * </table>
     *
     * <p><b>Case B is the one that constrains {@code m}.</b> It is built so {@code vectorsInUse() == 3}
     * — one main vector plus two lazily allocated preemption vectors, which requires marking at two
     * distinct preemption levels. With {@code vectorsInUse() == 1} the {@code size * vectorsInUse()}
     * mutant evaluates {@code size / 1}, numerically identical to the original, and cases A and C
     * would pass against it. At three vectors the mutant reports 0.5815579217 against a true value of
     * 0.0059562428 — a factor of ~98, five orders of magnitude outside a 1e-9 tolerance.
     *
     * <p>The tolerance is 1e-9, which is loose only with respect to double rounding: the literals are
     * the correctly-rounded doubles of values that are irrational, so exact equality is not available.
     * Every mutant separation quoted above exceeds 0.006 by a wide margin, so the tolerance still has
     * roughly six orders of magnitude of headroom against the nearest wrong answer.
     */
    @Test
    void estimatedFalsePositiveRate_matchesClosedForm_forKnownInputs() {
        // Case A: plain marks only, so vectorsInUse() == 1 and m == 1000.
        BitstateStore a = new BitstateStore(1000, 3, 2);
        markPlain(a, 100);
        assertEquals(100, a.statesMarked(), "case A precondition: 100 plain marks");
        assertEquals(1000, a.size(), "case A precondition: capacity is the m denominator's factor");
        // k*n/m = 3*100/1000 = 0.3
        assertEquals(0.017410586496326586, a.estimatedFalsePositiveRate(), 1e-9,
            "case A: (1 - e^-0.3)^3 with m = 1000, k = 3, n = 100");

        // Case B: 100 plain plus 100 preemption marks spread over two levels, so vectorsInUse() == 3
        // and m == 3000. This is the case that makes the vectorsInUse() multiplier observable.
        BitstateStore b = new BitstateStore(1000, 3, 2);
        markPlain(b, 100);
        markPreemption(b, 0, 50);
        markPreemption(b, 1, 50);
        assertEquals(100, b.statesMarked(), "case B precondition: 100 plain marks");
        assertEquals(100, b.preemptionStatesMarked(), "case B precondition: 100 preemption marks");
        assertTrue(b.bitCount() > 0, "case B precondition: the store really wrote bits");
        // k*n/m = 3*200/3000 = 0.2
        assertEquals(0.0059562427789458935, b.estimatedFalsePositiveRate(), 1e-9,
            "case B: (1 - e^-0.2)^3 with m = 3000 (three vectors in use), k = 3, n = 200");

        // Case C: k = 4 and k*n/m exactly 1.0, so the expected value is (1 - e^-1)^4.
        BitstateStore c = new BitstateStore(1000, 4, 2);
        markPlain(c, 250);
        assertEquals(250, c.statesMarked(), "case C precondition: 250 plain marks");
        // k*n/m = 4*250/1000 = 1.0
        assertEquals(0.15966130015118526, c.estimatedFalsePositiveRate(), 1e-9,
            "case C: (1 - e^-1)^4 with m = 1000, k = 4, n = 250");
    }

    /**
     * R2 — {@code preemptionStatesMarked} contributes to {@code n}, and a run that marks only
     * preemption vectors still reports a real estimate.
     *
     * <p>Pins the {@code statesMarked + preemptionStatesMarked} sum, which is otherwise masked: in
     * every other case the two terms are added in a proportion that a sign flip on either could
     * survive. This case isolates it — {@code statesMarked == 0}, so {@code n} is exactly
     * {@code preemptionStatesMarked} and any contribution from the main vector is immediately visible.
     *
     * <p>size = 1000, k = 3, n = 50, one preemption vector in use, so m = 1000 and k*n/m = 0.15.
     */
    @Test
    void estimatedFalsePositiveRate_includesPreemptionStatesInCount() {
        BitstateStore store = new BitstateStore(1000, 3, 2);
        markPreemption(store, 0, 50);

        assertEquals(0, store.statesMarked(), "precondition: no plain marks were made");
        assertEquals(50, store.preemptionStatesMarked(), "precondition: 50 preemption marks");

        // (1 - e^-0.15)^3, computed independently of the implementation.
        assertEquals(0.0027025811482068833, store.estimatedFalsePositiveRate(), 1e-9,
            "n must include preemptionStatesMarked; ignoring it reports a materially lower rate");
    }

    /**
     * R3 — the estimate never falls as a user adds states to a fixed store.
     *
     * <p>This is the property that makes the number actionable: a user who marks more states and sees
     * the estimated false-positive rate *drop* has been handed a figure that cannot be trusted. The
     * sequence below is checked with {@code <=} on each step rather than only at the ends, so a
     * non-monotone excursion that happens to return to its starting value is still caught.
     */
    @Test
    void estimatedFalsePositiveRate_isMonotonicInStatesMarked() {
        BitstateStore store = new BitstateStore(1000, 3, 2);
        double previous = store.estimatedFalsePositiveRate();
        assertEquals(0.0, previous, "precondition: the sequence starts from an empty store");

        for (int marked = 1; marked <= 400; marked++) {
            markPlain(store, 1);
            double current = store.estimatedFalsePositiveRate();
            assertTrue(current >= previous,
                "FPR fell after marking state " + marked + ": " + previous + " -> " + current);
            previous = current;
        }
    }

    // ---------------------------------------------------------------- R5/R6 — constructor boundaries

    /**
     * R5 — the {@code size} guard rejects at the boundary and accepts one step inside it.
     *
     * <p>Rejecting {@code -1} proves nothing about the guard's edge: a mutated {@code size < 0} also
     * rejects {@code -1}, so a test covering only negatives passes against the exact off-by-one this
     * guards against. {@code size == 0} is the discriminating input, and {@code size == 1} is the
     * acceptance side that stops an over-broad guard from being written to match.
     */
    @Test
    void constructor_rejectsNonPositiveSize_atBoundary() {
        assertThrows(IllegalArgumentException.class, () -> new BitstateStore(0),
            "size == 0 is exactly the boundary and must be rejected");
        assertThrows(IllegalArgumentException.class, () -> new BitstateStore(-1),
            "a negative size must be rejected");
        assertThrows(IllegalArgumentException.class, () -> new BitstateStore(Integer.MIN_VALUE),
            "a negative size must be rejected regardless of magnitude");
    }

    /** R5 — {@code size == 1} is legal, so the guard rejects nothing inside the positive domain. */
    @Test
    void constructor_acceptsSizeOfOne() {
        BitstateStore store = new BitstateStore(1);

        assertEquals(1, store.size(), "the smallest legal capacity must be accepted verbatim");
        assertEquals(0, store.statesMarked(), "a freshly constructed store has marked nothing");
        assertEquals(0.0, store.bitDensity(), "an untouched store has no bits set");
    }

    /**
     * R6 — the {@code numHashFunctions} guard rejects at the boundary and accepts one step inside.
     *
     * <p>{@code k} is the exponent of the reported FPR, so a store built with {@code k == 0} does not
     * merely misreport: {@code x^0} is 1 for every {@code x}, so the diagnostic would report a
     * certainty of false positives on a store that has none. {@code k == 0} is the boundary input
     * that distinguishes {@code <= 0} from a mutated {@code < 0}.
     */
    @Test
    void constructor_rejectsNonPositiveNumHashFunctions_atBoundary() {
        assertThrows(IllegalArgumentException.class, () -> new BitstateStore(1000, 0),
            "numHashFunctions == 0 is exactly the boundary and must be rejected");
        assertThrows(IllegalArgumentException.class, () -> new BitstateStore(1000, -3),
            "a negative hash-function count must be rejected");
    }

    /** R6 — {@code k == 1} is legal; the guard must not reach into the positive domain. */
    @Test
    void constructor_acceptsOneHashFunction() {
        BitstateStore store = new BitstateStore(1000, 1);

        assertEquals(1, store.numHashFunctions(), "the smallest legal k must be accepted verbatim");
        assertEquals(1000, store.size(), "the other arguments must be unaffected by the guard");

        // A single hash function is still a working filter: one bit set, one query answered.
        Configuration config = configurationWithCounter(1, 0, 0);
        assertFalse(store.isVisited(config), "precondition: nothing is marked yet");
        store.markVisited(config);
        assertTrue(store.isVisited(config), "k == 1 must still mark and find its own bit");
        assertEquals(1, store.bitCount(), "k == 1 sets exactly one bit per mark");
    }

    // ---------------------------------------------------------------- R7 — the maxPreemptions boundary

    /** R7 — a negative preemption bound is rejected, and zero is not caught by an over-broad guard. */
    @Test
    void constructor_rejectsNegativeMaxPreemptions() {
        assertThrows(IllegalArgumentException.class, () -> new BitstateStore(1000, 4, -1),
            "a negative preemption bound must be rejected");
    }

    /**
     * R7 — {@code maxPreemptions == 0} is legal, allocates a single preemption slot, and slot 0 works.
     *
     * <p>This is the boundary most likely to hide an off-by-one, because the guard is {@code < 0}
     * while the allocation is {@code new BitSet[maxPreemptions + 1]}. A bound of zero therefore has to
     * yield a length-1 array: rejecting it would make context-bounded search at K=0 impossible, and
     * allocating it as length 0 would turn the first {@code markVisited(config, tid, 0)} into an
     * {@code ArrayIndexOutOfBoundsException} at the worst possible moment.
     *
     * <p>Constructing the store is not sufficient evidence. {@code preemptionBitsets} is allocated
     * lazily, so a length-1 array full of nulls is indistinguishable, by construction alone, from a
     * correct one. The slot is therefore exercised through the bounded APIs: level 0 is marked and
     * queried, which is the only way to show the slot is both reachable and correct.
     *
     * <p>Assertions are on observable behaviour — {@code bitCount()} and the FPR — and not on the
     * private array, so the test cannot pass while the store is broken in a way a user would feel.
     */
    @Test
    void constructor_zeroMaxPreemptions_allocatesAndExercisesSlotZero() {
        BitstateStore store = new BitstateStore(1000, 4, 0);

        assertEquals(0, store.maxPreemptions(), "a zero bound is retained verbatim");
        assertEquals(0, store.preemptionStatesMarked(), "nothing is marked before the first mark");
        assertEquals(0, store.bitCount(), "a lazily allocated slot holds no bits until marked");
        assertEquals(0.0, store.estimatedFalsePositiveRate(),
            "an untouched zero-bound store reports no false positives");

        // Slot 0 must be reachable and correct, not merely sized.
        Configuration config = configurationWithCounter(2, 0, 0);
        assertFalse(store.isVisited(config, 0, 0), "precondition: slot 0 is empty before the mark");

        store.markVisited(config, 0, 0);

        assertTrue(store.isVisited(config, 0, 0), "slot 0 must answer for the state marked into it");
        assertEquals(1, store.preemptionStatesMarked(), "the preemption mark is counted");
        assertTrue(store.bitCount() > 0,
            "the mark reached a real bit vector, not a discarded slot");
        assertTrue(store.estimatedFalsePositiveRate() > 0.0,
            "a zero-bound store that has marked reports a non-zero estimate, not the empty-store 0.0");

        // A bound of zero admits exactly one level: asking for level 1 must be refused, not wrapped.
        assertThrows(IllegalArgumentException.class, () -> store.isVisited(config, 0, 1),
            "level 1 exceeds a zero bound and must be rejected");
        assertThrows(IllegalArgumentException.class, () -> store.markVisited(config, 0, 1),
            "level 1 exceeds a zero bound and must be rejected");
    }

    // ---------------------------------------------------------------- R8 — the accessors

    /**
     * R8 — {@code size()} returns the constructed bit capacity.
     *
     * <p>{@code size} is the {@code m} factor in the FPR formula, so this accessor is one of the two
     * inputs the user-facing number is computed from. Returning a derived or recomputed value instead
     * of the constructor argument would leave the formula consuming a capacity the store does not
     * have.
     */
    @Test
    void size_returnsConstructedCapacity() {
        assertEquals(1000, new BitstateStore(1000, 4, 2).size(), "the three-argument constructor");
        assertEquals(1, new BitstateStore(1).size(), "the smallest legal capacity");
        assertEquals(1_000_003, new BitstateStore(1_000_003).size(), "the default corpus capacity");
        assertEquals(64, new BitstateStore(64, 2).size(), "a power-of-two capacity");

        // Independent of marking: the capacity is a property of construction, not of use.
        BitstateStore store = new BitstateStore(1000, 4, 2);
        markPlain(store, 25);
        assertEquals(1000, store.size(), "marking must not change the reported capacity");
    }

    /**
     * R8 — {@code numHashFunctions()} returns the constructed {@code k}.
     *
     * <p>{@code k} is both the hash count and the FPR formula's exponent. Nothing in production reads
     * this accessor, so an unpinned return value would be invisible until a user compared the reported
     * FPR against their own calculation of {@code (1 - e^(-kn/m))^k}.
     */
    @Test
    void numHashFunctions_returnsConstructedK() {
        assertEquals(4, new BitstateStore(1000, 4, 2).numHashFunctions(),
            "the three-argument constructor");
        assertEquals(1, new BitstateStore(1000, 1).numHashFunctions(), "the smallest legal k");
        assertEquals(7, new BitstateStore(1000, 7).numHashFunctions(), "an unusual k");

        BitstateStore store = new BitstateStore(1000, 3, 2);
        assertEquals(3, store.numHashFunctions(), "the two-argument constructor's k");
        markPlain(store, 10);
        assertEquals(3, store.numHashFunctions(), "marking must not change the reported k");
    }

    /**
     * R8 — the accessors agree with each other and with the estimate's own inputs.
     *
     * <p>Pins that {@code size()} and {@code numHashFunctions()} are the quantities the FPR formula
     * consumes, by checking the estimate they imply against the literal for case A. Without this, both
     * accessors could return swapped or scaled values that every other test tolerated.
     */
    @Test
    void accessors_areTheQuantitiesTheFormulaConsumes() {
        BitstateStore store = new BitstateStore(1000, 3, 2);
        markPlain(store, 100);

        // Recomputing from the accessors' values, not from the constructor arguments, pins the
        // accessors to the formula's inputs: if either drifted, the literal below would no longer hold.
        double m = store.size();
        double k = store.numHashFunctions();
        double n = store.statesMarked();
        assertEquals(0.017410586496326586, Math.pow(1.0 - Math.exp(-k * n / m), k), 1e-9,
            "precondition: the literal follows from the accessor values alone");
        assertEquals(0.017410586496326586, store.estimatedFalsePositiveRate(), 1e-9,
            "the store's estimate must agree with its own accessors");

        assertInstanceOf(BitstateStore.class, store, "the store is concrete, so the casts are safe");
    }

    // ---------------------------------------------------------------- fixtures

    /**
     * Marks {@code count} distinct configurations through the plain API.
     *
     * <p>Distinct values are required so {@code statesMarked} reflects distinct insertions. Note that
     * {@code markVisited} increments its counter on every call regardless of whether the bits were
     * already set, so distinctness here is about honesty rather than correctness.
     */
    private static void markPlain(BitstateStore store, int count) {
        for (int i = 0; i < count; i++) {
            store.markVisited(configurationWithCounter(i + 1, 0, 0));
        }
    }

    /**
     * Marks {@code count} distinct configurations into preemption vector {@code level}.
     *
     * <p>{@code lastThreadId} is set to the level so the calls stay distinguishable; it does not
     * affect how many vectors are in use, which is what the FPR cases depend on.
     */
    private static void markPreemption(BitstateStore store, int level, int count) {
        for (int i = 0; i < count; i++) {
            store.markVisited(configurationWithCounter(i + 1, 0, 0), level, level);
        }
    }

    /**
     * Builds a configuration fixture with one thread position and two program counters.
     *
     * <p>Reflection is unavoidable: {@link Configuration} exposes only {@code initial} and
     * {@code successor}, and neither can place a counter at an arbitrary value without executing a
     * program. This arranges a precondition and asserts nothing about the store's internals.
     */
    private static Configuration configurationWithCounter(int stateValue, int pc0, int pc1) {
        List<Integer> counters = new ArrayList<>();
        counters.add(pc0);
        counters.add(pc1);
        try {
            Constructor<Configuration> ctor = Configuration.class.getDeclaredConstructor(
                SharedState.class, List.class, java.util.Map.class, java.util.Map.class, List.class,
                boolean.class, boolean.class, StepOutcome.class);
            ctor.setAccessible(true);
            return ctor.newInstance(new CounterState(stateValue), counters, new HashMap<>(),
                new HashMap<>(), List.of(0, 1), false, false, null);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("cannot build a Configuration fixture", e);
        }
    }
}