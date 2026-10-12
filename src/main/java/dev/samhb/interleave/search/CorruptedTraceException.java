/** Signals a recorded trace outcome that diverges from actual step execution. */
package dev.samhb.interleave.search;

/**
 * Signals that replaying a recorded step produced an execution outcome that disagrees
 * with the outcome recorded in the trace.
 */
public class CorruptedTraceException extends RuntimeException {
    /**
     * Creates corrupted trace exception with diagnostic message.
     * @param message diagnostic explaining the outcome mismatch
     */
    public CorruptedTraceException(String message) {
        super(message);
    }
}
