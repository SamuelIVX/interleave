package dev.samhb.interleave.core;

import java.util.List;

/**
 * An ordered list of thread IDs describing one interleaving.
 *
 * <p>The output of a search: which thread ran, in what order. Replaying a schedule means feeding
 * this sequence back through a program, so it is a first-class result rather than a debugging aid.
 */
public final class Schedule {

    private final List<Integer> threadIds;

    /**
     * Creates a schedule.
     *
     * @param threadIds the thread selected at each position, copied defensively
     * @throws IllegalArgumentException if {@code threadIds} is null
     */
    public Schedule(List<Integer> threadIds) {
        if (threadIds == null) throw new IllegalArgumentException("threadIds must not be null");
        this.threadIds = List.copyOf(threadIds);
    }

    /**
     * Returns the thread IDs in scheduling order.
     *
     * @return an immutable list
     */
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
