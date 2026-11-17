/**
 * Artifact-relative locations without fabricated source spans.
 */
package dev.samhb.interleave.bytecode;

/**
 * A real application bytecode position, including its overload descriptor.
 * @param owner internal class name
 * @param name method name
 * @param descriptor JVM method descriptor
 * @param bci bytecode index
 */
public record BytecodeLocation(String owner, String name, String descriptor, int bci) implements java.io.Serializable {
    /** Validates the real method identity and nonnegative bytecode index.
     * @throws IllegalArgumentException for empty identities or a negative index
     * @throws NullPointerException for null method identity components */
    public BytecodeLocation {
        java.util.Objects.requireNonNull(owner);java.util.Objects.requireNonNull(name);java.util.Objects.requireNonNull(descriptor);
        if(owner.isEmpty() || name.isEmpty() || descriptor.isEmpty() || bci<0)throw new IllegalArgumentException("Invalid bytecode location");
    }
}
