package dev.samhb.interleave.corpus;

import dev.samhb.interleave.corpus.templates.CounterRaceTemplate;
import dev.samhb.interleave.corpus.templates.LostUpdateTemplate;
import java.util.*;

public final class TemplateRegistry {
    private static final Map<String, CorpusTemplate> REGISTRY = new LinkedHashMap<>();

    static {
        register(new LostUpdateTemplate());
        register(new CounterRaceTemplate());
    }

    private TemplateRegistry() {}

    public static void register(CorpusTemplate t) {
        REGISTRY.put(t.id(), t);
    }

    public static CorpusTemplate get(String id) {
        CorpusTemplate t = REGISTRY.get(id);
        if (t == null) throw new IllegalArgumentException("Unknown template: " + id + ". Known: " + REGISTRY.keySet());
        return t;
    }

    public static Set<String> ids() {
        return Collections.unmodifiableSet(REGISTRY.keySet());
    }
}
