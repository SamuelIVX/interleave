/** Shared JSON configuration for public trace, resource-prefix and result values. */
package dev.samhb.interleave;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

/** Package-owned escaping policy; serialization never executes a model or property. */
final class ResultJson {
    /** Thread-safe serializer preserving existing trace string and typed-value behavior. */
    static final Gson GSON = new GsonBuilder().disableHtmlEscaping().create();

    /** Prevents instantiation of this configuration holder. */
    private ResultJson() {}
}
