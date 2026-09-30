package dev.samhb.interleave;

/**
 * Thrown when a search is stopped by a caller-supplied resource limit rather than by exhausting
 * the state space.
 *
 * <p>Distinct from a preemption bound being reached, which is a statement about completeness and
 * surfaces as an {@code INCOMPLETE} verdict. This exception means exploration stopped before the
 * state space was exhausted; the runner catches it and returns the traces collected so far with
 * {@code limitExceeded} set. A violation recorded <em>before</em> the limit was hit therefore
 * survives in {@code failingTraces} and must still be reported. The result asserts nothing about
 * the part of the space that was never reached -- not nothing at all.
 */
public final class LimitExceededException extends RuntimeException {

    /**
     * Creates the exception.
     *
     * @param message human-readable description of which limit was hit and at what count
     */
    public LimitExceededException(String message) {
        super(message);
    }
}
