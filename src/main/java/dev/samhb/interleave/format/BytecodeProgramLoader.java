/** Public directory-only loader for the bounded sequentially consistent bytecode profile. */
package dev.samhb.interleave.format;

import dev.samhb.interleave.bytecode.*;
import java.nio.file.Path;

/** Parses and validates frozen class bytes, interpreting setup without reflectively loading target classes.
 * Compile the example externally first, then use the same frozen program for execution and replay:
 * <pre>{@code
 * var program = BytecodeProgramLoader.load(java.nio.file.Path.of("build/bytecode-examples"),
 *     "examples.bytecode.LostUpdate", dev.samhb.interleave.bytecode.BytecodeLimits.defaults());
 * var result = dev.samhb.interleave.InterleaveRunner.builder().build().runBytecode(program);
 * var saved = result.failingTraces().getFirst().toJson();
 * var record = dev.samhb.interleave.TraceRecord.fromJson(saved);
 * var replayed = new dev.samhb.interleave.search.TraceReplayer().replayBytecode(program, record);
 * var rows = new dev.samhb.interleave.report.BenchmarkHarness().runBytecode(program);
 * }</pre>
 */
public final class BytecodeProgramLoader {
    /** Prevents construction of this static loading facade. */
    private BytecodeProgramLoader() {}

    /** Loads one fixed-actor model using the provisional defaults reviewed against94B fixtures.
     * Inputs are non-preview Java17–26 class files in one immutable regular-file directory classpath.
     * No compilation, jar discovery or target-code host fallback occurs.
     * See the class example for execution and replay; omitting limits selects {@link BytecodeLimits#defaults()}.
     * @param classDirectory class-file root, immutable for the duration of loading
     * @param modelClass selected binary fixture name
     * @return immutable initialized program, safe to reuse independently
     * @throws BytecodeLoadException for invalid input/metadata, unsupported behavior or interpreted setup failure */
    public static BytecodeProgram load(Path classDirectory,String modelClass){return load(classDirectory,modelClass,BytecodeLimits.defaults());}

    /** Loads one model with immutable per-owner allowances and bounded metadata-only resolution.
     * See the class example for a complete compiled-example execution and replay.
     * @param classDirectory immutable regular-file classpath root
     * @param modelClass selected binary fixture name
     * @param limits validated execution limits
     * @return frozen initialized program
     * @throws BytecodeLoadException for null/invalid input, unsupported behavior, bounds or setup faults */
    public static BytecodeProgram load(Path classDirectory,String modelClass,BytecodeLimits limits){return BytecodeRuntime.load(classDirectory,modelClass,limits);}
}
