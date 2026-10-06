/** Exception thrown when program definition loading fails due to invalid format, unknown types, missing parameters, or compatibility issues. */
package dev.samhb.interleave.format.registry;

/**
 * Exception thrown when program definition loading fails due to invalid format,
 * unknown types, missing parameters, or compatibility issues.
 */
public class RegistryException extends RuntimeException {
    /**
     * Creates registry exception from the supplied values.
     * @param message diagnostic explaining the failure
     */
    public RegistryException(String message) {
        super(message);
    }

    /**
     * Creates registry exception from the supplied values.
     * @param message diagnostic explaining the failure
     * @param cause underlying failure
     */
    public RegistryException(String message, Throwable cause) {
        super(message, cause);
    }
}
