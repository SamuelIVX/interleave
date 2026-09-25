package dev.samhb.interleave.format.dsl;

import dev.samhb.interleave.core.SharedState;

import java.io.DataOutput;
import java.io.IOException;
import java.util.Arrays;

/**
 * Declarative shared state with deterministic canonical encoding.
 */
public final class DynamicState implements SharedState {
    private final StateDecl decl;
    private final int threadCount;
    private final Object[] fieldValues; // per field index: Integer, Boolean, or int[]
    private final Object[][] localValues; // [tid][localIdx]: Integer or Boolean

    /**
     * Creates state with initial values from declaration.
     *
     * @param decl state declaration
     * @param threadCount number of threads
     */
    public DynamicState(StateDecl decl, int threadCount) {
        this.decl = decl;
        this.threadCount = threadCount;
        this.fieldValues = new Object[decl.fields().size()];
        for (int i = 0; i < decl.fields().size(); i++) {
            FieldDecl f = decl.fields().get(i);
            switch (f.type()) {
                case INT -> fieldValues[i] = f.intInit();
                case BOOL -> fieldValues[i] = f.boolInit();
                case INT_ARRAY -> fieldValues[i] = Arrays.copyOf(f.arrayInit(), f.arrayInit().length);
            }
        }
        this.localValues = new Object[threadCount][decl.locals().size()];
        for (int tid = 0; tid < threadCount; tid++) {
            for (int j = 0; j < decl.locals().size(); j++) {
                LocalDecl l = decl.locals().get(j);
                localValues[tid][j] = l.type() == FieldType.INT ? l.intInit() : l.boolInit();
            }
        }
    }

    private DynamicState(StateDecl decl, int threadCount, Object[] fieldValues, Object[][] localValues) {
        this.decl = decl;
        this.threadCount = threadCount;
        this.fieldValues = fieldValues;
        this.localValues = localValues;
    }

    /**
     * Returns declaration.
     *
     * @return decl
     */
    public StateDecl decl() { return decl; }

    /**
     * Returns thread count.
     *
     * @return thread count
     */
    public int threadCount() { return threadCount; }

    /** @return int field value */
    public int getInt(String name) {
        int idx = indexOfField(name);
        return (Integer) fieldValues[idx];
    }

    /** @param value int value */
    public void setInt(String name, int value) {
        int idx = indexOfField(name);
        fieldValues[idx] = value;
    }

    /** @return bool field value */
    public boolean getBool(String name) {
        int idx = indexOfField(name);
        return (Boolean) fieldValues[idx];
    }

    /** @param value bool value */
    public void setBool(String name, boolean value) {
        int idx = indexOfField(name);
        fieldValues[idx] = value;
    }

    /**
     * Returns copy of array field.
     *
     * @param name field name
     * @return copy of array
     */
    public int[] getArray(String name) {
        int idx = indexOfField(name);
        int[] arr = (int[]) fieldValues[idx];
        return Arrays.copyOf(arr, arr.length);
    }

    /**
     * Returns direct array reference for internal use (no copy).
     *
     * @param name field name
     * @return array reference
     */
    int[] getArrayRef(String name) {
        int idx = indexOfField(name);
        return (int[]) fieldValues[idx];
    }

    /**
     * Sets array element.
     *
     * @param name array name
     * @param index element index
     * @param value value
     */
    public void setArrayElement(String name, int index, int value) {
        int[] arr = getArrayRef(name);
        arr[index] = value;
    }

    /** @return local int value */
    public int getLocalInt(int tid, String name) {
        int idx = indexOfLocal(name);
        return (Integer) localValues[tid][idx];
    }

    /** @param value local int */
    public void setLocalInt(int tid, String name, int value) {
        int idx = indexOfLocal(name);
        localValues[tid][idx] = value;
    }

    /** @return local bool value */
    public boolean getLocalBool(int tid, String name) {
        int idx = indexOfLocal(name);
        return (Boolean) localValues[tid][idx];
    }

