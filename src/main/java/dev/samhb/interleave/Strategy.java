package dev.samhb.interleave;

import dev.samhb.interleave.core.Program;
import dev.samhb.interleave.dpor.DporExplorer;
import dev.samhb.interleave.por.StaticPorExplorer;
import dev.samhb.interleave.search.DfsExplorer;
import dev.samhb.interleave.search.DfsResult;
import dev.samhb.interleave.search.Invariant;

public enum Strategy {
    DFS {
        @Override
        public DfsResult explore(Program program) {
            return new DfsExplorer().explore(program);
        }

        @Override
        public DfsResult explore(Program program, Invariant invariant) {
            return new DfsExplorer().explore(program, invariant);
        }
    },
    STATIC_POR {
        @Override
        public DfsResult explore(Program program) {
            return new StaticPorExplorer().explore(program);
        }

        @Override
        public DfsResult explore(Program program, Invariant invariant) {
            return new StaticPorExplorer().explore(program, invariant);
        }
    },
    DPOR {
        @Override
        public DfsResult explore(Program program) {
            return new DporExplorer().explore(program);
        }

        @Override
        public DfsResult explore(Program program, Invariant invariant) {
            return new DporExplorer().explore(program, invariant);
        }
    };

    public abstract DfsResult explore(Program program);

    public DfsResult explore(Program program, Invariant invariant) {
        return explore(program);
    }
}
