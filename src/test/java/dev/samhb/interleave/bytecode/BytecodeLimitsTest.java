/** Internal execution-limit boundaries; no public bytecode interface is enabled by this milestone. */
package dev.samhb.interleave.bytecode;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class BytecodeLimitsTest {
    /** Defaults keep setup and actors separately bounded and reject invalid values eagerly. */
    @Test void defaultsAndInclusiveCeilings() {
        var limits = BytecodeLimits.defaults();
        assertEquals(256, limits.actorInstructions());
        assertEquals(4096, limits.setupInstructions());
        assertEquals(128, limits.setupAllocations());
        assertEquals(1024, limits.checkInstructions());
        assertDoesNotThrow(() -> new BytecodeLimits(4096,4096,65536,256,4096,4096,65536,4096,65536,16384));
        assertThrows(IllegalArgumentException.class,
            () -> new BytecodeLimits(0,128,8192,32,64,256,4096,128,8192,1024));
        assertThrows(IllegalArgumentException.class,
            () -> new BytecodeLimits(4097,128,8192,32,64,256,4096,128,8192,1024));
    }
    /** Every component independently enforces inclusive positive limits, including unused future resources. */
    @Test void eachComponentHasItsOwnHardCeiling() {
        int[] baseline={256,128,8192,32,64,256,4096,128,8192,1024};
        int[] ceilings={4096,4096,65536,256,4096,4096,65536,4096,65536,16384};
        for(int slot=0;slot<baseline.length;slot++) {
            int[] values=baseline.clone(); values[slot]=1; int[] minimum=values; assertDoesNotThrow(()->profile(minimum));
            values=baseline.clone(); values[slot]=ceilings[slot]; int[] maximum=values; assertDoesNotThrow(()->profile(maximum));
            for(int rejected:new int[]{0,-1,ceilings[slot]+1,Integer.MAX_VALUE}) {
                int[] invalid=baseline.clone(); invalid[slot]=rejected;
                assertThrows(IllegalArgumentException.class,()->profile(invalid));
            }
        }
    }

    /** Constructs from a test-owned vector; it does not derive expected limits from production.
     *
     * @param values ten selected components
     * @return tested profile */
    private static BytecodeLimits profile(int[] values) {
        return new BytecodeLimits(values[0],values[1],values[2],values[3],values[4],values[5],values[6],values[7],values[8],values[9]);
    }
}
