/** Conservative global/private instruction counts at the inclusive 64-action boundary. */
package dev.samhb.interleave.format;

import dev.samhb.interleave.format.registry.RegistryException;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import static org.junit.jupiter.api.Assertions.*;

class SourceInstructionWorkContractTest {
    private final SourceProgramLoader loader = new SourceProgramLoader();

    /**
     * Both work consumers count unchosen arms and all loop checks/body copies before expansion.
     * @param scope global or private work domain
     * @param control conditional or bounded-while fixture
     * @param globalSteps literal compiled global count at the accepted boundary
     * @param diagnostic independent literal category/message/source position for the rejected input
     */
    @ParameterizedTest
    @CsvSource(delimiter = '|', textBlock = """
        global  | if    | 64 | <source>:3:1: limit: Expanded thread instructions exceed 64
        global  | while | 64 | <source>:3:1: limit: Expanded thread instructions exceed 64
        private | if    | 1  | <source>:4:1: limit: Atomic internal instructions exceed 64
        private | while | 1  | <source>:4:1: limit: Atomic internal instructions exceed 64
        """)
    void bothConsumersEnforceTheCompleteInstructionBudget(String scope, String control, int globalSteps, String diagnostic) {
        boolean privateBody = scope.equals("private");
        var accepted = assertDoesNotThrow(() -> loader.load(source(privateBody, control, 0)));
        assertEquals(globalSteps, accepted.program().threads().getFirst().steps().size());
        RegistryException failure = assertThrows(RegistryException.class,
            () -> loader.load(source(privateBody, control, 1)));
        assertEquals(diagnostic, failure.getMessage());
    }

    /**
     * Places the owner at 3:1 and, for private work, its enclosing atomic keyword at 4:1.
     * @param privateBody whether control work lives inside one atomic action
     * @param control if or while shape
     * @param extra one extra body statement for the 65-instruction rejection case, otherwise zero
     * @return complete typed source within every independent syntax/expression limit
     */
    private static String source(boolean privateBody, String control, int extra) {
        String statement = privateBody ? "counter = 0; " : "atomic {} ";
        String body = switch (control) {
            case "if" -> "if (true) { " + statement.repeat(31) + "} else { " + statement.repeat(32 + extra) + "}";
            case "while" -> "while (true) limit 1 { " + statement.repeat(62 + extra) + "}";
            default -> throw new IllegalArgumentException("Unknown fixture control: " + control);
        };
        return "program steps {\nshared int counter = 0;\nthread 0 {\n"
            + (privateBody ? "atomic {\n" : "") + body + "\n" + (privateBody ? "}\n" : "") + "}\n}";
    }
}
