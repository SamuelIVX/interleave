/** Inclusive UTF-8 byte limits and path-bearing I/O diagnostics at the source entry point. */
package dev.samhb.interleave.format;

import dev.samhb.interleave.format.registry.RegistryException;
import java.io.IOException;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

class SourceLoaderContractTest {
    private final SourceProgramLoader loader = new SourceProgramLoader();

    /** The inclusive ASCII boundary must compile rather than merely reach a syntax error. */
    @Test void acceptsExactlyOneMebibyteOfAscii() {
        String source = model("x".repeat(1_048_519));
        assertEquals(1_048_576, source.length());
        assertEquals("size", loader.load(source).name());
    }

    /** UTF-8 bytes, rather than Java character count, decide both sides of the boundary. */
    @Test void enforcesEncodedByteLimitForOtherwiseValidMultibyteSource() {
        String comment = "é".repeat(524_259) + "x";
        String source = model(comment);
        assertEquals(524_317, source.length());
        assertEquals(1_048_576, source.getBytes(StandardCharsets.UTF_8).length);
        assertEquals("size", loader.load(source).name());
        RegistryException failure = assertThrows(RegistryException.class,
            () -> loader.load(model(comment + "x")));
        assertEquals("<source>:1:1: limit: Source exceeds 1048576 UTF-8 bytes", failure.getMessage());
    }

    /**
     * Bounded file reading accepts the exact cap and preserves the supplied path above it.
     * @param directory isolated temporary directory for the source file
     * @throws IOException if the fixture cannot be written or measured
     */
    @Test void enforcesInclusiveFileByteLimitRegardlessOfExtension(@TempDir Path directory) throws IOException {
        Path file = directory.resolve("source.data");
        String source = model("x".repeat(1_048_519));
        Files.writeString(file, source, StandardCharsets.UTF_8);
        assertEquals(1_048_576L, Files.size(file));
        assertEquals("size", loader.loadFromFile(file).name());
        Files.writeString(file, source + " ", StandardCharsets.UTF_8);
        RegistryException failure = assertThrows(RegistryException.class, () -> loader.loadFromFile(file));
        assertEquals(file + ":1:1: limit: Source exceeds 1048576 UTF-8 bytes", failure.getMessage());
    }

    /**
     * Malformed bytes cannot disappear inside comments; decoding retains its diagnostic cause.
     * @param directory isolated temporary directory for the malformed source file
     * @throws IOException if the fixture cannot be written
     */
    @Test void reportsInvalidUtf8WithTheSuppliedPath(@TempDir Path directory) throws IOException {
        Path file = directory.resolve("invalid.ilv");
        byte[] bytes = model("x").getBytes(StandardCharsets.UTF_8);
        bytes[17] = (byte) 0xc3;
        Files.write(file, bytes);
        RegistryException failure = assertThrows(RegistryException.class, () -> loader.loadFromFile(file));
        assertEquals(file + ":1:1: io: Invalid UTF-8", failure.getMessage());
        assertInstanceOf(CharacterCodingException.class, failure.getCause());
    }

    /**
     * An absent file is an I/O failure with its original cause, rather than a parser failure.
     * @param directory isolated temporary directory in which the requested file does not exist
     */
    @Test void reportsUnreadableSourceWithTheSuppliedPath(@TempDir Path directory) {
        Path file = directory.resolve("missing.ilv");
        RegistryException failure = assertThrows(RegistryException.class, () -> loader.loadFromFile(file));
        assertEquals(file + ":1:1: io: Cannot read source", failure.getMessage());
        assertInstanceOf(IOException.class, failure.getCause());
    }

    /** A unique absent relative file retains its supplied path rather than an absolute rewrite. */
    @Test void reportsRelativeUnreadableSourceWithTheSuppliedPath() {
        Path file = Path.of(".", "missing-" + UUID.randomUUID() + ".ilv");
        assertFalse(file.isAbsolute());
        assertFalse(Files.exists(file));
        RegistryException failure = assertThrows(RegistryException.class, () -> loader.loadFromFile(file));
        assertEquals(file + ":1:1: io: Cannot read source", failure.getMessage());
        assertInstanceOf(IOException.class, failure.getCause());
    }

    /**
     * Wraps padding in a valid zero-action model without changing executable work.
     * @param comment padding without comment delimiters
     * @return source with a 57-byte ASCII envelope around the comment
     */
    private static String model(String comment) {
        return "program size { /*" + comment + "*/ shared int counter = 0; thread 0 {} }";
    }
}
