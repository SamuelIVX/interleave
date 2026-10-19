/** Immutable original text and indexed line map retained independently of parser state. */
package dev.samhb.interleave.format.dsl;

import dev.samhb.interleave.core.SourceLocation;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/** Provides bounded source slices and logarithmic original-span lookup. */
final class SourceText {
    /** Exact original characters. */
    private final String source;
    /** Artifact-safe file identity. */
    private final String basename;
    /** UTF-16 line starts, with CRLF treated as one line break. */
    private final int[] lineStarts;

    /** Initializes a per-model line index from the original text and diagnostic name.
     * @param source validated original text without normalization
     * @param name valid local path or in-memory marker; only its basename enters artifacts
     */
    SourceText(String source, String name) {
        this.source = source;
        this.basename = name.equals("<source>") ? name : Path.of(name).getFileName().toString();
        List<Integer> starts = new ArrayList<>(); starts.add(0);
        for (int i = 0; i < source.length(); i++) {
            if (source.charAt(i) == '\r') {
                if (i + 1 < source.length() && source.charAt(i + 1) == '\n') i++;
                starts.add(i + 1);
            } else if (source.charAt(i) == '\n') starts.add(i + 1);
        }
        this.lineStarts = starts.stream().mapToInt(Integer::intValue).toArray();
    }

    /** Returns original text without newline normalization.
     * @return exact UTF-16 source characters
     */
    String source() { return source; }

    /** Maps a half-open original UTF-16 range into one-based lines/columns.
     * @param start inclusive original offset
     * @param end exclusive original offset
     * @return artifact-safe span, treating CRLF as one newline
     * @throws IllegalArgumentException if offsets are reversed, negative or beyond the text
     */
    SourceLocation location(int start, int end) {
        if (start < 0 || end < start || end > source.length()) throw new IllegalArgumentException("Invalid source offset");
        int a = lineAt(start), b = lineAt(end);
        return new SourceLocation(basename, a + 1, start - lineStarts[a] + 1,
            b + 1, end - lineStarts[b] + 1);
    }

    /** Locates the containing line without scanning the original source on every trace event.
     * @param offset validated original offset, including EOF
     * @return zero-based containing line index
     */
    private int lineAt(int offset) {
        int found = Arrays.binarySearch(lineStarts, offset);
        return found >= 0 ? found : -found - 2;
    }
}
