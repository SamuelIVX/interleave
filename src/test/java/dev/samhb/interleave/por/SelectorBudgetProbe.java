/** Child-JVM fixture for verifying the PIT-only selector agent against genuine source models. */
package dev.samhb.interleave.por;

import dev.samhb.interleave.format.SourceProgramLoader;
import java.util.List;
import java.util.Set;

/** Process entry point, not a PIT test or a production API. */
public final class SelectorBudgetProbe {
    /** Prevents construction of this process entry point. */
    private SelectorBudgetProbe() {}

    /** Checks a real selection or the exact injected logical-bound assertion.
     * Prints {@code SELECTION [2]} on successful {@code selection}, or {@code BOUND_EXCEEDED}
     * when the expected logical assertion fires in {@code bound} mode.
     * @param args non-null command-line arguments containing one mode: selection or bound
     * @throws IllegalArgumentException if the arguments do not contain exactly one supported mode
     * @throws IllegalStateException if bound mode completes without exceeding the logical bound
     * @throws AssertionError if the selection is wrong or an unexpected assertion occurs
     */
    public static void main(String[] args) {
        boolean bound = args.length == 1 && args[0].equals("bound");
        if (!bound && !(args.length == 1 && args[0].equals("selection"))) {
            throw new IllegalArgumentException("Expected selection or bound");
        }
        var program = new SourceProgramLoader().load("""
            program probe { shared int x = 0; shared int z = 0;
              thread 0 { atomic when (x == 0) {} }
              thread 1 { atomic { x = 1; } }
              thread 2 { atomic { z = 1; } }
            }
            """).program();
        try {
            var result = new PropertyPersistentSetComputer(program.threads(), Set.of(),
                new IndependenceRelation()).compute(program.initialConfiguration());
            if (bound) throw new IllegalStateException("Expected logical owner bound to fail");
            if (!result.equals(List.of(2))) throw new AssertionError("Wrong selection: " + result);
            System.out.println("SELECTION [2]");
        } catch (AssertionError failure) {
            if (!bound || !"Selector expanded more owners than the finite program contains"
                    .equals(failure.getMessage())) throw failure;
            System.out.println("BOUND_EXCEEDED");
        }
    }
}
