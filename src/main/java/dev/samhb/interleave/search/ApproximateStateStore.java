/** Common precision diagnostics for approximate visited-state membership. */
package dev.samhb.interleave.search;

/** Positive membership can be false; these diagnostics never establish coverage. */
public interface ApproximateStateStore extends StateStore {
    /** Returns the single-lookup heuristic, not a probability of program correctness.
     * @return estimated false-positive rate in [0, 1]
     */
    double estimatedFalsePositiveRate();
    /** Returns actual occupied positions across the store's membership vectors.
     * @return occupied bit count
     */
    int bitCount();
    /** Returns occupied positions divided by the logical capacity in use.
     * @return density in [0, 1], or zero before marking
     */
    double bitDensity();
    /** Returns the configured preemption capacity; zero does not imply CBS eligibility.
     * @return maximum representable preemption count
     */
    int maxPreemptions();
}
