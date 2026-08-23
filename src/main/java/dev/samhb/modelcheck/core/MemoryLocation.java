package dev.samhb.modelcheck.core;

public final class MemoryLocation {
    private final String name;

    private MemoryLocation(String name) {
        this.name = name;
    }

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
