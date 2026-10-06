/** Encodes shared-state values and supplies immutable configuration keys for stores and explorers. */
package dev.samhb.interleave.state;

import dev.samhb.interleave.core.SharedState;
import dev.samhb.interleave.core.Configuration;
import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.Serializable;
import java.util.*;

/** Stateless canonical encoding shared by exact identity and probabilistic hash prefilters. */
public final class CanonicalEncoder implements Serializable {
    /**
     * Encodes the state through its canonical-value contract.
     *
     * @param state the value to encode; must not be mutated during this call
     * @return a fresh byte array containing the canonical state payload
     * @throws IllegalStateException if the state cannot be written
     */
    public byte[] encode(SharedState state) {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        DataOutputStream out = new DataOutputStream(baos);
        try {
            state.encodeTo(out);
            out.flush();
        } catch (IOException e) {
            throw new IllegalStateException("Failed to encode state", e);
        }
        return baos.toByteArray();
    }

    /**
     * Hashes the canonical payload for use as a prefilter, not proof of equality.
     *
     * @param state the value to hash; must not be mutated during this call
     * @return a hash that may collide for unequal values
     * @throws IllegalStateException if the state cannot be written
     */
    public int hashCode(SharedState state) {
        return Arrays.hashCode(encode(state));
    }

    /**
     * Returns an immutable value key for a search position within one program.
     *
     * <p>The key covers the state type, canonical shared-state bytes and ordered counters, never diagnostic
     * text. Each call reads the current state; an earlier returned string remains a snapshot after
     * that state changes. Program definitions and derived configuration metadata are not identity.
     * Treat the string as opaque rather than a stable serialization format.
     * The caller must not mutate the held state during encoding.
     *
     * <pre>{@code
     * String key = encoder.configurationKey(config);
     * visited.putIfAbsent(key, config);
     * }</pre>
     *
     * @param config the configuration whose current value is encoded
     * @return an immutable search-position key
     * @throws IllegalStateException if the shared state cannot be encoded
     */
    public String configurationKey(Configuration config) {
        return config.state().getClass().getName() + "|"
            + Base64.getEncoder().encodeToString(encode(config.state()))
            + "|" + config.programCounters();
    }

    /**
     * Returns the value key extended with the scheduling context used by bounded search.
     *
     * <p>Preemption cost is deliberately excluded: the store compares costs for the same position
     * and last thread rather than treating each cost as a different state.
     *
     * @param config the configuration whose current value is encoded
     * @param lastThreadId the last scheduled thread, or {@code -1} before any thread ran
     * @return an immutable scheduling-position key
     * @throws IllegalStateException if the shared state cannot be encoded
     */
    public String configurationKey(Configuration config, int lastThreadId) {
        return configurationKey(config) + "|" + lastThreadId;
    }
}
