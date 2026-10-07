/** Initial-state snapshots and mutation isolation between program runs. */
package dev.samhb.interleave.core;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;

/**
 * Pins the aliasing property {@link Program} documents for itself.
 *
 * <p>{@code SharedState} implementations are mutable, and {@link Configuration#initial} consults
 * the supplied state to decide which threads start enabled before it takes its own copy. A
 * {@code Program} that retained the caller's state would let a later mutation change which threads
 * a run starts with, contradicting the class Javadoc. These fail if that defensive copy is removed.
 */
class ProgramImmutabilityTest {

    @Test
    void constructor_snapshotsInitialState_soLaterMutationCannotChangeTheRun() {
        PetersonState initial = PetersonState.of(false, false, 0);
        Program program = new Program(initial, List.of(
                new ModelThread(0, List.of()),
                new ModelThread(1, List.of())));

        // Mutate the caller's state AFTER the program is built. Without the constructor's deep
        // copy this is the object Configuration.initial deep-copies, so the mutation would land.
        initial.setTurn(1);
        initial.setFlag(0, true);

        Configuration config = program.initialConfiguration();

        assertEquals(0, ((PetersonState) config.state()).turn(),
                "initialConfiguration must reflect the state captured at construction");
        assertEquals(false, ((PetersonState) config.state()).flag(0),
                "caller mutation after construction must not reach a run");
    }

    @Test
    void initialConfiguration_doesNotShareStructureWithTheCallersState() {
        PetersonState initial = PetersonState.of(false, false, 0);
        Program program = new Program(initial, List.of(
                new ModelThread(0, List.of()),
                new ModelThread(1, List.of())));

        Configuration first = program.initialConfiguration();
        Configuration second = program.initialConfiguration();

        assertNotSame(first.state(), second.state(),
                "each call must hand back an independent state, not a shared one");
        ((PetersonState) first.state()).setFlag(0, true);
        ((PetersonState) first.state()).setTurn(1);
        assertEquals(false, ((PetersonState) second.state()).flag(0));
        assertEquals(0, ((PetersonState) second.state()).turn());
        Configuration third = program.initialConfiguration();
        assertEquals(false, ((PetersonState) third.state()).flag(0));
        assertEquals(0, ((PetersonState) third.state()).turn());
    }
}
