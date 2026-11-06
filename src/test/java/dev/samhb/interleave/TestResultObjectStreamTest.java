/* Native result streams are build-specific and must not silently erase symmetry identity. */
package dev.samhb.interleave;

import com.google.gson.JsonParser;
import dev.samhb.interleave.format.SourceProgramLoader;
import java.io.*;
import java.net.URLClassLoader;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.Base64;
import javax.tools.ToolProvider;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import static org.junit.jupiter.api.Assertions.*;

class TestResultObjectStreamTest {
    /** An actual old-build stream rejects at the root result descriptor, before field mapping.
     * @throws Exception if reading the controlled fixture fails unexpectedly
     */
    @Test void previousBuildStreamIsExplicitlyIncompatible() throws Exception {
        long oldUid = legacyUid();
        assertNotEquals(oldUid, ObjectStreamClass.lookup(TestResult.class).getSerialVersionUID());
    }

    /** Native round trips preserve complete current data; the old UID gate rejects both modes.
     * @param symmetry whether the actual runner uses checked quotient search
     * @param dir isolated compilation output for the legacy descriptor reader
     * @throws Exception if fixture compilation or stream I/O fails unexpectedly
     */
    @ParameterizedTest @ValueSource(booleans = {false, true})
    void currentRoundTripPreservesModeAndLegacyReaderRejects(boolean symmetry, @TempDir Path dir) throws Exception {
        var model = new SourceProgramLoader().load("program stream { shared int x = 0; thread 0 { atomic { x = x + 1; } }"
            + "thread 1 { atomic { x = x + 1; } } invariant final (x == 2); }");
        var original = InterleaveRunner.builder().symmetryReduction(symmetry)
            .invariant(model.invariant().orElseThrow()).build().run(model.program());
        byte[] bytes = serialize(original);
        TestResult restored;
        try (var input = new ObjectInputStream(new ByteArrayInputStream(bytes))) {
            restored = assertInstanceOf(TestResult.class, input.readObject());
        }
        assertEquals(symmetry, restored.symmetryReduction());
        assertEquals(symmetry ? 3 : 4, restored.statesExplored());
        assertFalse(restored.completedTraces().isEmpty());
        assertEquals(JsonParser.parseString(original.toJson()), JsonParser.parseString(restored.toJson()));
        assertThrows(UnsupportedOperationException.class, () -> restored.completedTraces().clear());
        assertRejectedByLegacyUid(bytes, legacyUid(), dir);
    }

    /** Serializes an actual current result through Java's ordinary object stream protocol.
     * @param result runner-produced result including real trace/snapshot metadata
     * @return complete native stream bytes
     * @throws IOException if the actual object graph is not serializable
     */
    private static byte[] serialize(TestResult result) throws IOException {
        var bytes = new ByteArrayOutputStream();
        try (var output = new ObjectOutputStream(bytes)) { output.writeObject(result); }
        return bytes.toByteArray();
    }

    /** Reads the real base fixture's descriptor and verifies the deliberate root incompatibility.
     * @return old writer's UID from the stream itself, not a reimplemented UID algorithm
     * @throws Exception if the controlled fixture is missing, malformed or unexpectedly compatible
     */
    private static long legacyUid() throws Exception {
        byte[] bytes;
        try (var resource = TestResultObjectStreamTest.class.getResourceAsStream("/serialization/test-result-ac16444.b64")) {
            assertNotNull(resource, "The real ac16444 stream fixture must exist");
            bytes = Base64.getMimeDecoder().decode(resource.readAllBytes());
        }
        ObjectStreamClass[] descriptor = new ObjectStreamClass[1];
        try (var input = new ObjectInputStream(new ByteArrayInputStream(bytes)) {
            /** Captures the actual root descriptor while keeping Java's compatibility checks intact.
             * @return the unmodified descriptor supplied by the native stream
             * @throws IOException if the fixture cannot be read
             * @throws ClassNotFoundException if a serialized type is unavailable
             */
            @Override protected ObjectStreamClass readClassDescriptor() throws IOException, ClassNotFoundException {
                var value = super.readClassDescriptor();
                if (value.getName().equals(TestResult.class.getName())) descriptor[0] = value;
                return value;
            }
        }) {
            var failure = assertThrows(InvalidClassException.class, input::readObject);
            assertEquals(TestResult.class.getName(), failure.classname);
        }
        assertNotNull(descriptor[0]);
        return descriptor[0].getSerialVersionUID();
    }

    /** Exercises the real Java UID gate with an isolated old binary identity, not a full old reader.
     * Field layout is irrelevant because mismatched root UIDs must reject before field mapping.
     * The complete old library reader is separately exercised by the documented fresh-JVM probe.
     * @param bytes actual current ordinary or symmetry stream
     * @param oldUid independently captured real old writer UID
     * @param dir temporary source/class output, never committed as a compiled artifact
     * @throws Exception if compilation, class loading or stream rejection fails unexpectedly
     */
    private static void assertRejectedByLegacyUid(byte[] bytes, long oldUid, Path dir) throws Exception {
        var source = dir.resolve("TestResult.java");
        Files.writeString(source, "package dev.samhb.interleave; public final class TestResult implements java.io.Serializable {"
            + "private static final long serialVersionUID = " + oldUid + "L; }", StandardCharsets.UTF_8);
        var compiler = ToolProvider.getSystemJavaCompiler();
        assertNotNull(compiler, "Tests require the configured JDK, not a JRE");
        var diagnostics = new ByteArrayOutputStream();
        assertEquals(0, compiler.run(null, diagnostics, diagnostics, "-d", dir.toString(), source.toString()),
            diagnostics.toString(StandardCharsets.UTF_8));
        try (var loader = new URLClassLoader(new java.net.URL[]{dir.toUri().toURL()}, ClassLoader.getPlatformClassLoader())) {
            var legacy = Class.forName(TestResult.class.getName(), true, loader);
            try (var input = new ObjectInputStream(new ByteArrayInputStream(bytes)) {
                /** Resolves only the root result against the isolated old binary identity.
                 * @param descriptor actual serialized class descriptor
                 * @return old root class, or the normal unchanged dependency class
                 * @throws IOException if class resolution fails
                 * @throws ClassNotFoundException if a dependency is unavailable
                 */
                @Override protected Class<?> resolveClass(ObjectStreamClass descriptor) throws IOException, ClassNotFoundException {
                    return descriptor.getName().equals(TestResult.class.getName()) ? legacy : super.resolveClass(descriptor);
                }
            }) {
                var failure = assertThrows(InvalidClassException.class, input::readObject);
                assertEquals(TestResult.class.getName(), failure.classname);
            }
        }
    }
}
