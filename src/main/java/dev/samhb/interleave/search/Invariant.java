/** Defines safety predicates and optional state observations for property-aware static reduction. */
package dev.samhb.interleave.search;

import dev.samhb.interleave.core.Configuration;
import dev.samhb.interleave.core.SharedState;
import dev.samhb.interleave.core.MemoryLocation;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.Predicate;

/** A pure safety check; ordinary callbacks retain exhaustive invariant traversal. */
@FunctionalInterface
public interface Invariant {
    /**
     * Checks a modeled position without mutating it or external state.
     * @param state current shared-state value
     * @param config current counters and configuration metadata
     * @return whether the safety property holds
     */
    boolean holds(SharedState state, Configuration config);

    /**
     * Describes a state-only property's complete observation footprint, when known.
     *
     * <p>An absent value means unknown or configuration-sensitive observations and retains
     * exhaustive branching. A present empty set describes a constant property. Opted-in checks
     * must be deterministic, value-based, and depend only on the declared locations, including
     * inputs to evaluation errors. Counters, termination, history and object identity are excluded.
     * Locations must use the same names and array aliases as the steps' footprints.
     *
     * @return the stable complete observation set, or empty when reduction is unsupported
     */
    default Optional<Set<MemoryLocation>> observedLocations() {
        return Optional.empty();
    }

    /**
     * Creates an explicitly observed, state-only safety property.
     *
     * <p>Incomplete observations can cause false passes. The predicate must satisfy
     * {@link #observedLocations()}'s contract. Static POR may explore fewer states and schedules;
     * violation preservation requires complete step footprints and an exact visited store.
     *
     * <pre>{@code
     * Invariant safe = Invariant.observing(Set.of(MemoryLocation.of("flag[0]")),
     *     state -> !((PetersonState) state).flag(0));
     * }</pre>
     *
     * @param locations all state locations the predicate can observe; copied defensively
     * @param predicate pure state-value check
     * @return an invariant with immutable, known observations
     * @throws NullPointerException if an argument or location is null
     */
    static Invariant observing(Set<MemoryLocation> locations, Predicate<SharedState> predicate) {
        Set<MemoryLocation> snapshot = Set.copyOf(locations);
        Objects.requireNonNull(predicate, "predicate");
        return new Invariant() {
            @Override
            public boolean holds(SharedState state, Configuration config) {
                return predicate.test(state);
            }

            @Override
            public Optional<Set<MemoryLocation>> observedLocations() {
                return Optional.of(snapshot);
            }
        };
    }
}
