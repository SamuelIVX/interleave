/** Run-wide resource interruption distinct from model/scheduling bounds. */
package dev.samhb.interleave.core;

/** Deterministic resource-check order: states before time. */
public enum ResourceLimitReason { /** Visited-state allowance exhausted. */ MAX_STATES,
    /** Configured elapsed deadline reached. */ MAX_TIME }
