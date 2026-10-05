package dev.samhb.interleave.format.registry;

import dev.samhb.interleave.core.SharedState;
import dev.samhb.interleave.core.PetersonState;
import dev.samhb.interleave.core.CounterState;
import dev.samhb.interleave.core.DclState;
import dev.samhb.interleave.core.DeadlockState;
import dev.samhb.interleave.core.PairState;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class StateRegistryTest {

    private final StateRegistry registry = new StateRegistry();

    @Test
    void allFiveStateTypesRegistered() {
        assertEquals(5, registry.typeNames().size());
        assertTrue(registry.typeNames().contains("peterson"));
        assertTrue(registry.typeNames().contains("counter"));
        assertTrue(registry.typeNames().contains("dcl"));
        assertTrue(registry.typeNames().contains("deadlock"));
        assertTrue(registry.typeNames().contains("pair"));
    }

    @Test
    void createState_unknownType_throws() {
        com.google.gson.JsonObject json = new com.google.gson.JsonObject();
        json.addProperty("type", "unknown_state");
        
        RegistryException ex = assertThrows(RegistryException.class,
            () -> registry.create(json, 2));
        assertTrue(ex.getMessage().contains("Unknown state type"));
    }

    @Test
    void createPetersonState_valid() {
        com.google.gson.JsonObject json = new com.google.gson.JsonObject();
        json.addProperty("type", "peterson");
        com.google.gson.JsonArray flags = new com.google.gson.JsonArray();
        flags.add(false);
        flags.add(false);
        json.add("flags", flags);
        json.addProperty("turn", 0);
        
        dev.samhb.interleave.core.SharedState state = registry.create(json, 2);
        assertTrue(state instanceof dev.samhb.interleave.core.PetersonState);
        dev.samhb.interleave.core.PetersonState ps = (dev.samhb.interleave.core.PetersonState) state;
        assertFalse(ps.flag(0));
        assertFalse(ps.flag(1));
        assertEquals(0, ps.turn());
    }

    @Test
    void createPetersonState_missingFlags_throws() {
        com.google.gson.JsonObject json = new com.google.gson.JsonObject();
        json.addProperty("type", "peterson");
        json.addProperty("turn", 0);
        
        RegistryException ex = assertThrows(RegistryException.class,
            () -> registry.create(json, 2));
        assertTrue(ex.getMessage().contains("flags"));
    }

    @Test
    void createPetersonState_flagCountDisagreeingWithThreads_throws() {
        com.google.gson.JsonObject json = new com.google.gson.JsonObject();
        json.addProperty("type", "peterson");
        com.google.gson.JsonArray flags = new com.google.gson.JsonArray();
        flags.add(false);
        json.add("flags", flags);
        json.addProperty("turn", 0);
        
        RegistryException ex = assertThrows(RegistryException.class,
            () -> registry.create(json, 2));
        assertTrue(ex.getMessage().contains("one element per thread"), ex.getMessage());
    }

    @Test
    void createCounterState_valid() {
        com.google.gson.JsonObject json = new com.google.gson.JsonObject();
        json.addProperty("type", "counter");
        json.addProperty("counter", 5);
        
        dev.samhb.interleave.core.SharedState state = registry.create(json, 2);
        assertTrue(state instanceof dev.samhb.interleave.core.CounterState);
        dev.samhb.interleave.core.CounterState cs = (dev.samhb.interleave.core.CounterState) state;
        assertEquals(5, cs.counter());
    }

    @Test
    void createDclState_valid() {
        com.google.gson.JsonObject json = new com.google.gson.JsonObject();
        json.addProperty("type", "dcl");
        json.addProperty("initialized", true);
        
        dev.samhb.interleave.core.SharedState state = registry.create(json, 2);
        assertTrue(state instanceof dev.samhb.interleave.core.DclState);
        dev.samhb.interleave.core.DclState ds = (dev.samhb.interleave.core.DclState) state;
        assertTrue(ds.initialized());
    }

    @Test
    void createDeadlockState_valid() {
        com.google.gson.JsonObject json = new com.google.gson.JsonObject();
        json.addProperty("type", "deadlock");
        com.google.gson.JsonArray flags = new com.google.gson.JsonArray();
        flags.add(true);
        flags.add(false);
        json.add("flags", flags);
        
        dev.samhb.interleave.core.SharedState state = registry.create(json, 2);
        assertTrue(state instanceof dev.samhb.interleave.core.DeadlockState);
        dev.samhb.interleave.core.DeadlockState ds = (dev.samhb.interleave.core.DeadlockState) state;
        assertTrue(ds.flag(0));
        assertFalse(ds.flag(1));
    }

    @Test
    void createPairState_valid() {
        com.google.gson.JsonObject json = new com.google.gson.JsonObject();
        json.addProperty("type", "pair");
        json.addProperty("high", 10);
        json.addProperty("low", 20);

        dev.samhb.interleave.core.SharedState state = registry.create(json, 2);
        assertTrue(state instanceof dev.samhb.interleave.core.PairState);
        dev.samhb.interleave.core.PairState ps = (dev.samhb.interleave.core.PairState) state;
        assertEquals(10, ps.high());
        assertEquals(20, ps.low());
    }

    // --- A3: per-thread arrays sized from the declared thread count ---

    /**
     * Three threads, which the registry format could not express at all before: peterson and deadlock
     * rejected any flags array whose length was not 2. The step layer already indexed flags by thread
     * id, so only the size check and the encoding were in the way.
     */
    @Test
    void createPetersonState_threeThreads() {
        com.google.gson.JsonObject json = petersonJson(3);

        PetersonState ps = (PetersonState) registry.create(json, 3);

        assertFalse(ps.flag(0));
        assertTrue(ps.flag(1));
        assertFalse(ps.flag(2));
        assertEquals(2, ps.turn());
    }

    @Test
    void createDeadlockState_threeThreads() {
        com.google.gson.JsonObject json = new com.google.gson.JsonObject();
        json.addProperty("type", "deadlock");
        json.add("flags", flags(true, false, true));

        DeadlockState ds = (DeadlockState) registry.create(json, 3);

        assertTrue(ds.flag(0));
        assertFalse(ds.flag(1));
        assertTrue(ds.flag(2));
    }

    /** counter had the identical ceiling by a different route: {@code CounterState.of(int)} defaulted to 2. */
    @Test
    void createCounterState_threeThreadsSizesTheRegisters() {
        com.google.gson.JsonObject json = new com.google.gson.JsonObject();
        json.addProperty("type", "counter");
        json.addProperty("counter", 5);

        CounterState cs = (CounterState) registry.create(json, 3);

        // Reachable only if the register array was sized from the thread count.
        cs.setRegister(2, 42);
        assertEquals(42, cs.getRegister(2));
    }

    /**
     * A three-thread program declaring two flags is malformed, not something to pad. Reporting it keeps
     * the ceiling loud rather than silently reshaping a program.
     */
    @Test
    void createPetersonState_flagCountBelowThreadCount_throws() {
        RegistryException ex = assertThrows(RegistryException.class,
                () -> registry.create(petersonJson(2), 3));
        assertTrue(ex.getMessage().contains("one element per thread"), ex.getMessage());
    }

    @Test
    void createPetersonState_flagCountAboveThreadCount_throws() {
        RegistryException ex = assertThrows(RegistryException.class,
                () -> registry.create(petersonJson(4), 3));
        assertTrue(ex.getMessage().contains("one element per thread"), ex.getMessage());
    }

    /** Peterson and the deadlock demo are defined for two or more threads; one is not padded up to two. */
    @Test
    void createPetersonState_singleThread_throws() {
        RegistryException ex = assertThrows(RegistryException.class,
                () -> registry.create(petersonJson(1), 1));
        assertTrue(ex.getMessage().contains("at least 2 threads"), ex.getMessage());
    }

    @Test
    void createDeadlockState_singleThread_throws() {
        com.google.gson.JsonObject json = new com.google.gson.JsonObject();
        json.addProperty("type", "deadlock");
        json.add("flags", flags(false));

        RegistryException ex = assertThrows(RegistryException.class,
                () -> registry.create(json, 1));
        assertTrue(ex.getMessage().contains("at least 2 threads"), ex.getMessage());
    }

    /** The constructors refuse a degenerate flag array on their own, not only via the registry. */
    @Test
    void stateConstructors_rejectFewerThanTwoFlags() {
        assertThrows(IllegalArgumentException.class, () -> PetersonState.of(new boolean[]{true}, 0));
        assertThrows(IllegalArgumentException.class, () -> DeadlockState.of(new boolean[]{true}));
    }

    /**
     * Two three-flag states differing only at index 2 must encode differently.
     *
     * <p>This is the probe the two-versus-three comparison cannot make. An encoder that wrote the count
     * and then only the first two flags would pass that comparison — the lengths differ, so the byte
     * counts differ — while silently dropping every flag past index 1. Only a pair at the <em>same</em>
     * length differing in a later position can catch it.
     */
    @Test
    void flagBeyondTheSecond_reachesTheEncoding() {
        byte[] off = encode(DeadlockState.of(new boolean[]{true, false, false}));
        byte[] on = encode(DeadlockState.of(new boolean[]{true, false, true}));

        assertFalse(Arrays.equals(off, on),
                "flag[2] must reach the encoding; a three-thread state's third flag cannot be dropped");

        PetersonState pOff = PetersonState.of(new boolean[]{false, false, false}, 0);
        PetersonState pOn = PetersonState.of(new boolean[]{false, false, false}, 0);
        pOn.setFlag(2, true);
        assertNotEquals(pOff, pOn, "precondition: the two states differ only at flag[2]");
        assertFalse(Arrays.equals(encode(pOff), encode(pOn)),
                "PetersonState.flag[2] must reach the encoding too");
    }

    /**
     * A longer flag array must encode differently from a shorter one. Without the count prefix the two
     * differ only in byte count, which keeps them apart incidentally; the prefix makes it explicit.
     */
    @Test
    void flagArrayLengthReachesTheEncoding() {
        byte[] two = encode(DeadlockState.of(new boolean[]{true, false}));
        byte[] three = encode(DeadlockState.of(new boolean[]{true, false, true}));

        assertFalse(java.util.Arrays.equals(two, three),
                "a three-thread state must not share an encoding with a two-thread one");
    }

    /** And equal states must still encode identically at three threads. */
    @Test
    void equalThreeThreadStates_encodeIdentically() {
        assertArrayEquals(encode(PetersonState.of(new boolean[]{false, true, false}, 1)),
                encode(PetersonState.of(new boolean[]{false, true, false}, 1)));
    }

    /**
     * A deep copy must not alias the original's flag array, at any length.
     *
     * <p>Indices 1 <em>and</em> 2 are both mutated. A {@code deepCopy} that copied only the first two
     * flags would leave index 2 false in both states and still pass a test that looked at index 1 alone,
     * so the third flag is what catches truncation.
     */
    @Test
    void deepCopy_doesNotShareTheFlagArray() {
        PetersonState original = PetersonState.of(new boolean[]{false, false, false}, 0);
        PetersonState copy = (PetersonState) original.deepCopy();

        copy.setFlag(1, true);
        copy.setFlag(2, true);

        assertFalse(original.flag(1), "mutating the copy must not write through to the original");
        assertFalse(original.flag(2), "index 2 must be copied, not truncated away");
        assertTrue(copy.flag(1));
        assertTrue(copy.flag(2));
    }

    private static com.google.gson.JsonObject petersonJson(int threads) {
        com.google.gson.JsonObject json = new com.google.gson.JsonObject();
        json.addProperty("type", "peterson");
        boolean[] values = new boolean[threads];
        if (threads > 1) values[1] = true;
        json.add("flags", flags(values));
        json.addProperty("turn", threads - 1);
        return json;
    }

    private static com.google.gson.JsonArray flags(boolean... values) {
        com.google.gson.JsonArray array = new com.google.gson.JsonArray();
        for (boolean v : values) array.add(v);
        return array;
    }

    private static byte[] encode(SharedState state) {
        java.io.ByteArrayOutputStream bytes = new java.io.ByteArrayOutputStream();
        try (java.io.DataOutputStream out = new java.io.DataOutputStream(bytes)) {
            state.encodeTo(out);
        } catch (java.io.IOException e) {
            throw new AssertionError("encoding failed", e);
        }
        return bytes.toByteArray();
    }
}