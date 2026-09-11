package dev.samhb.interleave;

public final class LimitExceededException extends RuntimeException {
    public LimitExceededException(String message) {
        super(message);
    }
}