/** Isolated special-file probe so a regressed blocking open cannot strand the test worker. */
package dev.samhb.interleave.bytecode;

import java.nio.file.Path;

final class BytecodeInputProbe {
    /** Loads only metadata and reports the rejection category.
     *
     * @param args immutable class directory */
    public static void main(String[] args) {
        try { BytecodeLoader.load(Path.of(args[0]),"Fixture",BytecodeLimits.defaults()); }
        catch(BytecodeLoadException failure) { System.out.println(failure.category); return; }
        throw new AssertionError("Special input accepted");
    }
}
