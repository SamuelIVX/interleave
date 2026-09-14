package dev.samhb.interleave.format.registry;

/**
 * Exception thrown when program definition loading fails due to invalid format,
 * unknown types, missing parameters, or compatibility issues.
 */
public class RegistryException extends RuntimeException {
    public RegistryException(String message) {
        super(message);
    }

    public RegistryException(String message, Throwable cause) {
        super(message, cause);
    }
}