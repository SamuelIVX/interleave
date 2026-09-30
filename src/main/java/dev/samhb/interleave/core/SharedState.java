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
     * <p>Two states that are equal must encode to identical bytes. The two properties are
     * maintained together and tested together; a mismatch would make the bitstate store prune real
     * configurations.
     *
     * @param out the sink to write to
     * @throws IOException if the sink fails
     */
    void encodeTo(DataOutput out) throws IOException;
}
