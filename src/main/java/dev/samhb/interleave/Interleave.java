package dev.samhb.interleave;

import dev.samhb.interleave.core.*;
import dev.samhb.interleave.search.*;
import dev.samhb.interleave.por.*;
import dev.samhb.interleave.dpor.*;
import java.util.*;

public final class Interleave {
    private Interleave() {}

    public static Program program(SharedState state, ModelThread... threads) {
        if (state == null) throw new IllegalArgumentException("state must not be null");
        if (threads == null || threads.length == 0) throw new IllegalArgumentException("threads must not be empty");
        List<ModelThread> threadList = Arrays.asList(threads);
        return new Program(state, threadList);
    }

    public static VerificationResult verify(Program program, Strategy strategy) {
        return verify(program, strategy, null);
    }

    public static VerificationResult verify(Program program, Strategy strategy, Invariant invariant) {
        long start = System.currentTimeMillis();
        Runtime runtime = Runtime.getRuntime();
        runtime.gc();
        long memBefore = runtime.totalMemory() - runtime.freeMemory();

        DfsResult result = switch (strategy) {
            case DFS -> new DfsExplorer().explore(program, invariant);
            case STATIC_POR -> new StaticPorExplorer().explore(program, invariant);
            case DPOR -> new DporExplorer().explore(program, invariant);
        };

        long memAfter = runtime.totalMemory() - runtime.freeMemory();
        long wallTime = System.currentTimeMillis() - start;
        long heapDelta = Math.max(0, memAfter - memBefore);

        return VerificationResult.from(result, strategy, wallTime, heapDelta);
    }

    public static Configuration replay(Program program, Trace trace) {
        TraceReplayer replayer = new TraceReplayer();
        return replayer.replay(program, trace);
    }
}
