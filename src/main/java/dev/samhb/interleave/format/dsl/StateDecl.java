package dev.samhb.interleave.format.dsl;

import java.util.Collections;
import java.util.List;

/**
 * Declarative state declaration holding shared fields and per-thread locals.
 *
 * @param fields shared fields in declaration order
 * @param locals per-thread locals in declaration order
 */
public record StateDecl(List<FieldDecl> fields, List<LocalDecl> locals) {
    /**
     * Creates declaration.
     *
     * @param fields shared fields
     * @param locals locals
     */
    public StateDecl {
        fields = List.copyOf(fields);
        locals = List.copyOf(locals);
    }

    /**
     * Finds shared field by name.
     *
     * @param name field name
     * @return decl or null
     */
    public FieldDecl findField(String name) {
        for (FieldDecl f : fields) if (f.name().equals(name)) return f;
        return null;
    }

    /**
     * Finds local by name.
     *
     * @param name local name
     * @return decl or null
     */
    public LocalDecl findLocal(String name) {
        for (LocalDecl l : locals) if (l.name().equals(name)) return l;
        return null;
    }
}
