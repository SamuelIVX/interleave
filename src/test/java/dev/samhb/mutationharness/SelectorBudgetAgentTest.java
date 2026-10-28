/** Process checks of real selector instrumentation; kept outside PIT's application test filter. */
package dev.samhb.mutationharness;

import java.lang.classfile.*;
import java.lang.classfile.instruction.BranchInstruction;
import java.lang.classfile.instruction.InvokeInstruction;
import java.lang.constant.ClassDesc;
import java.lang.constant.MethodTypeDesc;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static java.lang.constant.ConstantDescs.*;
import static org.junit.jupiter.api.Assertions.*;

class SelectorBudgetAgentTest {
    /** Classpath-relative name of the actual selector definition used by the application. */
    private static final String SELECTOR = "dev/samhb/interleave/por/PropertyPersistentSetComputer.class";
    /** Independent process output for a supported source selection. */
    private static final String SELECTION = "SELECTION [2]";
    /** Isolated class definitions and child-process output. */
    @TempDir Path directory;

    /** The original selector returns the same literal selection with and without the agent.
     * @throws Exception if the child process cannot execute
     */
    @Test void preservesRealSelection() throws Exception {
        assertEquals(SELECTION, run(null, false, "selection").trim());
        assertEquals(SELECTION, run(null, true, "selection").trim());
    }

    /** The actual seen-skip negation fails its finite work assertion, without a time-based oracle.
     * @throws Exception if the class definition or child process cannot be prepared
     */
    @Test void detectsActualNegatedSeenSkip() throws Exception {
        int[] changed = {0};
        ClassFile file = ClassFile.of();
        byte[] mutated = file.transformClass(file.parse(selector()), ClassTransform.transformingMethodBodies(
            method -> method.methodName().equalsString("component"),
            CodeTransform.ofStateful(() -> new CodeTransform() {
                /** Whether the next branch consumes the seen-owner membership read. */
                private boolean seenRead;
                /** Negates only the branch immediately following the boolean-array read.
                 * @param code destination body
                 * @param element original element
                 */
                @Override public void accept(CodeBuilder code, CodeElement element) {
                    if (seenRead && element instanceof BranchInstruction branch) {
                        assertEquals(Opcode.IFNE, branch.opcode());
                        code.branch(Opcode.IFEQ, branch.target());
                        seenRead = false;
                        changed[0]++;
                    } else {
                        code.with(element);
                        if (element instanceof Instruction instruction && instruction.opcode() == Opcode.BALOAD) {
                            seenRead = true;
                        }
                    }
                }
            })));
        assertEquals(1, changed[0]);
        assertEquals("BOUND_EXCEEDED", run(mutated, true, "bound").trim());
    }

    /** Missing method binding is rejected rather than silently yielding an unbounded selector.
     * @throws Exception if the original class cannot be read
     */
    @Test void rejectsMissingMethod() throws Exception {
        ClassFile file = ClassFile.of();
        byte[] changed = file.transformClass(file.parse(selector()), ClassTransform.dropping(
            element -> element instanceof MethodModel method && method.methodName().equalsString("component")));
        assertThrows(IllegalArgumentException.class, () -> SelectorBudgetAgent.instrument(changed, false));
    }

    /** A changed expansion call is rejected both by the transformer and by the actual agent JVM.
     * @throws Exception if the class definition or child process cannot be prepared
     */
    @Test void rejectsMissingCheckpointAtClassLoad() throws Exception {
        ClassFile file = ClassFile.of();
        byte[] changed = file.transformClass(file.parse(selector()), ClassTransform.transformingMethodBodies(
            method -> method.methodName().equalsString("component"), (code, element) -> {
                if (element instanceof InvokeInstruction invoke && invoke.owner().asInternalName().equals("java/util/List")
                        && invoke.name().equalsString("add")) {
                    code.invokeinterface(ClassDesc.of("java.util.List"), "contains",
                        MethodTypeDesc.of(CD_boolean, CD_Object));
                } else code.with(element);
            }));
        assertThrows(IllegalArgumentException.class, () -> SelectorBudgetAgent.instrument(changed, false));
        AssertionError rejected = assertThrows(AssertionError.class, () -> run(changed, true, "selection"));
        assertTrue(rejected.getMessage().contains("ClassFormatError"), rejected.getMessage());
    }

    /** PIT's single List.add-removal mutation remains executable, rather than becoming NON_VIABLE.
     * @throws Exception if the original class cannot be read
     */
    @Test void acceptsRemovedCheckpointOnlyDuringRedefinition() throws Exception {
        ClassFile file = ClassFile.of();
        byte[] removed = file.transformClass(file.parse(selector()), ClassTransform.transformingMethodBodies(
            method -> method.methodName().equalsString("component"), (code, element) -> {
                if (element instanceof InvokeInstruction invoke && invoke.owner().asInternalName().equals("java/util/List")
                        && invoke.name().equalsString("add")) {
                    code.pop2().iconst_0();
                } else code.with(element);
            }));
        assertThrows(IllegalArgumentException.class, () -> SelectorBudgetAgent.instrument(removed, false));
        byte[] instrumented = SelectorBudgetAgent.instrument(removed, true);
        assertEquals(List.of(), file.verify(instrumented));
    }

    /** Reads the real compiled selector rather than duplicating its algorithm.
     * @return original application class bytes
     * @throws Exception if the classpath resource is unavailable
     */
    private static byte[] selector() throws Exception {
        try (var input = SelectorBudgetAgentTest.class.getClassLoader().getResourceAsStream(SELECTOR)) {
            assertNotNull(input);
            return input.readAllBytes();
        }
    }

    /** Runs a real child JVM and always reaps it, including on harness regressions.
     * @param replacement optional selector bytes placed first on the child classpath
     * @param agent whether to install the test-only agent
     * @param mode independent selection or logical-bound oracle
     * @return child output after successful execution
     * @throws Exception if process setup or output retrieval fails
     */
    private String run(byte[] replacement, boolean agent, String mode) throws Exception {
        Path classes = Files.createTempDirectory(directory, "classes");
        if (replacement != null) {
            Path target = classes.resolve(SELECTOR);
            Files.createDirectories(target.getParent());
            Files.write(target, replacement);
        }
        var command = new ArrayList<String>();
        command.add(Path.of(System.getProperty("java.home"), "bin", "java").toString());
        if (agent) command.add("-javaagent:" + System.getProperty("interleave.mutationHarnessJar"));
        command.add("-cp");
        command.add(classes + java.io.File.pathSeparator + System.getProperty("interleave.testRuntimeClasspath"));
        command.add("dev.samhb.interleave.por.SelectorBudgetProbe");
        command.add(mode);
        Path output = Files.createTempFile(directory, "probe", ".log");
        Process child = new ProcessBuilder(command).redirectErrorStream(true).redirectOutput(output.toFile()).start();
        try {
            // Safety watchdog for broken harness startup, not the selector mutation's killing oracle.
            assertTrue(child.waitFor(15, TimeUnit.SECONDS), "Probe JVM did not terminate");
            String text = Files.readString(output);
            assertEquals(0, child.exitValue(), text);
            return text;
        } finally {
            if (child.isAlive()) {
                child.destroyForcibly();
                assertTrue(child.waitFor(5, TimeUnit.SECONDS), "Probe JVM could not be reaped");
            }
        }
    }
}
