/** Validated, half-open source span used only for diagnostics. */
package dev.samhb.interleave.core;

import java.io.Serializable;
import java.util.Objects;

/**
 * A half-open span with one-based UTF-16 columns.
 * @param file diagnostic basename
 * @param line start line
 * @param column start column
 * @param endLine end line
 * @param endColumn exclusive end column
 */
public record SourceLocation(String file, int line, int column, int endLine, int endColumn)
        implements Serializable {
    /** Rejects malformed spans before they enter artifacts. */
    public SourceLocation {
        Objects.requireNonNull(file, "file");
        if (line < 1 || column < 1 || endLine < line || endColumn < 1
                || (endLine == line && endColumn < column)) {
            throw new IllegalArgumentException("Invalid source span");
        }
    }
}
