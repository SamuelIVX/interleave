package dev.samhb.interleave.core;

/**
 * A named cell in shared memory.
 *
 * <p>The unit that read and write sets are expressed in, and therefore the granularity at which
 * two steps are judged to conflict. Identity is by name, so two locations constructed separately
 * with the same name are the same location.
 */
public final class MemoryLocation {

    private final String name;

    private MemoryLocation(String name) {
        this.name = name;
    }

    /**
     * Returns the location with the given name.
     *
     * @param name the location's name, non-blank
     * @return the location
     * @throws IllegalArgumentException if {@code name} is null or blank
     */
    public static MemoryLocation of(String name) {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("MemoryLocation name must not be blank");
        }
        return new MemoryLocation(name);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof MemoryLocation that)) return false;
        return name.equals(that.name);
    }

    @Override
    public int hashCode() {
        return name.hashCode();
    }

    @Override
    public String toString() {
        return name;
    }
}
