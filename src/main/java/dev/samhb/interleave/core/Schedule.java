package dev.samhb.interleave.core;

import java.util.List;

public final class Schedule {
    private final List<Integer> threadIds;

    public Schedule(List<Integer> threadIds) {
        if (threadIds == null) throw new IllegalArgumentException("threadIds must not be null");
        this.threadIds = List.copyOf(threadIds);
    }

    public List<Integer> threadIds() {
        return threadIds;
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < threadIds.size(); i++) {
            if (i > 0) sb.append(" -> ");
            sb.append("t").append(threadIds.get(i));
        }
        return sb.toString();
    }
}
