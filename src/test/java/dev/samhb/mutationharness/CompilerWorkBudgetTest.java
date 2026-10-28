/** Independent class-verifier and child-JVM checks for the PIT-only compiler work assertions. */
package dev.samhb.mutationharness;

import java.lang.classfile.*;
import java.lang.classfile.instruction.BranchInstruction;
import java.lang.classfile.instruction.IncrementInstruction;
import java.lang.classfile.instruction.InvokeInstruction;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import static org.junit.jupiter.api.Assertions.*;

class CompilerWorkBudgetTest {
    /** Classpath-relative name of the actual production compiler. */
    private static final String COMPILER = "dev/samhb/interleave/format/dsl/SourceCompiler.class";
    /** Isolated class replacements and child output. */
    @TempDir Path directory;

    /** Inclusive unrolling, huge empty repeats and invalid empty/oversized sources retain their behavior.
     * @throws Exception if the child JVM cannot execute
     */
    @Test void preservesRealCompilerBoundaries() throws Exception {
        assertEquals("BOUNDARIES [64, 64, 1, 0]", run(null, false, "boundaries").trim());
        assertEquals("BOUNDARIES [64, 64, 1, 0]", run(null, true, "boundaries").trim());
    }

    /** An actual zero-work skip negation fails the logical unrolling oracle.
     * @throws Exception if bytecode or the child JVM cannot be prepared
     */
    @Test void catchesNegatedZeroWorkSkip() throws Exception {
        assertEquals("BOUND_EXCEEDED", run(negateWorkBranch("expand", Opcode.IFNE, Opcode.IFEQ),
            true, "zero-bound").trim());
    }

    /** Bypassing the real instruction cap reaches the same bounded unrolling oracle.
     * @throws Exception if bytecode or the child JVM cannot be prepared
     */
    @Test void catchesBypassedInstructionCap() throws Exception {
        assertEquals("BOUND_EXCEEDED", run(negateWorkBranch("compile", Opcode.IFLE, Opcode.IFGT),
            true, "work-bound").trim());
    }

    /** Real whitespace/operator position regressions must fail the token-count assertion.
     * @param ordinal first whitespace increment or last two-character-operator increment
     * @throws Exception if bytecode or the child JVM cannot be prepared
     */
    @ParameterizedTest @ValueSource(ints = {0, 3})
    void catchesTokenizerPositionRegression(int ordinal) throws Exception {
        int[] increments = {0};
        ClassFile file = classFile();
        byte[] changed = file.transformClass(file.parse(compiler()), ClassTransform.transformingMethodBodies(
            method -> method.methodName().equalsString("tokenize"), (code, element) -> {
                if (element instanceof IncrementInstruction increment) {
                    assertEquals(1, increment.constant());
                    if (increments[0]++ == ordinal) code.iinc(increment.slot(), -1);
                    else code.with(element);
                } else code.with(element);
            }));
        assertEquals(4, increments[0]);
        assertEquals(List.of(), file.verify(changed));
        assertEquals("BOUND_EXCEEDED", run(changed, true, "token-bound").trim());
    }

    /** Single removed checkpoints remain valid on redefinition but reject initial binding drift.
     * @param method one instrumented method
     * @throws Exception if bytecode or the child JVM cannot be prepared
     */
    @ParameterizedTest @ValueSource(strings = {"expand", "tokenize"})
    void verifiesRemovedCheckpointAndRejectsInitialDrift(String method) throws Exception {
        int[] removed = {0};
        ClassFile file = classFile();
        byte[] changed = file.transformClass(file.parse(compiler()), ClassTransform.transformingMethodBodies(
            candidate -> candidate.methodName().equalsString(method), (code, element) -> {
                if (removed[0] == 0 && element instanceof InvokeInstruction invoke
                        && invoke.name().equalsString(method.equals("expand") ? "ancestry" : "add")) {
                    if (method.equals("expand")) code.pop2().pop().aconst_null();
                    else code.pop2().iconst_0();
                    removed[0]++;
                } else code.with(element);
            }));
        assertEquals(1, removed[0]);
        assertThrows(IllegalArgumentException.class,
            () -> CompilerWorkBudget.instrument(changed, false, getClass().getClassLoader()));
        assertEquals(List.of(), file.verify(CompilerWorkBudget.instrument(changed, true, getClass().getClassLoader())));
        AssertionError rejected = assertThrows(AssertionError.class, () -> run(changed, true, "boundaries"));
        assertTrue(rejected.getMessage().contains("ClassFormatError"), rejected.getMessage());
    }