    /** @param value local bool */
    public void setLocalBool(int tid, String name, boolean value) {
        int idx = indexOfLocal(name);
        localValues[tid][idx] = value;
    }

    private int indexOfField(String name) {
        for (int i = 0; i < decl.fields().size(); i++) if (decl.fields().get(i).name().equals(name)) return i;
        throw new IllegalArgumentException("Unknown field: " + name);
    }

    private int indexOfLocal(String name) {
        for (int i = 0; i < decl.locals().size(); i++) if (decl.locals().get(i).name().equals(name)) return i;
        throw new IllegalArgumentException("Unknown local: " + name);
    }

    @Override
    public SharedState deepCopy() {
        Object[] fieldCopy = new Object[fieldValues.length];
        for (int i = 0; i < fieldValues.length; i++) {
            Object v = fieldValues[i];
            if (v instanceof int[] arr) fieldCopy[i] = Arrays.copyOf(arr, arr.length);
            else fieldCopy[i] = v;
        }
        Object[][] localCopy = new Object[threadCount][decl.locals().size()];
        for (int tid = 0; tid < threadCount; tid++) {
            localCopy[tid] = Arrays.copyOf(localValues[tid], localValues[tid].length);
        }
        return new DynamicState(decl, threadCount, fieldCopy, localCopy);
    }

    @Override
    public void encodeTo(DataOutput out) throws IOException {
        // fields in declaration order: type ordinal, then value
        for (int i = 0; i < decl.fields().size(); i++) {
            FieldDecl f = decl.fields().get(i);
            out.writeInt(f.type().ordinal());
            switch (f.type()) {
                case INT -> out.writeInt((Integer) fieldValues[i]);
                case BOOL -> out.writeBoolean((Boolean) fieldValues[i]);
                case INT_ARRAY -> {
                    int[] arr = (int[]) fieldValues[i];
                    out.writeInt(arr.length);
                    for (int v : arr) out.writeInt(v);
                }
            }
        }
        // locals per tid
        for (int tid = 0; tid < threadCount; tid++) {
            for (int j = 0; j < decl.locals().size(); j++) {
                LocalDecl l = decl.locals().get(j);
                if (l.type() == FieldType.INT) out.writeInt((Integer) localValues[tid][j]);
                else out.writeBoolean((Boolean) localValues[tid][j]);
            }
        }
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof DynamicState that)) return false;
        if (threadCount != that.threadCount) return false;
        if (!decl.equals(that.decl)) return false;
        // compare field values
        for (int i = 0; i < fieldValues.length; i++) {
            Object a = fieldValues[i];
            Object b = that.fieldValues[i];
            if (a instanceof int[] arrA && b instanceof int[] arrB) {
                if (!Arrays.equals(arrA, arrB)) return false;
            } else if (!a.equals(b)) return false;
        }
        for (int tid = 0; tid < threadCount; tid++) {
            if (!Arrays.equals(localValues[tid], that.localValues[tid])) {
                // need deep equals for possibly int[]? locals are scalar only, so direct equals
                boolean eq = true;
                for (int j = 0; j < localValues[tid].length; j++) if (!localValues[tid][j].equals(that.localValues[tid][j])) eq = false;
                if (!eq) return false;
            }
        }
        return true;
    }

    @Override
    public int hashCode() {
        int h = decl.hashCode() * 31 + threadCount;
        for (Object v : fieldValues) {
            if (v instanceof int[] arr) h = h * 31 + Arrays.hashCode(arr);
            else h = h * 31 + v.hashCode();
        }
        for (Object[] row : localValues) h = h * 31 + Arrays.hashCode(row);
        return h;
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder("DynamicState{");
        for (int i = 0; i < decl.fields().size(); i++) {
            FieldDecl f = decl.fields().get(i);
            sb.append(f.name()).append("=");
            Object v = fieldValues[i];
            if (v instanceof int[] arr) sb.append(Arrays.toString(arr));
            else sb.append(v);
            if (i < decl.fields().size() - 1) sb.append(", ");
        }
        sb.append("}");
        return sb.toString();
    }
}
