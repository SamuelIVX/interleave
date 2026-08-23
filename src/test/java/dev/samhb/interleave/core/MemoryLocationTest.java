package dev.samhb.interleave.core;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class MemoryLocationTest {

    @Test
    void memoryLocationEqualityAndHashContract() {
        MemoryLocation a = MemoryLocation.of("x");
        MemoryLocation b = MemoryLocation.of("x");
        MemoryLocation c = MemoryLocation.of("y");

        assertEquals(a, b);
        assertEquals(a.hashCode(), b.hashCode());
        assertNotEquals(a, c);
    }
}
