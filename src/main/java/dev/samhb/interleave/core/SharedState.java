package dev.samhb.interleave.core;

import java.io.DataOutput;
import java.io.IOException;

/**
 * The shared memory a program's threads read and write.
 *
 * <p>Two obligations beyond modelling the data. {@link #deepCopy} exists because a search explores
 * many configurations at once, each needing its own state, and the DFS oracle compares states for
 * equality to prune duplicates -- a copy that shares structure with its original would make two
 * distinct configurations compare equal. {@link #encodeTo} exists because hashing state for the
 * bitstate store needs a stable byte representation, and that representation must agree with
 * {@link Object#equals} or the store will prune states that are genuinely different.
 */
public interface SharedState {

    /**
     * Returns an independent copy that shares no mutable structure with this one.
     *
     * @return a deep copy
     */
    SharedState deepCopy();

    /**
     * Writes a canonical encoding of this state, used for hashing and for exact comparison.
     *
     * <p>The encoding is required in <em>both</em> directions, and the second is the one that is easy
     * to get wrong:
     *
     * <ul>
     *   <li><b>Equal states encode identically</b>, because {@code equals} and {@code hashCode} agree
     *       with the fields written here.
     *   <li><b>Distinct states encode distinctly.</b> An encoding coarser than the state — for instance
     *       one that omits a field {@code equals} treats as identity — merges genuinely different states.
     *       Both stores key on this encoding, so such an omission can cause a real configuration to be
     *       reported as already visited and pruned from the search.
     * </ul>
     *
     * <p>Note the asymmetry that makes the second direction easy to overlook: an omission can only ever
     * <em>preserve</em> the first direction, so the first guarantee is structurally incapable of
     * detecting it. Every field that participates in {@code equals} and {@code hashCode} must therefore
     * appear in {@code encodeTo}. Encoding a field as a faithful projection rather than its full value
     * is acceptable, provided the projection distinguishes every pair of values {@code equals}
     * distinguishes.
     *
     * @param out the sink to write to
     * @throws IOException if the sink fails
     */
    void encodeTo(DataOutput out) throws IOException;
}
