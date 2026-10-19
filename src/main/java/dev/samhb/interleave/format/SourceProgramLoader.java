/** Bounded UTF-8 source entry point alongside the existing JSON loader. */
package dev.samhb.interleave.format;

import dev.samhb.interleave.bugs.BenchmarkProgram;
import dev.samhb.interleave.format.dsl.SourceCompiler;
import dev.samhb.interleave.format.registry.RegistryException;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/** Stateless reusable loader for finite `.ilv` concurrency models. */
public final class SourceProgramLoader {
    /** Maximum input bytes, checked before unbounded file allocation. */
    public static final int MAX_SOURCE_BYTES = 1_048_576;

    /** Creates a stateless source loader. */
    public SourceProgramLoader() {}

    /**
     * Compiles in-memory source without executing it.
     * @param source complete model text
     * @return validated model
     * @throws RegistryException for syntax, type, name or resource failures
     */
    public BenchmarkProgram load(String source) {
        return compile(source, "<source>");
    }

    /**
     * Reads a bounded strict UTF-8 file and compiles its model.
     * @param path source path, regardless of extension
     * @return validated model
     * @throws RegistryException for unreadable, invalid UTF-8 or invalid source
     */
    public BenchmarkProgram loadFromFile(Path path) {
        try (var input = Files.newInputStream(path)) {
            byte[] bytes = input.readNBytes(MAX_SOURCE_BYTES + 1);
            if (bytes.length > MAX_SOURCE_BYTES) throw new RegistryException(path + ":1:1: limit: Source exceeds 1048576 UTF-8 bytes");
            String source = StandardCharsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(bytes)).toString();
            return compile(source, path.toString());
        } catch (CharacterCodingException e) {
            throw new RegistryException(path + ":1:1: io: Invalid UTF-8", e);
        } catch (IOException e) {
            throw new RegistryException(path + ":1:1: io: Cannot read source", e);
        }
    }

    /** Validates in-memory encoding and delegates each call to an isolated compiler.
     * @param source complete text without encoding/newline normalization
     * @param name local diagnostic path or in-memory marker
     * @return validated immutable benchmark definitions
     * @throws RegistryException for null/malformed Unicode, source over 1 MiB or frontend errors
     */
    private static BenchmarkProgram compile(String source, String name) {
        if (source == null) throw new RegistryException(name + ":1:1: syntax: Source is null");
        if (source.length() > MAX_SOURCE_BYTES) throw new RegistryException(name + ":1:1: limit: Source exceeds 1048576 UTF-8 bytes");
        try {
            int bytes = StandardCharsets.UTF_8.newEncoder().onMalformedInput(CodingErrorAction.REPORT)
                .encode(java.nio.CharBuffer.wrap(source)).remaining();
            if (bytes > MAX_SOURCE_BYTES) throw new RegistryException(name + ":1:1: limit: Source exceeds 1048576 UTF-8 bytes");
        } catch (CharacterCodingException e) {
            throw new RegistryException(name + ":1:1: syntax: Invalid Unicode source", e);
        }
        return new SourceCompiler(source, name).compile();
    }
}
