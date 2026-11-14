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
record BytecodeLocation(String owner, String name, String descriptor, int bci) {}
