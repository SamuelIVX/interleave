/** Verifier and isolated-process checks for PIT-only parser progress assertions. */
package dev.samhb.mutationharness;

import java.lang.classfile.*;
import java.lang.classfile.instruction.InvokeInstruction;
import java.lang.classfile.instruction.ReturnInstruction;
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

class ParserWorkBudgetTest {
    /** Resource name of the real production parser. */
    private static final String PARSER = "dev/samhb/interleave/format/dsl/Parser.class";
    /** Isolated replacement classes and process logs. */
    @TempDir Path directory;

    /**
     * Both agent and no-agent processes preserve real grammar and boundary behavior.
     * @throws Exception if the child JVM cannot be prepared or reaped
     */
    @Test void preservesParserBoundaries() throws Exception {
        assertEquals("PARSER_BOUNDARIES", run(null, false, "boundaries").trim());
        assertEquals("PARSER_BOUNDARIES", run(null, true, "boundaries").trim());
    }

    /**
     * Actual loop-consume removal must fail the independent lexical-work assertion.
     * @param method lexical method whose last consume call belongs to its loop
     * @throws Exception if actual class bytes or the child cannot be prepared
     */
    @ParameterizedTest @ValueSource(strings = {"parseIdent", "parseIntLit", "parseWord"})
    void catchesRemovedLoopConsume(String method) throws Exception {
        ClassFile file = classFile();
        int expected = method.equals("parseWord") ? 1 : 2;
        int[] calls = {0};
        byte[] changed = file.transformClass(file.parse(parser()), ClassTransform.transformingMethodBodies(
            candidate -> candidate.methodName().equalsString(method), (code, element) -> {
                if (element instanceof InvokeInstruction invoke && invoke.name().equalsString("consume")
                        && ++calls[0] == expected) code.pop().iconst_0();
                else code.with(element);
            }));
        assertEquals(expected, calls[0]);
        assertEquals(List.of(), file.verify(changed));
        assertEquals(List.of(), file.verify(ParserWorkBudget.instrument(changed, true, getClass().getClassLoader())));
        assertEquals("BOUND_EXCEEDED", run(changed, true, method).trim());
    }

    /**
     * The actual match cursor-addition mutant must fail a per-call monotonicity assertion.
     * @throws Exception if bytecode or the child cannot be prepared
     */
    @Test void catchesBackwardOperatorMatch() throws Exception {
        ClassFile file = classFile();
        int[] changedSites = {0};
        byte[] changed = file.transformClass(file.parse(parser()), ClassTransform.transformingMethodBodies(
            method -> method.methodName().equalsString("match"), (code, element) -> {
                if (element instanceof Instruction instruction && instruction.opcode() == Opcode.IADD) {
                    changedSites[0]++;
                    code.isub();
                } else code.with(element);
            }));
        assertEquals(1, changedSites[0]);
        assertEquals(List.of(), file.verify(changed));
        assertEquals(List.of(), file.verify(ParserWorkBudget.instrument(changed, true, getClass().getClassLoader())));
        assertEquals("BOUND_EXCEEDED", run(changed, true, "match").trim());
    }

    /**
     * One removed peek remains verifier-clean on redefinition but rejects initial binding drift.
     * @param method lexical method losing its first static peek checkpoint
     * @throws Exception if actual bytes or the child cannot be prepared
     */
    @ParameterizedTest @ValueSource(strings = {"parseIdent", "parseIntLit", "parseWord"})
    void allowsOneRemovedCheckpointOnlyOnRedefinition(String method) throws Exception {
        ClassFile file = classFile();
        int[] removed = {0};
        byte[] changed = file.transformClass(file.parse(parser()), ClassTransform.transformingMethodBodies(
            candidate -> candidate.methodName().equalsString(method), (code, element) -> {
                if (removed[0] == 0 && element instanceof InvokeInstruction invoke && invoke.name().equalsString("peek")) {
                    removed[0]++;
                    code.pop().iconst_0();
                } else code.with(element);
            }));
        assertEquals(1, removed[0]);
        assertEquals(List.of(), file.verify(changed));
        assertThrows(IllegalArgumentException.class,
            () -> ParserWorkBudget.instrument(changed, false, getClass().getClassLoader()));
        assertEquals(List.of(), file.verify(ParserWorkBudget.instrument(changed, true, getClass().getClassLoader())));
        AssertionError rejected = assertThrows(AssertionError.class, () -> run(changed, true, "boundaries"));
        assertTrue(rejected.getMessage().contains("ClassFormatError"), rejected.getMessage());
    }

    /**
     * Two omitted checkpoints exceed the single-call-mutant allowance even across lexical methods.
     * @throws Exception if actual parser bytes cannot be read
     */
    @Test void rejectsMultipleRemovedCheckpoints() throws Exception {
        ClassFile file = classFile();
        int[] removed = {0};
        byte[] changed = file.transformClass(file.parse(parser()), ClassTransform.transformingMethodBodies(
            method -> method.methodName().equalsString("parseIdent") || method.methodName().equalsString("parseWord"),
            CodeTransform.ofStateful(() -> new CodeTransform() {
                /** One omitted checkpoint in each selected method. */
                private boolean omitted;
                /**
                 * Removes only the first real peek from this method.
                 * @param code destination body
                 * @param element original bytecode element
                 */
                @Override public void accept(CodeBuilder code, CodeElement element) {
                    if (!omitted && element instanceof InvokeInstruction invoke && invoke.name().equalsString("peek")) {
                        omitted = true;
                        removed[0]++;
                        code.pop().iconst_0();
                    } else code.with(element);
                }
            })));
        assertEquals(2, removed[0]);
        assertEquals(List.of(), file.verify(changed));
        rejects(changed);
    }

