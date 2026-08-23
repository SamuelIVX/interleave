package dev.samhb.modelcheck.state;

import dev.samhb.modelcheck.core.SharedState;
import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.*;

public final class CanonicalEncoder {
    public byte[] encode(SharedState state) {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        DataOutputStream out = new DataOutputStream(baos);
        try {
            state.encodeTo(out);
            out.flush();
        } catch (IOException e) {
            throw new IllegalStateException("Failed to encode state", e);
        }
        return baos.toByteArray();
    }

    public boolean equals(SharedState a, SharedState b) {
        return Arrays.equals(encode(a), encode(b));
    }

    public int hashCode(SharedState state) {
        return Arrays.hashCode(encode(state));
    }
}
