package dev.samhb.interleave;

/**
 * Thrown when a search is stopped by a caller-supplied resource limit rather than by exhausting
 * the state space.
 *
 * <p>Distinct from a preemption bound being reached, which is a statement about completeness and
 * surfaces as an {@code INCOMPLETE} verdict. This exception means the search was cut short and
 * asserts nothing at all: it is caught by the runner and converted into a partial result with
 * {@code limitExceeded} set.
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
