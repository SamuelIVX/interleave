package dev.samhb.interleave.report;

/**
 * Enumerates the type of state store used in a benchmark run.
 * <ul>
 *   <li>{@code EXACT} — uses {@link HashingStateStore} for exact, complete results</li>
 *   <li>{@code BITSTATE} — uses {@link BitstateStore} for memory-efficient approximate results</li>
 * </ul>
 */
public enum StoreType {
    EXACT,
    BITSTATE
}