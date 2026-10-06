/** Describes exact versus probabilistic visited-position storage used in benchmark results. */
package dev.samhb.interleave.report;

import dev.samhb.interleave.state.BitstateStore;
import dev.samhb.interleave.state.HashingStateStore;

/**
 * Enumerates the type of state store used in a benchmark run.
 * <ul>
 *   <li>{@code EXACT} — uses {@link HashingStateStore} for exact deduplication without false-positive pruning; search bounds may still limit completeness</li>
 *   <li>{@code BITSTATE} — uses {@link BitstateStore} for memory-efficient approximate results</li>
 * </ul>
 */
public enum StoreType {
    /** Exact deduplication; this choice alone does not make a bounded search exhaustive. */
    EXACT,
    /** Bitstate. */
    BITSTATE
}
