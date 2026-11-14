/**
 * Immutable internal resource profile; public bytecode selection remains unavailable until 94C.
 */
package dev.samhb.interleave.bytecode;

/**
 * Independent actor/setup allowances and per-invocation observer fuel.
 * @param actorInstructions actor work allowance
 * @param actorAllocations actor object allowance
 * @param actorHeapCells actor cell allowance
 * @param maxCallDepth maximum frames per execution owner
 * @param maxArrayLength future array length allowance
 * @param maxStringLength future String UTF-16 allowance
 * @param setupInstructions setup work allowance
 * @param setupAllocations setup object allowance
 * @param setupHeapCells setup cell allowance
 * @param checkInstructions observer invocation work allowance
 */
record BytecodeLimits(int actorInstructions, int actorAllocations, int actorHeapCells,
                      int maxCallDepth, int maxArrayLength, int maxStringLength,
                      int setupInstructions, int setupAllocations, int setupHeapCells,
                      int checkInstructions) {
    /**
     * Rejects zero, negative and above-ceiling limits before any target input is processed.
     */
    BytecodeLimits {
        int[] values = {actorInstructions, actorAllocations, actorHeapCells, maxCallDepth,
            maxArrayLength, maxStringLength, setupInstructions, setupAllocations, setupHeapCells, checkInstructions};
        int[] ceilings = {4096,4096,65536,256,4096,4096,65536,4096,65536,16384};
        for (int i = 0; i < values.length; i++) {
            if (values[i] < 1 || values[i] > ceilings[i]) {
                throw new IllegalArgumentException("Invalid bytecode limit at component " + i);
            }
        }
    }

    /**
     * Returns the provisional resource profile for the first bytecode release.
     * @return provisional engineering defaults, subject to the 94B measurement gate
     */
    static BytecodeLimits defaults() {
        return new BytecodeLimits(256,128,8192,32,64,256,4096,128,8192,1024);
    }
}
