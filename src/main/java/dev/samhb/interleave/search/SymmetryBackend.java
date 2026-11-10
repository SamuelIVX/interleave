// Certified exact collection choices for run-owned symmetry membership.
package dev.samhb.interleave.search;

import java.util.*;

/**
 * Exact symmetry membership implementations with no caller-supplied equality or comparator.
 * Selection is immutable and safe to share; every checked store owns newly allocated containers.
 * For example, use builder().symmetryReduction(true).symmetryBackend(SymmetryBackend.ORDERED).
 * This selects storage only; it does not change source eligibility, reduction or trace order.
 */
public enum SymmetryBackend {
    /** Hash-based full-key membership; retains the existing default storage behavior. */
    HASHED,
    /** Natural String ordering in tree collections; no speed or memory advantage is promised. */
    ORDERED;

    /** Creates empty run-owned full-key membership with exact String equality.
     * String natural ordering is consistent with equals, so tree and hash membership distinguish
     * the same complete keys; no custom comparator can coarsen that identity.
     * @return independent mutable set; natural ordering for ORDERED
     */
    Set<String> newSet() { return this == ORDERED ? new TreeSet<>() : new HashSet<>(); }

    /** Creates an empty run-owned map keyed by complete immutable orbit strings.
     * @param <V> retained cost or exact sleep-coverage value type
     * @return independent mutable map; natural ordering for ORDERED
     */
    <V> Map<String,V> newMap() { return this == ORDERED ? new TreeMap<>() : new HashMap<>(); }
}
