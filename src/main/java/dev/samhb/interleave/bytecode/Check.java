/** Public metadata-only annotation protocol for the bounded sequentially consistent profile. */
package dev.samhb.interleave.bytecode;

/** Declares an interpreted pure observer with a separate per-invocation fuel allowance. */
@java.lang.annotation.Retention(java.lang.annotation.RetentionPolicy.CLASS)
@java.lang.annotation.Target(java.lang.annotation.ElementType.METHOD)
public @interface Check {
    /** ALWAYS selects initial and property-relevant boundaries; FINAL selects normal completion of all actors.
     * FINAL is the default.
     *
     * @return configured observation timing */
    When when() default When.FINAL;
    /** Observation points from the approved contract. */
    enum When {
        /** Initially and at property-relevant committed execution boundaries. */ ALWAYS,
        /** Only after every actor terminates normally. */ FINAL
    }
}
