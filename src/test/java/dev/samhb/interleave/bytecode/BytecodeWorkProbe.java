/** Child-JVM entry point for independently validating PIT-only bytecode progress assertions. */
package dev.samhb.interleave.bytecode;

import java.nio.file.Path;
import java.util.TreeSet;

/** Never shipped in the production artifact or used as a model execution fallback. */
public final class BytecodeWorkProbe {
    /** Prevents construction of this child entry point. */
    private BytecodeWorkProbe() {}
    /** Runs a real fixture through internal search, reporting assertion failures separately from verdicts.
     * @param arguments fixture class directory
     */
    public static void main(String[] arguments) {
        try {
            var program=BytecodeLoader.load(Path.of(arguments[0]),"Fixture",BytecodeLimits.defaults());
            var result=new BytecodeExplorer(program,BytecodeScheduler.Mode.REFERENCE).explore(100000);
            if(!result.exhaustive())throw new IllegalStateException("Probe traversal incomplete");
            var kinds=new TreeSet<String>();for(var witness:result.witnesses())kinds.add(witness.outcome().kind().toString());
            System.out.println("OUTCOMES "+kinds);
        } catch(AssertionError exceeded) {
            if(!exceeded.getMessage().startsWith("Bytecode logical"))throw exceeded;
            System.out.println("BOUND_EXCEEDED");
        }
    }
}
