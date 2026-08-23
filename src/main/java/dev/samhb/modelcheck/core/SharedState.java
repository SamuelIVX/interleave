package dev.samhb.modelcheck.core;

import java.io.DataOutput;
import java.io.IOException;

public interface SharedState {
    SharedState deepCopy();

    void encodeTo(DataOutput out) throws IOException;
}
