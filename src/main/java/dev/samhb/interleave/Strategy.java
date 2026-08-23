package dev.samhb.interleave;

import dev.samhb.interleave.dpor.DporExplorer;
import dev.samhb.interleave.por.StaticPorExplorer;
import dev.samhb.interleave.search.DfsExplorer;
import dev.samhb.interleave.search.DfsResult;
import dev.samhb.interleave.core.Program;

public enum Strategy {
    DFS {
        @Override
        public DfsResult explore(Program program) {
            return new DfsExplorer().explore(program);
        }
    },
    STATIC_POR {
        @Override
        public DfsResult explore(Program program) {
            return new StaticPorExplorer().explore(program);
        }
    },
    DPOR {
        @Override
        public DfsResult explore(Program program) {
            return new DporExplorer().explore(program);
        }
    };

    public abstract DfsResult explore(Program program);
}
