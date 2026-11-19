/** Shared JSON configuration for public trace, resource-prefix and result values. */
package dev.samhb.interleave;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

/** Package-owned escaping policy; serialization never executes a model or property. */
final class ResultJson {
    /** Thread-safe serializer preserving existing trace string and typed-value behavior. */
    static final Gson GSON = new GsonBuilder().disableHtmlEscaping().create();

    /** Escapes isolated UTF-16 surrogates so BYTECODE JSON survives UTF-8 file transport exactly.
     * Paired supplementary characters retain their ordinary representation; legacy output is untouched.
     * @param json serialized BYTECODE text
     * @return transport-safe JSON with identical decoded UTF-16 strings */
    static String bytecodeText(String json) {
        var safe = new StringBuilder(json.length());
        for (int i = 0; i < json.length(); i++) {
            char value = json.charAt(i);
            if (Character.isHighSurrogate(value) && i + 1 < json.length() && Character.isLowSurrogate(json.charAt(i + 1))) {
                safe.append(value).append(json.charAt(++i));
            } else if (Character.isSurrogate(value)) {
                safe.append(String.format(java.util.Locale.ROOT, "\\u%04x", (int) value));
            } else safe.append(value);
        }
        return safe.toString();
    }

    /** Prevents instantiation of this configuration holder. */
    private ResultJson() {}
}
