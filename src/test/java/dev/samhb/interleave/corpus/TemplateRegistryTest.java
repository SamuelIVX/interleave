package dev.samhb.interleave.corpus;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class TemplateRegistryTest {
    @Test
    void knownTemplates() {
        assertTrue(TemplateRegistry.ids().contains("lost-update"));
        assertTrue(TemplateRegistry.ids().contains("counter-race"));
        assertNotNull(TemplateRegistry.get("lost-update"));
    }

    @Test
    void unknownThrows() {
        assertThrows(IllegalArgumentException.class, () -> TemplateRegistry.get("nope"));
    }
}
