/** Child-JVM oracles for compiler boundaries and exact test-only logical work assertions. */
package dev.samhb.mutationharness;

import dev.samhb.interleave.format.SourceProgramLoader;
import dev.samhb.interleave.format.registry.RegistryException;
import java.util.List;

/** A process fixture outside PIT's application-test filter. */
public final class CompilerWorkProbe {
    /** Prevents construction of this process entry point. */
    private CompilerWorkProbe() {}

    /** Validates compiler boundaries or an exact injected assertion.
     * Prints {@code BOUNDARIES [64, 64, 1, 0]} or {@code DELIMITER_BOUNDARIES} on boundary success,
     * or {@code BOUND_EXCEEDED}
     * when the requested exact logical assertion fires.
     * @param args non-null command-line arguments containing one supported mode
     * @throws IllegalArgumentException for unsupported modes
     * @throws IllegalStateException if a requested logical bound does not fail
     * @throws AssertionError if a boundary or logical assertion differs from its literal oracle
     */
    public static void main(String[] args) {
        if (args.length != 1) throw new IllegalArgumentException("Expected one compiler probe mode");
        if (args[0].equals("boundaries")) {
            List<Integer> counts = List.of(steps("repeat 64 { atomic {} }"),
                steps("while (true) limit 63 {}"), steps("atomic { repeat 64 { counter = 1; } }"),
                steps("repeat 2147483647 {}"));
            if (!counts.equals(List.of(64, 64, 1, 0))) throw new AssertionError("Wrong boundaries: " + counts);
            try {
                new SourceProgramLoader().load("");
                throw new AssertionError("Empty source must be rejected");
            } catch (RegistryException expected) { /* The empty tokenizer emits only EOF, within its bound. */ }
            try {
                steps("repeat 2147483647 { atomic {} }");
                throw new AssertionError("Oversized work must be rejected");
            } catch (RegistryException expected) { /* Original validation must still reject before expansion. */ }
            System.out.println("BOUNDARIES [64, 64, 1, 0]");
            return;
        }
        if (args[0].equals("delimiter-boundaries")) {
            delimiterBoundaries();
            System.out.println("DELIMITER_BOUNDARIES");
            return;
        }
        String body;
        String assertion;
        switch (args[0]) {
            case "zero-bound" -> {
                body = "repeat 2147483647 {}";
                assertion = "Compiler expansion exceeded 64 unrolling events in one frame";
            }
            case "work-bound" -> {
                body = "repeat 2147483647 { atomic {} }";
                assertion = "Compiler expansion exceeded 64 unrolling events in one frame";
            }
            case "token-bound" -> {
                body = "atomic when (counter == 0) {}";
                assertion = "Compiler tokenization exceeded source units plus EOF";
            }
            case "delimiter-bound" -> {
                body = "";
                assertion = "Compiler delimiter scan exceeded token count plus one";
            }
            default -> throw new IllegalArgumentException("Unknown compiler probe mode");
        }
        try {
            if (args[0].equals("delimiter-bound")) {
                new SourceProgramLoader().load("program probe { shared int counter = 0; thread 0 { atomic { counter = 1");
            } else steps(body);
            throw new IllegalStateException("Expected logical compiler bound");
        } catch (AssertionError failure) {
            if (!assertion.equals(failure.getMessage())) throw failure;
            System.out.println("BOUND_EXCEEDED");
        }
    }

    /** Checks finite original scanner paths without accepting injected assertion failures. */
    private static void delimiterBoundaries() {
        if (steps("atomic when ((counter == 0)) { counter = (1 + (2)); }") != 1)
            throw new AssertionError("Nested expression must compile to one instruction");
        String prefix = "program probe { shared int counter = 0; thread 0 { ";
        for (String tail : List.of("if (true", "atomic { counter = 1", "if (true { } } }",
                "if (true } }", "atomic { counter = 1 { } } } }", "atomic { counter = 1 } } }",
                "atomic { counter = 1); } } }", "if (true]) {} } }")) {
            try {
                new SourceProgramLoader().load(prefix + tail);
                throw new AssertionError("Malformed delimiter must be rejected: " + tail);
            } catch (RegistryException expected) { /* Original syntax errors remain finite. */ }
        }
    }

    /** Compiles a real one-owner source model and returns its actual scheduled instruction count.
     * @param body supported thread-body syntax
     * @return compiled scheduled instruction count
     * @throws RegistryException if the compiler rejects the supplied body
     */
    private static int steps(String body) {
        return new SourceProgramLoader().load("program probe { shared int counter = 0; thread 0 { "
            + body + " } }").program().threads().getFirst().steps().size();
    }
}
