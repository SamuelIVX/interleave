/** Verifies automatic DSL property observations and reduction through the public JSON loader. */
package dev.samhb.interleave.format.dsl;

import dev.samhb.interleave.format.ProgramLoader;
import dev.samhb.interleave.format.registry.RegistryException;
import dev.samhb.interleave.core.MemoryLocation;
import dev.samhb.interleave.por.StaticPorExplorer;
import dev.samhb.interleave.search.*;
import org.junit.jupiter.api.Test;
import java.util.Set;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

/** Checks DSL-derived observation metadata and property-preserving reduction. */
class DslPropertyObservationTest {
    /** Verifies that always properties expose reads and reduce invisible work. */
    @Test
    void alwaysPropertiesExposeReadsAndReduceInvisibleWork() {
        var model = new ProgramLoader().load(twoWrites("always", "x >= 0"));
        Invariant property = model.invariant().orElseThrow();
        assertEquals(Set.of(MemoryLocation.of("x")), property.observedLocations().orElseThrow());
        assertEquals(4, new DfsExplorer().explore(model.program(), property).statesExplored());
        assertEquals(3, new StaticPorExplorer().explore(model.program(), property).statesExplored());
    }

    /** Verifies that final properties remain unknown and exhaustive. */
    @Test
    void finalPropertiesRemainUnknownAndExhaustive() {
        var model = new ProgramLoader().load(twoWrites("final", "x >= 0"));
        Invariant property = model.invariant().orElseThrow();
        assertTrue(property.observedLocations().isEmpty());
        assertEquals(4, new StaticPorExplorer().explore(model.program(), property).statesExplored());
    }

    /** Verifies that constant always property has known empty reads. */
    @Test
    void constantAlwaysPropertyHasKnownEmptyReads() {
        var model = new ProgramLoader().load(twoWrites("always", "true"));
        Invariant property = model.invariant().orElseThrow();
        assertEquals(Set.of(), property.observedLocations().orElseThrow());
        assertEquals(3, new StaticPorExplorer().explore(model.program(), property).statesExplored());
    }

    /** Verifies that conjunctions and dynamic indices include all expression inputs. */
    @Test
    void conjunctionsAndDynamicIndicesIncludeAllExpressionInputs() {
        var model = new ProgramLoader().load(arrays(
            "{\"when\":\"always\",\"all\":[\"arr[0] >= 0\",\"arr[idx] >= 0\",\"!(x < 0) || y >= 0\"]}",
            "x = 1", "y = 1"));
        Invariant property = model.invariant().orElseThrow();
        assertEquals(Set.of(MemoryLocation.of("arr[0]"), MemoryLocation.of("arr"),
            MemoryLocation.of("idx"), MemoryLocation.of("x"), MemoryLocation.of("y")),
            property.observedLocations().orElseThrow());
    }

    /** Verifies that dynamic array writes remain visible to an element property. */
    @Test
    void dynamicArrayWritesRemainVisibleToAnElementProperty() {
        var model = new ProgramLoader().load(arrays(
            "{\"when\":\"always\",\"expr\":\"arr[1] == 0\"}", "arr[idx] = 1", "y = 1"));
        Invariant property = model.invariant().orElseThrow();
        DfsResult result = new StaticPorExplorer().explore(model.program(), property);
        assertTrue(result.traces().stream().anyMatch(trace -> trace.outcome() == TraceOutcome.VIOLATION));
        assertEquals(List.of(1, 0), result.traces().getFirst().threadIds(),
            "the invisible scalar write goes first; the aliased array write must remain visible");
    }

    /** Verifies that runtime predicate errors remain violations. */
    @Test
    void runtimePredicateErrorsRemainViolations() {
        var model = new ProgramLoader().load(twoWrites("always", "x % 0 == 0"));
        Invariant property = model.invariant().orElseThrow();
        assertEquals(Set.of(MemoryLocation.of("x")), property.observedLocations().orElseThrow());
        DfsResult result = new StaticPorExplorer().explore(model.program(), property);
        assertEquals(1, result.statesExplored());
        assertEquals(TraceOutcome.VIOLATION, result.traces().getFirst().outcome());
    }

    /** Verifies that local arrays are rejected before observation analysis. */
    @Test
    void localArraysAreRejectedBeforeObservationAnalysis() {
        RegistryException error = assertThrows(RegistryException.class, () -> new ProgramLoader().load(
            arrays("{\"when\":\"always\",\"expr\":\"x >= 0\"}", "x = local.r[idx]", "y = 1")));
        assertTrue(error.getMessage().contains("Local array access not supported"));
    }

    /** Verifies that independent work reduces an eight thread grid to one schedule. */
    @Test
    void independentWorkReducesAnEightThreadGridToOneSchedule() {
        String threads = java.util.stream.IntStream.range(0, 8)
            .mapToObj(id -> "{\"id\":" + id + ",\"steps\":[{\"effects\":[\"arr[" + id
                + "] = 1\"]},{\"effects\":[\"arr[" + id + "] = 2\"]}]}")
            .collect(java.util.stream.Collectors.joining(","));
        var model = new ProgramLoader().load("""
            {"format":"declarative", "name":"grid",
             "state":{"fields":[{"name":"safe","type":"bool","init":true},
                                  {"name":"arr","type":"int[]","init":[0,0,0,0,0,0,0,0]}]},
             "threads":[%s], "invariant":{"when":"always","expr":"safe"}}
            """.formatted(threads));
        Invariant property = model.invariant().orElseThrow();
        // Independently: eight three-position counters give 3^8 positions; one schedule has 16 edges.
        assertEquals(6561, new DfsExplorer().explore(model.program(), property).statesExplored());
        DfsResult por = new StaticPorExplorer().explore(model.program(), property);
        assertEquals(17, por.statesExplored());
        assertEquals(1, por.traces().size());
        assertEquals(TraceOutcome.COMPLETED, por.traces().getFirst().outcome());
    }

    /**
     * Loads a two-thread array program with the supplied assignments and always property.
     * @param invariant property to check, or null when no property is supplied
     * @param firstEffect first thread assignment
     * @param secondEffect second thread assignment
     * @return loaded two-thread array benchmark with the supplied property
     */
    private static String arrays(String invariant, String firstEffect, String secondEffect) {
        return """
            {"format":"declarative", "name":"arrays",
             "state":{"fields":[{"name":"x","type":"int","init":0},
                 {"name":"y","type":"int","init":0}, {"name":"idx","type":"int","init":1},
                 {"name":"arr","type":"int[]","init":[0,0]}]},
             "threads":[{"id":0,"steps":[{"effects":["%s"]}]},
                        {"id":1,"steps":[{"effects":["%s"]}]}],
             "invariant":%s}
            """.formatted(firstEffect, secondEffect, invariant);
    }

    /**
     * Loads a two-thread scalar-write program with a property evaluated in the requested phase.
     * @param when property evaluation phase
     * @param expression property expression
     * @return loaded scalar-write benchmark with the supplied property phase
     */
    private static String twoWrites(String when, String expression) {
        return """
            {"format":"declarative", "name":"observed",
             "state":{"fields":[{"name":"x","type":"int","init":0},
                                  {"name":"y","type":"int","init":0}]},
             "threads":[{"id":0,"steps":[{"effects":["x = 1"]}]},
                        {"id":1,"steps":[{"effects":["y = 1"]}]}],
             "invariant":{"when":"%s","expr":"%s"}}
            """.formatted(when, expression);
    }
}