    /** Each required method is independently checked before any transformed definition is returned.
     * @param method one required method to remove
     * @throws Exception if the compiler definition cannot be read
     */
    @ParameterizedTest @ValueSource(strings = {"expand", "tokenize"})
    void rejectsMissingMethod(String method) throws Exception {
        ClassFile file = classFile();
        byte[] changed = file.transformClass(file.parse(compiler()), ClassTransform.dropping(
            element -> element instanceof MethodModel candidate && candidate.methodName().equalsString(method)));
        assertThrows(IllegalArgumentException.class,
            () -> CompilerWorkBudget.instrument(changed, false, getClass().getClassLoader()));
    }

    /** Negates the first work comparison in an actual compiled method, with a checked opcode oracle.
     * @param method method owning the comparison
     * @param expected original comparison opcode
     * @param replacement its logical negation
     * @return verified single-guard mutant bytes
     * @throws Exception if the compiler definition cannot be read
     */
    private byte[] negateWorkBranch(String method, Opcode expected, Opcode replacement) throws Exception {
        int[] changed = {0};
        ClassFile file = classFile();
        byte[] result = file.transformClass(file.parse(compiler()), ClassTransform.transformingMethodBodies(
            candidate -> candidate.methodName().equalsString(method), CodeTransform.ofStateful(() -> new CodeTransform() {
                /** Whether the next branch consumes the first work comparison. */
                private boolean work;
                /** Replaces only that comparison, preserving all surrounding compiler code.
                 * @param code destination body
                 * @param element original element
                 */
                @Override public void accept(CodeBuilder code, CodeElement element) {
                    if (work && element instanceof BranchInstruction branch) {
                        assertEquals(expected, branch.opcode());
                        code.branch(replacement, branch.target());
                        changed[0]++;
                        work = false;
                    } else {
                        code.with(element);
                        if (changed[0] == 0 && element instanceof InvokeInstruction invoke
                                && invoke.name().equalsString("work")) work = true;
                    }
                }
            })));
        assertEquals(1, changed[0]);
        assertEquals(List.of(), file.verify(result));
        return result;
    }

    /** Uses the test/application loader to resolve compiler and Gson types during frame verification.
     * @return class-file service for real application definitions
     */
    private static ClassFile classFile() {
        return ClassFile.of(ClassFile.ClassHierarchyResolverOption.of(
            ClassHierarchyResolver.ofClassLoading(CompilerWorkBudgetTest.class.getClassLoader())));
    }

    /** Reads the actual compiled compiler, never a copied parser algorithm.
     * @return original compiler bytes
     * @throws Exception if the classpath resource cannot be read
     */
    private static byte[] compiler() throws Exception {
        try (var input = CompilerWorkBudgetTest.class.getClassLoader().getResourceAsStream(COMPILER)) {
            assertNotNull(input);
            return input.readAllBytes();
        }
    }

    /** Executes an isolated probe and always reaps its child, including harness failures.
     * @param replacement optional compiler definition placed first on the classpath
     * @param agent whether to activate the mutation harness
     * @param mode independent probe oracle
     * @return successful child output
     * @throws Exception if process setup or output retrieval fails
     */
    private String run(byte[] replacement, boolean agent, String mode) throws Exception {
        Path classes = Files.createTempDirectory(directory, "classes");
        if (replacement != null) {
            Path target = classes.resolve(COMPILER);
            Files.createDirectories(target.getParent());
            Files.write(target, replacement);
        }
        var command = new ArrayList<String>();
        command.add(Path.of(System.getProperty("java.home"), "bin", "java").toString());
        if (agent) command.add("-javaagent:" + System.getProperty("interleave.mutationHarnessJar"));
        command.add("-cp");
        command.add(classes + java.io.File.pathSeparator + System.getProperty("interleave.testRuntimeClasspath"));
        command.add("dev.samhb.mutationharness.CompilerWorkProbe");
        command.add(mode);
        Path output = Files.createTempFile(directory, "compiler", ".log");
        Process child = new ProcessBuilder(command).redirectErrorStream(true).redirectOutput(output.toFile()).start();
        try {
            // Startup safety only; these harness tests are outside PIT's application-test filter.
            assertTrue(child.waitFor(15, TimeUnit.SECONDS), "Compiler probe did not terminate");
            String text = Files.readString(output);
            assertEquals(0, child.exitValue(), text);
            return text;
        } finally {
            if (child.isAlive()) {
                child.destroyForcibly();
                assertTrue(child.waitFor(5, TimeUnit.SECONDS), "Compiler probe could not be reaped");
            }
        }
    }
}
