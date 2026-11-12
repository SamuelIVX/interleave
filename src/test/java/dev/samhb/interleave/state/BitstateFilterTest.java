/** Independent public bit/probe oracles for the shared membership kernel. */
package dev.samhb.interleave.state;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import static org.junit.jupiter.api.Assertions.*;

class BitstateFilterTest {
    /** Union cardinalities derived by signed-32-bit arithmetic and binary-string rotation in Python.
     * @param first first hashed key
     * @param second second hashed key
     * @param expected literal number of occupied positions
     */
    @ParameterizedTest
    @CsvSource({"0,1,6", "0,-1,8", "0,-2147483648,8", "0,2147483647,7", "-2147483648,2147483647,7"})
    void preservesSignedOverflowAndProbeUnion(int first, int second, int expected) {
        var filter = new BitstateFilter(17, 4);
        assertFalse(filter.contains(first)); assertEquals(0, filter.bitCount());
        filter.mark(first); assertEquals(4, filter.bitCount()); assertTrue(filter.contains(first));
        filter.mark(second); assertEquals(expected, filter.bitCount());
        assertTrue(filter.contains(first)); assertTrue(filter.contains(second));
        filter.mark(first); assertEquals(expected, filter.bitCount());
        filter.clear(); assertEquals(0, filter.bitCount()); assertFalse(filter.contains(first));
        assertFalse(filter.contains(second));
    }

    /** Saturation permits false positives while a fresh independent filter remains empty. */
    @Test
    void saturationAndIndependenceAreExplicit() {
        var filter = new BitstateFilter(1, 4);
        var other = new BitstateFilter(1, 4);
        filter.mark(123); assertTrue(filter.contains(-456)); assertEquals(1, filter.bitCount());
        assertFalse(other.contains(123)); assertEquals(0, other.bitCount());
        filter.clear(); assertFalse(filter.contains(123));
        assertThrows(IllegalArgumentException.class, () -> new BitstateFilter(0, 1));
        assertThrows(IllegalArgumentException.class, () -> new BitstateFilter(1, 0));
        assertThrows(IllegalArgumentException.class, () -> new BitstateFilter(-1, 1));
        assertThrows(IllegalArgumentException.class, () -> new BitstateFilter(1, -1));
    }
}
