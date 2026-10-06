/** Tests explicit state-only property observations without changing ordinary callback semantics. */
package dev.samhb.interleave.search;

import dev.samhb.interleave.core.Configuration;
import dev.samhb.interleave.core.MemoryLocation;
import dev.samhb.interleave.core.PetersonState;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class InvariantObservationTest {
    @Test
    void ordinaryCallbacksHaveUnknownObservations() {
        Invariant callback = (state, config) -> config.allTerminated();
        assertTrue(callback.observedLocations().isEmpty());
    }

    @Test
    void observingSnapshotsLocationsAndEvaluatesTheStatePredicate() {
        Set<MemoryLocation> locations = new HashSet<>(Set.of(MemoryLocation.of("flag[0]")));
        Invariant property = Invariant.observing(locations, state -> !((PetersonState) state).flag(0));
        locations.clear();
        assertEquals(Set.of(MemoryLocation.of("flag[0]")), property.observedLocations().orElseThrow());
        assertThrows(UnsupportedOperationException.class,
            () -> property.observedLocations().orElseThrow().clear());
        Configuration before = Configuration.initial(PetersonState.of(false, false, 0), List.of());
        Configuration after = Configuration.initial(PetersonState.of(true, false, 0), List.of());
        assertTrue(property.holds(before.state(), before));
        assertFalse(property.holds(after.state(), after));
    }

    @Test
    void constantStatePropertiesHaveKnownEmptyObservations() {
        Invariant property = Invariant.observing(Set.of(), state -> true);
        assertTrue(property.observedLocations().isPresent());
        assertEquals(Set.of(), property.observedLocations().orElseThrow());
    }
}
