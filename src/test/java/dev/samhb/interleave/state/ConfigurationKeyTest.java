/** Tests the shared configuration-key interface independently of diagnostic text and object identity. */
package dev.samhb.interleave.state;

import dev.samhb.interleave.core.Configuration;
import dev.samhb.interleave.core.DclState;
import dev.samhb.interleave.core.PetersonState;
import dev.samhb.interleave.core.SharedState;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.io.DataOutput;
import java.io.IOException;
import java.util.List;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ConfigurationKeyTest {
    private final CanonicalEncoder encoder = new CanonicalEncoder();

    /** Identical byte payloads from different state domains are different search positions. */
    @Test
    void keyDistinguishesStateTypesWithIdenticalPayloads() {
        Configuration first = Configuration.initial(new IntState(7), List.of());
        Configuration second = Configuration.initial(new OtherIntState(7), List.of());

        assertArrayEquals(encoder.encode(first.state()), encoder.encode(second.state()),
            "the fixture must isolate state type, not payload differences");
        assertNotEquals(encoder.configurationKey(first), encoder.configurationKey(second));
    }

    /** The exact store must use the same type-aware identity as the shared key interface. */
    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void exactStoreDoesNotMergeDifferentStateTypes(boolean scheduled) {
        Configuration first = Configuration.initial(new IntState(7), List.of());
        Configuration second = Configuration.initial(new OtherIntState(7), List.of());
        HashingStateStore store = new HashingStateStore();
        if (scheduled) {
            store.markVisited(first, 0, 0);
            assertFalse(store.isVisited(second, 0, 0));
            store.markVisited(second, 0, 0);
            assertEquals(2, store.preemptionEntryCount());
        } else {
            store.markVisited(first);
            assertFalse(store.isVisited(second));
            store.markVisited(second);
            assertEquals(2, store.size());
        }
    }

    /** Value-equal DCL states retain one key despite different sentinel objects and diagnostics. */
    @Test
    void equalValuesWithDifferentDiagnosticObjectsShareAKey() {
        DclState first = DclState.of(false);
        DclState second = DclState.of(false);
        first.setInstance("first diagnostic sentinel");
        second.setInstance("second diagnostic sentinel");
        assertEquals(first, second, "DCL identity observes presence, not sentinel reference/text");
        assertNotEquals(first.toString(), second.toString(), "the fixture isolates diagnostic drift");

        Configuration a = Configuration.initial(first, List.of());
        Configuration b = Configuration.initial(second, List.of());
        assertEquals(encoder.configurationKey(a), encoder.configurationKey(b));
        assertEquals(encoder.configurationKey(a, 0), encoder.configurationKey(b, 0));
    }

    /** State values, ordered counters, and counter-list lengths are each part of base identity. */
    @Test
    void keyDistinguishesStateValuesAndOrderedCounters() {
        Configuration base = Configuration.forTest(new IntState(7), List.of(0, 1), List.of());
        Configuration stateChanged = Configuration.forTest(new IntState(8), List.of(0, 1), List.of());
        Configuration reordered = Configuration.forTest(new IntState(7), List.of(1, 0), List.of());
        Configuration fewerThreads = Configuration.forTest(new IntState(7), List.of(0), List.of());
        String key = encoder.configurationKey(base);

        assertNotEquals(key, encoder.configurationKey(stateChanged));
        assertNotEquals(key, encoder.configurationKey(reordered));
        assertNotEquals(key, encoder.configurationKey(fewerThreads));
        assertNotEquals(encoder.configurationKey(base, 0), encoder.configurationKey(stateChanged, 0));
        assertNotEquals(encoder.configurationKey(base, 0), encoder.configurationKey(reordered, 0));
    }

    /** The initial sentinel and each last thread are distinct; preemption cost is not identity. */
    @Test
    void schedulingKeysRetainTheLastThreadAndBudgetDominance() {
        Configuration config = Configuration.initial(new IntState(7), List.of());
        assertNotEquals(encoder.configurationKey(config, -1), encoder.configurationKey(config, 0));
        assertNotEquals(encoder.configurationKey(config, 0), encoder.configurationKey(config, 1));

        HashingStateStore store = new HashingStateStore();
        store.markVisited(config, 0, 2);
        assertTrue(store.isVisited(config, 0, 3));
        assertFalse(store.isVisited(config, 0, 1));
        assertFalse(store.isVisited(config, 1, 3));
        store.markVisited(config, 0, 1);
        assertTrue(store.isVisited(config, 0, 1));
        assertEquals(1, store.preemptionEntryCount(), "cost improvements update one scheduling position");
    }

    /** A returned key remains an immutable snapshot even if the configuration's state changes. */
    @Test
    void aReturnedKeyDoesNotChangeWhenSharedStateIsMutated() {
        Configuration config = Configuration.initial(PetersonState.of(false, false, 0), List.of());
        String before = encoder.configurationKey(config);
        Map<String, String> saved = new HashMap<>();
        saved.put(before, "before mutation");
        ((PetersonState) config.state()).setFlag(0, true);
        String after = encoder.configurationKey(config);

        assertNotEquals(before, after, "a later call reads current values rather than a cached key");
        assertEquals("before mutation", saved.get(before));
        assertFalse(saved.containsKey(after));
        assertEquals(before, encoder.configurationKey(
            Configuration.initial(PetersonState.of(false, false, 0), List.of())));
    }

    /** A state domain with a single integer payload. */
    private record IntState(int value) implements SharedState {
        @Override
        public SharedState deepCopy() {
            return this;
        }

        @Override
        public void encodeTo(DataOutput out) throws IOException {
            out.writeInt(value);
        }
    }

    /** A different domain with the same wire payload, used to exercise the type discriminator. */
    private record OtherIntState(int value) implements SharedState {
        @Override
        public SharedState deepCopy() {
            return this;
        }

        @Override
        public void encodeTo(DataOutput out) throws IOException {
            out.writeInt(value);
        }
    }
}
