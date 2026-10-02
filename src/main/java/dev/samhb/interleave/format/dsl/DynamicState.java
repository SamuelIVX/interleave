package dev.samhb.interleave.format.dsl;

import dev.samhb.interleave.core.SharedState;

import java.io.DataOutput;
import java.io.IOException;
import java.util.Arrays;

/**
 * A {@link SharedState} whose shape comes from a {@link StateDecl} rather than from Java fields.
 *
 * <p>Where a hand-written state class declares its fields, this one holds an {@code Object[]} parallel
 * to {@link StateDecl#fields()} plus a {@code Object[][]} of per-thread locals, so a program read from
 * JSON can be explored without a generated class per declaration.
 *
 * <p><b>Encoding contract.</b> {@link #encodeTo(DataOutput)} writes declared fields in declaration
 * order, each as a type ordinal followed by its value, then the locals grouped by thread id. Two
 * consequences follow, and both are load-bearing:
 *
 * <ul>
 *   <li>Because the type ordinal is written before the value, a field declared {@code BOOL} cannot
 *       alias one declared {@code INT} with the same underlying number.
 *   <li>Because {@code decl} itself is <em>not</em> written, two structurally different declarations
 *       encode identically. That is a tracked encoding gap, not an oversight — see Spec 12.07, and
 *       {@code StateEncodingFidelityTest.trackedGaps_areStillRealGaps} for the check that fails the day
 *       it is closed.
 * </ul>
 *
 * <p>Arrays are written as a length followed by each element. The length prefix is load-bearing and
 * cannot be dropped: fixed-width elements make a <em>single</em> array's length recoverable from the
 * total byte count, but the encoding concatenates all fields, so without prefixes the boundary between
 * two adjacent arrays is invisible. Two declarations of {@code [p=[2], q=[]]} and {@code [p=[], q=[2]]}
 * are distinct states under {@link #equals} that encode identically once the prefix is removed — the
 * lone element simply migrates across the field boundary. See Spec 12.07 R2d.
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

    /**
     * Wraps already-populated storage without copying.
     *
     * <p>Takes ownership rather than copying, so every caller must pass storage it will not reuse. Used
     * only by {@link #deepCopy()}, which builds fresh arrays anyway; a public or general-purpose path
     * through this constructor would alias two states onto one grid and make {@code equals} compare
     * different configurations as equal.
     *
     * @param decl the declaration describing the field and local layout
     * @param threadCount number of threads the locals grid has a row for
     * @param fieldValues per-field storage, parallel to {@code decl.fields()}
     * @param localValues per-thread storage indexed {@code [tid][localIndex]}
     */
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

    /**
     * Resolves a declared field name to its storage index.
     *
     * @param name the field name to resolve
     * @return the index into {@code fieldValues}
     * @throws IllegalArgumentException if no field is declared under that name
     */
    private int indexOfField(String name) {
        for (int i = 0; i < decl.fields().size(); i++) if (decl.fields().get(i).name().equals(name)) return i;
        throw new IllegalArgumentException("Unknown field: " + name);
    }

    /**
     * Resolves a declared local name to its column in every thread's row.
     *
     * <p>Resolves a column rather than a cell, so the result is valid for any thread id.
     *
     * @param name the local name to resolve
     * @return the column index within {@code localValues[tid]}
     * @throws IllegalArgumentException if no local is declared under that name
     */
    private int indexOfLocal(String name) {
        for (int i = 0; i < decl.locals().size(); i++) if (decl.locals().get(i).name().equals(name)) return i;
        throw new IllegalArgumentException("Unknown local: " + name);
    }

    @Override
    /**
     * Returns a copy sharing no mutable structure with this one.
     *
     * <p>Array-valued fields need an element-wise copy. Aliasing them would let the search mutate a
     * configuration already recorded as visited, so two genuinely different configurations would
     * compare equal and pruning would discard real branches.
     *
     * @return an independent deep copy
     */
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
    /**
     * Writes the canonical encoding: declared fields in order, then locals grouped by thread.
     *
     * <p>Every field is written as a type ordinal followed by its value, so an {@code INT} and a
     * {@code BOOL} holding the same number do not alias. Arrays additionally carry a length prefix,
     * because fields are concatenated and an unprefixed array would let its elements blur into the
     * next field. Locals follow in {@code [tid][slot]} order, which preserves which thread holds which
     * value — a commutative summary such as a sum would collapse {@code [1,2]} onto {@code [2,1]}, two
     * configurations {@link #equals} distinguishes.
     *
     * <p><b>This encoding is coarser than {@link #equals} in two known, deliberate ways.</b>
     * {@code equals} compares the declaration and the thread count, and {@code encodeTo} writes neither,
     * so states differing only in those compare unequal yet encode identically. Both omissions are
     * tracked gaps escalated to Specs 09/10 — see the class Javadoc and
     * {@code StateEncodingFidelityTest.trackedGaps_areStillRealGaps}, which fails the day either is
     * closed. Everything else {@code equals} compares is written here, which is the property the
     * {@link dev.samhb.interleave.core.SharedState} encoding contract requires.
     *
     * @param out the sink to write to
     * @throws IOException if the sink fails
     */
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
    /**
     * Compares by declared layout, thread count, field values, and locals.
     *
     * <p>Field values are compared per declared name and locals per {@code [tid][slot]}, using array
     * equality for array fields rather than reference equality. Every field and local that appears
     * here also participates in {@link #encodeTo}, which is what makes a
     * {@link dev.samhb.interleave.core.SharedState} encoding contract satisfiable: no state
     * {@code equals} distinguishes may be omitted from the encoding.
     *
     * @param o the object to compare against
     * @return true if both describe the same configuration
     */
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
    /**
     * Hashes consistently with {@link #equals}.
     *
     * <p>Arrays hash by content, matching the content comparison in {@link #equals}; hashing an array
     * by identity would separate two states the contract requires to be equal.
     *
     * @return a hash consistent with {@link #equals}
     */
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
    /**
     * Renders every declared field and local by name, for failure messages and oracle traces.
     *
     * <p>Not part of any contract — unlike {@link #hashCode()}, this may change freely. It must not be
     * used to key a store: {@link dev.samhb.interleave.search.DfsExplorer} builds an unencoded
     * bookkeeping key by string-concatenating {@code toString()} with the program counters, which is a
     * debug aid rather than an identity.
     *
     * @return a human-readable rendering of the configuration
     */
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
        if (!decl.locals().isEmpty()) {
            if (!decl.fields().isEmpty()) sb.append(", ");
            for (int tid = 0; tid < threadCount; tid++) {
                for (int j = 0; j < decl.locals().size(); j++) {
                    LocalDecl l = decl.locals().get(j);
                    sb.append("t").append(tid).append(".").append(l.name()).append("=").append(localValues[tid][j]);
                    if (tid != threadCount - 1 || j != decl.locals().size() - 1) sb.append(", ");
                }
            }
        }
        sb.append("}");
        return sb.toString();
    }
}
