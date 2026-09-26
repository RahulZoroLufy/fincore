package com.fincore.customer.domain;

/**
 * Lifecycle state of a Customer.
 *
 * <p>The status drives which operations are allowed:
 * <ul>
 *   <li>PENDING_KYC - created but not verified; no accounts, no transactions.</li>
 *   <li>ACTIVE      - verified; full operational access.</li>
 *   <li>SUSPENDED   - temporarily blocked; can be reactivated by an admin.</li>
 *   <li>CLOSED      - permanently closed; terminal state.</li>
 * </ul>
 *
 * <p>Stored in the database as a string (never ordinal) so that reordering
 * or inserting new values does not corrupt existing rows.
 */
public enum CustomerStatus {

    PENDING_KYC,
    ACTIVE,
    SUSPENDED,
    CLOSED;

    /**
     * Whether a customer in this status may open new accounts and transact.
     */
    public boolean canTransact() {
        return this == ACTIVE;
    }

    /**
     * Whether this status is terminal (no further transitions allowed).
     */
    public boolean isTerminal() {
        return this == CLOSED;
    }
}