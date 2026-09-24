package dev.samhb.interleave.corpus;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import org.junit.jupiter.api.Test;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class CorpusGeneratorTest {

    @Test
    void configValidation() {
        assertThrows(IllegalArgumentException.class, () -> GeneratorConfig.builder("lost-update").count(0).build());
        assertThrows(IllegalArgumentException.class, () -> GeneratorConfig.builder("lost-update").maxStepsPerThread(0).build());
        assertThrows(IllegalArgumentException.class, () -> GeneratorConfig.builder("lost-update").maxStepsPerThread(21).build());
        assertThrows(IllegalArgumentException.class, () -> GeneratorConfig.builder("lost-update").threadCount(5).build());
        assertThrows(IllegalArgumentException.class, () -> GeneratorConfig.builder("lost-update").count(10_001).build());
        assertThrows(IllegalArgumentException.class, () -> GeneratorConfig.builder("").build());
    }

    @Test
    void generatesExactCount() {
        GeneratorConfig cfg = GeneratorConfig.builder("lost-update").seed(42).count(5).maxStepsPerThread(5).build();
        CorpusGenerator gen = new CorpusGenerator();
        List<CorpusResult> results = gen.generate(cfg);
        assertEquals(5, results.size());
    }

    @Test
    void determinismSameSeed() {
        GeneratorConfig cfg = GeneratorConfig.builder("counter-race").seed(123).count(3).maxStepsPerThread(4).build();
        CorpusGenerator gen = new CorpusGenerator();
        List<CorpusEntry> a = gen.generateEntries(cfg);
        List<CorpusEntry> b = gen.generateEntries(cfg);
        assertEquals(a.size(), b.size());
        for (int i = 0; i < a.size(); i++) {
            assertEquals(a.get(i).totalSteps, b.get(i).totalSteps);
            assertEquals(a.get(i).expectedVerdict, b.get(i).expectedVerdict);
            assertEquals(a.get(i).threadCount, b.get(i).threadCount);
        }
    }

    @Test
    void differentSeedProducesDifferent() {
        GeneratorConfig cfg1 = GeneratorConfig.builder("counter-race").seed(1).count(5).build();
        GeneratorConfig cfg2 = GeneratorConfig.builder("counter-race").seed(99).count(5).build();
        CorpusGenerator gen = new CorpusGenerator();
        List<CorpusEntry> a = gen.generateEntries(cfg1);
        List<CorpusEntry> b = gen.generateEntries(cfg2);
        // at least one differs
        boolean anyDiff = false;
        for (int i = 0; i < a.size(); i++) if (a.get(i).totalSteps != b.get(i).totalSteps) anyDiff = true;
        assertTrue(anyDiff, "different seeds should produce different totalSteps");
    }

    @Test
    void boundedSteps() {
        GeneratorConfig cfg = GeneratorConfig.builder("lost-update").seed(7).count(10).maxStepsPerThread(3).build();
        CorpusGenerator gen = new CorpusGenerator();
        List<CorpusResult> results = gen.generate(cfg);
        for (CorpusResult r : results) {
            for (var t : r.program().threads()) assertTrue(t.steps().size() <= 3);
        }
    }

    @Test
    void oracleAndTruncated() {
        GeneratorConfig cfg = GeneratorConfig.builder("lost-update").seed(42).count(2).maxStepsPerThread(5).maxStates(1).build();
        CorpusGenerator gen = new CorpusGenerator();
        List<CorpusResult> results = gen.generate(cfg);
        for (CorpusResult r : results) {
            assertEquals("TRUNCATED", r.expectedVerdict());
            assertTrue(r.truncated());
        }
        GeneratorConfig cfg2 = GeneratorConfig.builder("lost-update").seed(42).count(2).maxStates(10000).build();
        List<CorpusResult> results2 = new CorpusGenerator().generate(cfg2);
        for (CorpusResult r : results2) assertFalse(r.truncated());
    }

    @Test
    void jsonRoundTrip() {
        GeneratorConfig cfg = GeneratorConfig.builder("lost-update").seed(42).count(2).build();
        List<CorpusEntry> entries = new CorpusGenerator().generateEntries(cfg);
        Gson gson = new GsonBuilder().create();
        String json = gson.toJson(entries);
        CorpusEntry[] decoded = gson.fromJson(json, CorpusEntry[].class);
        assertEquals(entries.size(), decoded.length);
        assertEquals(entries.get(0).expectedVerdict, decoded[0].expectedVerdict);
    }

    @Test
    void threadCountRespected() {
        GeneratorConfig cfg = GeneratorConfig.builder("counter-race").seed(42).count(3).threadCount(3).build();
        List<CorpusResult> results = new CorpusGenerator().generate(cfg);
        for (CorpusResult r : results) assertEquals(3, r.program().threadCount());
    }
}
