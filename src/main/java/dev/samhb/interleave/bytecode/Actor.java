/** Public metadata-only annotation protocol for the bounded sequentially consistent profile. */
package dev.samhb.interleave.bytecode;

/** Declares one fixed actor entry method. */
@java.lang.annotation.Retention(java.lang.annotation.RetentionPolicy.CLASS)
@java.lang.annotation.Target(java.lang.annotation.ElementType.METHOD)
public @interface Actor {
    /** Identifiers are unique within a model and form a dense zero-based sequence.
     *
     * @return this actor's original identity */
    int id();
}
