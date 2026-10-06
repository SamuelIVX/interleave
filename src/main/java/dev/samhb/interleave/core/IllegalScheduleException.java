/** Signals an explicit schedule choice that is not enabled. */
package dev.samhb.interleave.core;

/** Signals an explicit schedule choice that is not enabled. */
public class IllegalScheduleException extends RuntimeException {
    /**
     * Creates illegal schedule exception from the supplied values.
     * @param message diagnostic explaining the failure
     */
    public IllegalScheduleException(String message) {
        super(message);
    }
}