    /**
     * Every required method participates in fail-closed shape validation.
     * @param method required method to remove independently
     * @throws Exception if actual parser bytes cannot be read
     */
    @ParameterizedTest @ValueSource(strings = {"parseIdent", "parseIntLit", "parseWord", "match"})
    void rejectsMissingMethod(String method) throws Exception {
        ClassFile file = classFile();
        byte[] changed = file.transformClass(file.parse(parser()), ClassTransform.dropping(
            element -> element instanceof MethodModel candidate && candidate.methodName().equalsString(method)));
        rejects(changed);
    }

    /**
     * A mutable source string or absent cursor invalidates the explicit field binding.
     * @param field input mutability drift or missing pos field
     * @throws Exception if actual parser bytes cannot be read
     */
    @ParameterizedTest @ValueSource(strings = {"input", "pos"})
    void rejectsFieldDrift(String field) throws Exception {
        ClassFile file = classFile();
        byte[] changed = file.transformClass(file.parse(parser()), (builder, element) -> {
            if (element instanceof FieldModel candidate && candidate.fieldName().equalsString(field)) {
                if (field.equals("input")) {
                    assertTrue(candidate.flags().has(java.lang.reflect.AccessFlag.FINAL));
                    builder.withField(candidate.fieldName(), candidate.fieldType(), target -> {
                        candidate.forEach(target::with);
                        target.withFlags(candidate.flags().flagsMask() & ~java.lang.reflect.AccessFlag.FINAL.mask());
                    });
                }
            } else builder.with(element);
        });
        if (field.equals("input")) assertEquals(List.of(), file.verify(changed));
        rejects(changed);
    }

    /**
     * Removing one match return checkpoint is not an allowed single-call mutation.
     * @throws Exception if actual parser bytes cannot be read
     */
    @Test void rejectsMissingMatchReturn() throws Exception {
        ClassFile file = classFile();
        int[] removed = {0};
        byte[] changed = file.transformClass(file.parse(parser()), ClassTransform.transformingMethodBodies(
            method -> method.methodName().equalsString("match"), (code, element) -> {
                if (removed[0] == 0 && element instanceof ReturnInstruction instruction
                        && instruction.opcode() == Opcode.IRETURN) {
                    removed[0]++;
                    code.pop().aconst_null().athrow();
                } else code.with(element);
            }));
        assertEquals(1, removed[0]);
        assertEquals(List.of(), file.verify(changed));
        rejects(changed);
    }

    /**
     * Both initial and replacement definitions must reject unsupported layout drift.
     * @param bytes verifier-independent shape mutation
     */
    private void rejects(byte[] bytes) {
        assertThrows(IllegalArgumentException.class,
            () -> ParserWorkBudget.instrument(bytes, false, getClass().getClassLoader()));
        assertThrows(IllegalArgumentException.class,
            () -> ParserWorkBudget.instrument(bytes, true, getClass().getClassLoader()));
    }

    /**
     * Resolves actual parser/AST types while generating and verifying stack frames.
     * @return class-file service using the application/test loader
     */
    private static ClassFile classFile() {
        return ClassFile.of(ClassFile.ClassHierarchyResolverOption.of(
            ClassHierarchyResolver.ofClassLoading(ParserWorkBudgetTest.class.getClassLoader())));
    }

    /**
     * Reads the actual compiled parser instead of copying its algorithm into a fixture.
     * @return production parser class bytes
     * @throws Exception if its classpath resource cannot be read
     */
    private static byte[] parser() throws Exception {
        try (var input = ParserWorkBudgetTest.class.getClassLoader().getResourceAsStream(PARSER)) {
            assertNotNull(input);
            return input.readAllBytes();
        }
    }

    /**
     * Runs a child and always reaps it; the watchdog cannot earn PIT application kills.
     * @param replacement optional actual parser mutation placed first on the classpath
     * @param agent whether to load the PIT-only instrumentation
     * @param mode independent AST or exact-assertion oracle
     * @return child output after successful termination
     * @throws Exception if process setup or output retrieval fails
     */
    private String run(byte[] replacement, boolean agent, String mode) throws Exception {
        Path classes = Files.createTempDirectory(directory, "classes");
        if (replacement != null) {
            Path target = classes.resolve(PARSER);
            Files.createDirectories(target.getParent());
            Files.write(target, replacement);
        }
        var command = new ArrayList<String>();
        command.add(Path.of(System.getProperty("java.home"), "bin", "java").toString());
        if (agent) command.add("-javaagent:" + System.getProperty("interleave.mutationHarnessJar"));
        command.add("-cp");
        command.add(classes + java.io.File.pathSeparator + System.getProperty("interleave.testRuntimeClasspath"));
        command.add("dev.samhb.mutationharness.ParserWorkProbe");
        command.add(mode);
        Path output = Files.createTempFile(directory, "parser", ".log");
        Process child = new ProcessBuilder(command).redirectErrorStream(true).redirectOutput(output.toFile()).start();
        try {
            // Startup safety only: these harness tests remain outside PIT's application-test filter.
            assertTrue(child.waitFor(15, TimeUnit.SECONDS), "Parser probe did not terminate");
            String text = Files.readString(output);
            assertEquals(0, child.exitValue(), text);
            return text;
        } finally {
            if (child.isAlive()) {
                child.destroyForcibly();
                assertTrue(child.waitFor(5, TimeUnit.SECONDS), "Parser probe could not be reaped");
            }
        }
    }
}
