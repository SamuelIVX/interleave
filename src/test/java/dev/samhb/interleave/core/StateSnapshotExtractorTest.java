package dev.samhb.interleave.core;

import dev.samhb.interleave.format.dsl.DynamicState;
import dev.samhb.interleave.format.dsl.FieldDecl;
import dev.samhb.interleave.format.dsl.LocalDecl;
import dev.samhb.interleave.format.dsl.StateDecl;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class StateSnapshotExtractorTest {

    @Test
    void extractDynamic_capturesFieldsAndLocals() {
        StateDecl decl = new StateDecl(
            List.of(
                FieldDecl.ofInt("x", 42),
                FieldDecl.ofBool("flag", true),
                FieldDecl.ofArray("arr", new int[]{1, 2, 3})
            ),
            List.of(
                LocalDecl.ofInt("locI", 10),
                LocalDecl.ofBool("locB", false),
                LocalDecl.ofArray("locA", new int[]{9, 8})
            )
        );
        DynamicState ds = new DynamicState(decl, 2);
        ds.setInt("x", 99);
        ds.setLocalInt(1, "locI", 77);

        StateSnapshot snapshot = StateSnapshotExtractor.extract(ds);

        assertEquals(99, snapshot.fields().get("x"));
        assertEquals(true, snapshot.fields().get("flag"));
        assertArrayEquals(new int[]{1, 2, 3}, (int[]) snapshot.fields().get("arr"));

        Map<String, Object> t0Locals = snapshot.locals().get("0");
        assertNotNull(t0Locals);
        assertEquals(10, t0Locals.get("locI"));
        assertEquals(false, t0Locals.get("locB"));
        assertArrayEquals(new int[]{9, 8}, (int[]) t0Locals.get("locA"));

        Map<String, Object> t1Locals = snapshot.locals().get("1");
        assertNotNull(t1Locals);
        assertEquals(77, t1Locals.get("locI"));

        // Deep immutability verification: mutating state after extraction does not alter snapshot
        ds.setInt("x", 1000);
        ds.setArrayElement("arr", 0, 999);
        ds.setLocalInt(1, "locI", 1234);
        assertEquals(99, snapshot.fields().get("x"));
        assertArrayEquals(new int[]{1, 2, 3}, (int[]) snapshot.fields().get("arr"));
        assertEquals(77, snapshot.locals().get("1").get("locI"));

        // Modifying input array after passing to snapshot constructor does not affect snapshot
        int[] inputArr = new int[]{10, 20};
        StateSnapshot sManual = StateSnapshot.ofFields(Map.of("data", inputArr));
        inputArr[0] = 999;
        assertArrayEquals(new int[]{10, 20}, (int[]) sManual.fields().get("data"));
    }

    @Test
    void extractPeterson_capturesFlagsAndTurn() {
        PetersonState ps = PetersonState.of(new boolean[]{true, false, true}, 1);
        ps.setFlag(1, true);

        StateSnapshot snapshot = StateSnapshotExtractor.extract(ps);

        assertArrayEquals(new boolean[]{true, true, true}, (boolean[]) snapshot.fields().get("flag"));
        assertEquals(1, snapshot.fields().get("turn"));
        assertEquals(-1, snapshot.fields().get("inCriticalSection"));
        assertTrue(snapshot.locals().isEmpty());

        // Immutability: modifying ps does not modify snapshot
        ps.setFlag(0, false);
        assertArrayEquals(new boolean[]{true, true, true}, (boolean[]) snapshot.fields().get("flag"));
    }

    @Test
    void extractCounter_capturesCounterAndRegisters() {
        CounterState cs = CounterState.of(5, 2);
        cs.setControl(true);
        cs.setRegister(0, 10);
        cs.setRegister(1, 20);

        StateSnapshot snapshot = StateSnapshotExtractor.extract(cs);

        assertEquals(5, snapshot.fields().get("counter"));
        assertEquals(true, snapshot.fields().get("control"));

        assertEquals(10, snapshot.locals().get("0").get("register"));
        assertEquals(20, snapshot.locals().get("1").get("register"));
    }

    @Test
    void extractPair_capturesHighLowAndObservation() {
        PairState ps = PairState.of(10, 20, true);
        ps.recordObservation(10, 19);

        StateSnapshot snapshot = StateSnapshotExtractor.extract(ps);

        assertEquals(10, snapshot.fields().get("high"));
        assertEquals(20, snapshot.fields().get("low"));
        assertEquals(true, snapshot.fields().get("control"));
        assertEquals(10, snapshot.fields().get("observedHigh"));
        assertEquals(19, snapshot.fields().get("observedLow"));
        assertEquals(true, snapshot.fields().get("hasObservation"));
        assertTrue(snapshot.locals().isEmpty());
    }

    @Test
    void extractDeadlock_capturesFlagsAndControl() {
        DeadlockState ds = DeadlockState.of(new boolean[]{true, false});
        ds.setControl(true);

        StateSnapshot snapshot = StateSnapshotExtractor.extract(ds);

        assertArrayEquals(new boolean[]{true, false}, (boolean[]) snapshot.fields().get("flag"));
        assertEquals(true, snapshot.fields().get("control"));
        assertTrue(snapshot.locals().isEmpty());
    }

    @Test
    void extractDcl_capturesInstanceAndLockOwner() {
        DclState ds = DclState.of(true);
        ds.setInstance("myInstance");
        ds.setObservedInstance("myInstance");

        StateSnapshot snapshot = StateSnapshotExtractor.extract(ds);

        assertEquals(true, snapshot.fields().get("initialized"));
        assertEquals("myInstance", snapshot.fields().get("instance"));
        assertEquals("myInstance", snapshot.fields().get("observedInstance"));
        assertEquals(false, snapshot.fields().get("locked"));
        assertEquals(-1, snapshot.fields().get("lockOwner"));
        assertEquals(false, snapshot.fields().get("control"));
        assertTrue(snapshot.locals().isEmpty());
    }

    @Test
    void snapshotDeepCopy_handlesNullAndEmpty() {
        StateSnapshot s1 = new StateSnapshot(null, null);
        assertTrue(s1.fields().isEmpty());
        assertTrue(s1.locals().isEmpty());

        StateSnapshot s2 = StateSnapshot.ofFields(Map.of("a", 1));
        assertEquals(1, s2.fields().get("a"));
        assertTrue(s2.locals().isEmpty());
    }

    @Test
    void snapshotAccessors_returnDefensiveCopiesOfArrays() {
        boolean[] flags = new boolean[]{false, false};
        int[] arr = new int[]{10, 20};
        Map<String, Object> fields = Map.of("flag", flags, "arr", arr);
        Map<String, Map<String, Object>> locals = Map.of("0", Map.of("localArr", new int[]{99}));

        StateSnapshot snapshot = new StateSnapshot(fields, locals);

        // Mutate array returned by fields()
        boolean[] returnedFlags = (boolean[]) snapshot.fields().get("flag");
        returnedFlags[0] = true;
        int[] returnedArr = (int[]) snapshot.fields().get("arr");
        returnedArr[0] = 999;

        // Mutate array returned by locals()
        int[] returnedLocalArr = (int[]) snapshot.locals().get("0").get("localArr");
        returnedLocalArr[0] = -1;

        // Verify snapshot internal state is uncorrupted
        boolean[] freshFlags = (boolean[]) snapshot.fields().get("flag");
        assertFalse(freshFlags[0], "Mutating returned flags must not mutate snapshot internal state");

        int[] freshArr = (int[]) snapshot.fields().get("arr");
        assertEquals(10, freshArr[0], "Mutating returned int array must not mutate snapshot internal state");

        int[] freshLocalArr = (int[]) snapshot.locals().get("0").get("localArr");
        assertEquals(99, freshLocalArr[0], "Mutating returned local array must not mutate snapshot internal state");
    }
}
