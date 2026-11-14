/** Compiler/oracle support isolated to tests; the production loader never compiles or launches targets. */
package dev.samhb.interleave.bytecode;

import java.nio.file.*;
import java.nio.charset.StandardCharsets;
import java.io.*;
import java.util.*;
import java.util.concurrent.TimeUnit;
import javax.tools.ToolProvider;
import static org.junit.jupiter.api.Assertions.*;

final class BytecodeFixtures {
    /** Test annotation classpath, without relying on Gradle's worker bootstrap classpath. */
    private static final String ANNOTATIONS = classpath(Model.class);


    /** Resolves local class directories or jars through the active launcher's class resources.
     * Child probes use the on-disk classes; they do not claim to execute PIT's in-memory mutations.
     *
     * @param types required test/production classes
     * @return platform-separated runtime roots */
    static String classpath(Class<?>... types) {
        var entries=new LinkedHashSet<String>();
        try {
            for(var type:types) {
                String name=type.getName().replace('.','/')+".class";
                var resource=Objects.requireNonNull(type.getResource("/"+name),"Missing runtime class resource");
                Path entry;
                if(resource.getProtocol().equals("jar")) {
                    var archive=(java.net.JarURLConnection)resource.openConnection();entry=Path.of(archive.getJarFileURL().toURI());
                } else {
                    assertEquals("file",resource.getProtocol(),"Unsupported nonlocal test launcher");
                    entry=Path.of(resource.toURI());for(String ignored:name.split("/"))entry=entry.getParent();
                }
                entries.add(entry.toString());
            }
            return String.join(File.pathSeparator,entries);
        } catch(java.net.URISyntaxException | IOException failure) {throw new AssertionError("Cannot resolve test classpath",failure);}
    }

    /** Compiles one supported/rejected application fixture.
     *
     * @param directory test workspace
     *
     * @param release Java input release
     *
     * @param body fixture body after imports
     *
     * @return class directory
     *
     * @throws IOException for fixture file failures
     */
    static Path compile(Path directory, int release, String body) throws IOException {
        Path sources=directory.resolve("sources"), output=directory.resolve("classes");
        Files.createDirectories(sources); Files.createDirectories(output);
        Path source=sources.resolve("Fixture.java");
        Files.writeString(source,"import dev.samhb.interleave.bytecode.*;\n"+body,StandardCharsets.UTF_8);
        var diagnostics=new ByteArrayOutputStream();
        int result=ToolProvider.getSystemJavaCompiler().run(null,diagnostics,diagnostics,"--release",Integer.toString(release),
            "-g","-classpath",ANNOTATIONS,"-d",output.toString(),source.toString());
        assertEquals(0,result,diagnostics.toString(StandardCharsets.UTF_8)); return output;
    }

    /** Executes a separate JVM solely as the independent sequential oracle.
     *
     * @param directory test workspace
     *
     * @param classes previously compiled application
     *
     * @param statement bounded oracle body
     *
     * @return stripped oracle output
     *
     * @throws Exception for compiler/process/fixture failures
     */
    static String oracle(Path directory, Path classes, String statement) throws Exception {
        Path source=directory.resolve("Oracle.java"), output=directory.resolve("oracle"); Files.createDirectories(output);
        Files.writeString(source,"public class Oracle { public static void main(String[] args) { "+statement+" } }");
        String classpath=classes+File.pathSeparator+ANNOTATIONS;
        assertEquals(0,ToolProvider.getSystemJavaCompiler().run(null,null,null,"--release","26","-classpath",classpath,
            "-d",output.toString(),source.toString()));
        Path capture=directory.resolve("oracle-output.txt");
        Process process=new ProcessBuilder(Path.of(System.getProperty("java.home"),"bin","java").toString(),
            "-cp",output+File.pathSeparator+classpath,"Oracle").redirectErrorStream(true).redirectOutput(capture.toFile()).start();
        try {
            assertTrue(process.waitFor(10,TimeUnit.SECONDS),"Sequential oracle exceeded10 seconds");
            assertEquals(0,process.exitValue(),Files.readString(capture)); return Files.readString(capture).strip();
        } finally { if (process.isAlive()) process.destroyForcibly(); }
    }
    /** Drives the instruction seam with a test-only event ceiling, turning stalled event progress into an assertion.
     *
     * @param program immutable artifact
     * @param parent owned starting state
     * @param owner selected actor
     *
     * @return terminal state */
    static BytecodeState run(BytecodeProgram program,BytecodeState parent,int owner) {
        var state=parent;
        for(int event=0;event<16384;event++) {
            assertFalse(Thread.currentThread().isInterrupted(),"Sequential driver cancelled");
            var result=OpcodeExecutor.step(program,state,owner); state=result.state();
            if(result.kind()==OpcodeExecutor.Kind.TERMINAL) return state;
            assertNotEquals(OpcodeExecutor.Kind.BLOCKED,result.kind(),"Sequential execution blocked");
        }
        throw new AssertionError("Instruction/event protocol did not terminate within the test ceiling");
    }

}
