/** Extracts immutable state snapshots from any shared state implementation. */
package dev.samhb.interleave.core;

import dev.samhb.interleave.format.dsl.DynamicState;
import dev.samhb.interleave.format.dsl.FieldDecl;
import dev.samhb.interleave.format.dsl.LocalDecl;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Extracts immutable state snapshots from any {@link SharedState} implementation.
 */
public final class StateSnapshotExtractor {

    private StateSnapshotExtractor() {}

    /**
     * Extracts an immutable snapshot from the given shared state.
     *
     * @param state the state to snapshot
     * @return an immutable snapshot of the state's fields and locals
     */
    public static StateSnapshot extract(SharedState state) {
        if (state instanceof DynamicState ds) {
            return extractDynamic(ds);
        } else if (state instanceof PetersonState ps) {
            return extractPeterson(ps);
        } else if (state instanceof CounterState cs) {
            return extractCounter(cs);
        } else if (state instanceof PairState ps) {
            return extractPair(ps);
        } else if (state instanceof DeadlockState ds) {
            return extractDeadlock(ds);
        } else if (state instanceof DclState ds) {
            return extractDcl(ds);
        } else {
            return StateSnapshot.ofFields(Map.of());
        }
    }

    private static StateSnapshot extractDynamic(DynamicState ds) {
        Map<String, Object> fields = new LinkedHashMap<>();
        for (FieldDecl f : ds.decl().fields()) {
            String name = f.name();
            switch (f.type()) {
                case INT -> fields.put(name, ds.getInt(name));
                case BOOL -> fields.put(name, ds.getBool(name));
                case INT_ARRAY -> fields.put(name, ds.getArray(name));
            }
        }

        Map<String, Map<String, Object>> locals = new LinkedHashMap<>();
        for (int tid = 0; tid < ds.threadCount(); tid++) {
            Map<String, Object> threadLocals = new LinkedHashMap<>();
            for (LocalDecl l : ds.decl().locals()) {
                String name = l.name();
                switch (l.type()) {
                    case INT -> threadLocals.put(name, ds.getLocalInt(tid, name));
                    case BOOL -> threadLocals.put(name, ds.getLocalBool(tid, name));
                    case INT_ARRAY -> threadLocals.put(name, ds.getLocalArray(tid, name));
                }
            }
            if (!threadLocals.isEmpty()) {
                locals.put(String.valueOf(tid), threadLocals);
            }
        }

        return new StateSnapshot(fields, locals);
    }

    private static StateSnapshot extractPeterson(PetersonState ps) {
        Map<String, Object> fields = new LinkedHashMap<>();
        boolean[] flags = new boolean[ps.threadCount()];
        for (int i = 0; i < ps.threadCount(); i++) {
            flags[i] = ps.flag(i);
        }
        fields.put("flag", flags);
        fields.put("turn", ps.turn());
        fields.put("inCriticalSection", ps.inCriticalSection());
        return StateSnapshot.ofFields(fields);
    }

    private static StateSnapshot extractCounter(CounterState cs) {
        Map<String, Object> fields = new LinkedHashMap<>();
        fields.put("counter", cs.counter());
        fields.put("control", cs.control());

        Map<String, Map<String, Object>> locals = new LinkedHashMap<>();
        for (int i = 0; i < cs.threadCount(); i++) {
            locals.put(String.valueOf(i), Map.of("register", cs.getRegister(i)));
        }
        return new StateSnapshot(fields, locals);
    }

    private static StateSnapshot extractPair(PairState ps) {
        Map<String, Object> fields = new LinkedHashMap<>();
        fields.put("high", ps.high());
        fields.put("low", ps.low());
        fields.put("control", ps.control());
        fields.put("observedHigh", ps.observedHigh());
        fields.put("observedLow", ps.observedLow());
        fields.put("hasObservation", ps.hasObservation());
        return StateSnapshot.ofFields(fields);
    }

    private static StateSnapshot extractDeadlock(DeadlockState ds) {
        Map<String, Object> fields = new LinkedHashMap<>();
        boolean[] flags = new boolean[ds.threadCount()];
        for (int i = 0; i < ds.threadCount(); i++) {
            flags[i] = ds.flag(i);
        }
        fields.put("flag", flags);
        fields.put("control", ds.control());
        return StateSnapshot.ofFields(fields);
    }

    private static StateSnapshot extractDcl(DclState ds) {
        Map<String, Object> fields = new LinkedHashMap<>();
        fields.put("initialized", ds.initialized());
        fields.put("instance", ds.instance() == null ? "null" : String.valueOf(ds.instance()));
        fields.put("locked", ds.locked());
        fields.put("lockOwner", ds.lockOwner());
        fields.put("control", ds.control());
        fields.put("observedInstance", ds.observedInstance() == null ? "null" : String.valueOf(ds.observedInstance()));
        return StateSnapshot.ofFields(fields);
    }
}
