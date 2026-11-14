/**
 * Fail-closed internal loading diagnostics, distinct from interpreted actor failures.
 */
package dev.samhb.interleave.bytecode;

/**
 * Reports malformed/unsupported input, unavailable metadata, limits and interpreted setup failures.
 */
final class BytecodeLoadException extends IllegalArgumentException {
    /**
     * Stable diagnostic family.
     */
    final String category;
    /**
     * Actual application location, or null for file/class-level diagnostics.
     */
    final BytecodeLocation location;

    /**
     * Constructs an actionable diagnostic without executing or retaining a target throwable.
     * @param category stable diagnostic family
     * @param message problem description
     * @param location real bytecode position when available
     */
    BytecodeLoadException(String category, String message, BytecodeLocation location) {
        super(category + ": " + message + (location == null ? "" : " at " + location));
        this.category = category;
        this.location = location;
    }
}
