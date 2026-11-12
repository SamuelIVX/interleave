// Exact backend allocation, full-key membership and run-bound selection contracts.
package dev.samhb.interleave.search;

import dev.samhb.interleave.Strategy;
import dev.samhb.interleave.format.SourceProgramLoader;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import static org.junit.jupiter.api.Assertions.*;

/** Checks the actual certified containers and preserves selection when a store is copied. */
class SymmetryBackendTest {
    /** The exact factory preserves its original strategy-before-backend rejection order. */
    @org.junit.jupiter.api.Test void nullArgumentsRetainValidationOrder() {
        assertEquals("strategy",assertThrows(NullPointerException.class, () ->
            SymmetryStateStore.forRun(null,null,null,null)).getMessage());
    }
    /** Each allocation is independent and full string equality distinguishes known hash collisions.
     * @param backend certified collection implementation
     */
    @ParameterizedTest @EnumSource(SymmetryBackend.class)
    void createsIndependentExactContainers(SymmetryBackend backend) {
        Set<String> keys = backend.newSet(); Map<String,Integer> costs = backend.newMap();
        assertEquals(backend == SymmetryBackend.ORDERED ? TreeSet.class : HashSet.class,keys.getClass());
        assertEquals(backend == SymmetryBackend.ORDERED ? TreeMap.class : HashMap.class,costs.getClass());
        assertEquals(2112,"Aa".hashCode()); assertEquals(2112,"BB".hashCode());
        assertTrue(keys.add("Aa")); assertFalse(keys.contains("BB")); assertTrue(keys.add("BB"));
        assertFalse(keys.add("Aa")); assertEquals(Set.of("Aa","BB"),keys);
        costs.put("Aa",1); costs.put("BB",2); assertEquals(1,costs.get("Aa")); assertEquals(2,costs.get("BB"));
        assertTrue(backend.newSet().isEmpty()); assertTrue(backend.newMap().isEmpty());
        assertNotSame(keys,backend.newSet()); assertNotSame(costs,backend.newMap());
        if (backend == SymmetryBackend.ORDERED) {
            assertNull(((TreeSet<String>)keys).comparator()); assertNull(((TreeMap<String,Integer>)costs).comparator());
        }
    }

    /** Copies retain backend/run identity while emptying value, minimum-cost and sleep coverage.
     * @param backend immutable storage configuration
     */
    @ParameterizedTest @EnumSource(SymmetryBackend.class)
    void copiesKeepBackendAndBoundObjectsWithoutSharingMembership(SymmetryBackend backend) {
        String source = "program pair { shared int x = 0; thread 0 { atomic {} } thread 1 { atomic {} } }";
        var model = new SourceProgramLoader().load(source); var initial = model.program().initialConfiguration();
        var store = SymmetryStateStore.forRun(model.program(),null,Strategy.DPOR,backend);
        assertEquals(backend,store.backend()); store.markVisited(initial); store.markVisited(initial,-1,2);
        assertFalse(store.sleepCovered(initial,Set.of()));
        var copy = (SymmetryStateStore)store.freshCopy(); assertEquals(backend,copy.backend()); assertNotSame(store,copy);
        assertFalse(copy.isVisited(initial)); assertFalse(copy.isVisited(initial,-1,2)); assertFalse(copy.sleepCovered(initial,Set.of()));
        assertTrue(store.isVisited(initial)); assertTrue(store.isVisited(initial,-1,2)); assertTrue(store.sleepCovered(initial,Set.of()));
        assertDoesNotThrow(() -> copy.requireRun(model.program(),null,Strategy.DPOR));
        var other = new SourceProgramLoader().load(source);
        assertThrows(IllegalArgumentException.class,() -> copy.requireRun(other.program(),null,Strategy.DPOR));
        assertThrows(IllegalArgumentException.class,() -> copy.requireRun(model.program(),(s,c) -> true,Strategy.DPOR));
        assertThrows(IllegalArgumentException.class,() -> copy.requireRun(model.program(),null,Strategy.DFS));
        store.clear(); assertFalse(store.isVisited(initial)); assertFalse(store.isVisited(initial,-1,2));
        assertFalse(store.sleepCovered(initial,Set.of())); assertTrue(copy.sleepCovered(initial,Set.of()));
    }

    /** The legacy factory retains HASHED and rejects a missing backend before creating membership. */
    @Test void defaultFactoryStaysHashedAndExplicitNullRejects() {
        var model = new SourceProgramLoader().load("program pair { shared int x = 0; thread 0 { atomic {} } thread 1 { atomic {} } }");
        assertEquals(SymmetryBackend.HASHED,SymmetryStateStore.forRun(model.program(),null,Strategy.DFS).backend());
        assertEquals("backend",assertThrows(NullPointerException.class,() -> SymmetryStateStore.forRun(model.program(),null,Strategy.DFS,null)).getMessage());
        assertEquals("strategy",assertThrows(NullPointerException.class,() -> SymmetryStateStore.forRun(model.program(),null,null,SymmetryBackend.ORDERED)).getMessage());
    }
}
