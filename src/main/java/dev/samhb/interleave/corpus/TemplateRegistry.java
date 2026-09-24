package dev.samhb.interleave.corpus;

import dev.samhb.interleave.corpus.templates.CounterRaceTemplate;
import dev.samhb.interleave.corpus.templates.LostUpdateTemplate;
import java.util.*;

/**
 * Registry of curated corpus templates.
 * Only curated patterns are registered to preserve realism.
 */
public final class TemplateRegistry {
    private static final Map<String, CorpusTemplate> REGISTRY = new LinkedHashMap<>();

    static {
        register(new LostUpdateTemplate());
        register(new CounterRaceTemplate());
    }

    private TemplateRegistry() {}

    /**
     * Registers a template.
     *
     * @param t template
     */
    public static void register(CorpusTemplate t) {
        REGISTRY.put(t.id(), t);
    }

    /**
     * Retrieves a template by id.
     *
     * @param id template id
     * @return template
     * @throws IllegalArgumentException if unknown
     */
    public static CorpusTemplate get(String id) {
        CorpusTemplate t = REGISTRY.get(id);
        if (t == null) throw new IllegalArgumentException("Unknown template: " + id + ". Known: " + REGISTRY.keySet());
        return t;
    }

    /**
     * @return unmodifiable set of ids
     */
    public static Set<String> ids() {
        return Collections.unmodifiableSet(REGISTRY.keySet());
    }
}
