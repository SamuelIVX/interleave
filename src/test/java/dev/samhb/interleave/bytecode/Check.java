/** Test-only annotation protocol; not published in the library before94C. */
package dev.samhb.interleave.bytecode;

/** Declares a pure observer;94A validates metadata and94B implements invocation. */
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
