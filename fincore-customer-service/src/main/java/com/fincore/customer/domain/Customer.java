package com.fincore.customer.domain;

import com.fincore.common.exception.ConflictException;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Objects;
import java.util.UUID;

/**
 * Customer aggregate root.
 *
 * <p>Owns the customer's identity, contact details and lifecycle state.
 * Business rules for state transitions live on this class; there is no
 * public setter for {@code status}.
 *
 * <p>Uses optimistic locking via {@link Version} to prevent lost updates
 * when two transactions modify the same row concurrently.
 */
@Entity
@Table(
        name = "customers",
        indexes = {
                @Index(name = "idx_customers_email", columnList = "email", unique = true),
                @Index(name = "idx_customers_status", columnList = "status")
        }
)
public class Customer {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "first_name", nullable = false, length = 100)
    private String firstName;

    @Column(name = "last_name", nullable = false, length = 100)
    private String lastName;

    @Column(name = "email", nullable = false, length = 254)
    private String email;

    @Column(name = "phone", length = 32)
    private String phone;

    @Column(name = "date_of_birth", nullable = false)
    private LocalDate dateOfBirth;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 32)
    private CustomerStatus status;

    @Version
    @Column(name = "version", nullable = false)
    private long version;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    /** Required by JPA. Do not use directly. */
    protected Customer() {
    }

    /**
     * Creates a new customer in {@link CustomerStatus#PENDING_KYC}.
     * Use the static factory instead of calling this constructor directly.
     */
    private Customer(UUID id,
                     String firstName,
                     String lastName,
                     String email,
                     String phone,
                     LocalDate dateOfBirth) {
        this.id = Objects.requireNonNull(id, "id");
        this.firstName = requireNonBlank(firstName, "firstName");
        this.lastName = requireNonBlank(lastName, "lastName");
        this.email = requireNonBlank(email, "email");
        this.phone = phone;
        this.dateOfBirth = Objects.requireNonNull(dateOfBirth, "dateOfBirth");
        this.status = CustomerStatus.PENDING_KYC;
    }

    /**
     * Factory method for creating a new customer.
     */
    public static Customer create(String firstName,
                                  String lastName,
                                  String email,
                                  String phone,
                                  LocalDate dateOfBirth) {
        return new Customer(UUID.randomUUID(), firstName, lastName, email, phone, dateOfBirth);
    }

    // ---------------------------------------------------------------------
    // Domain behaviour (state transitions)
    // ---------------------------------------------------------------------

    /**
     * Moves the customer from PENDING_KYC to ACTIVE.
     * Called after KYC verification succeeds.
     */
    public void activate() {
        if (status == CustomerStatus.ACTIVE) {
            return; // idempotent
        }
        if (status != CustomerStatus.PENDING_KYC && status != CustomerStatus.SUSPENDED) {
            throw new ConflictException("Cannot activate customer in status " + status);
        }
        this.status = CustomerStatus.ACTIVE;
    }

    /**
     * Temporarily blocks the customer. Only an ACTIVE customer can be suspended.
     */
    public void suspend() {
        if (status == CustomerStatus.SUSPENDED) {
            return;
        }
        if (status != CustomerStatus.ACTIVE) {
            throw new ConflictException("Cannot suspend customer in status " + status);
        }
        this.status = CustomerStatus.SUSPENDED;
    }

    /**
     * Permanently closes the customer. Cannot be undone.
     */
    public void close() {
        if (status == CustomerStatus.CLOSED) {
            return;
        }
        this.status = CustomerStatus.CLOSED;
    }

    /**
     * Updates mutable contact details.
     */
    public void updateContactDetails(String firstName,
                                     String lastName,
                                     String phone) {
        this.firstName = requireNonBlank(firstName, "firstName");
        this.lastName = requireNonBlank(lastName, "lastName");
        this.phone = phone;
    }

    // ---------------------------------------------------------------------
    // Getters (no setters for identity, status, email, dates)
    // ---------------------------------------------------------------------

    public UUID getId() { return id; }
    public String getFirstName() { return firstName; }
    public String getLastName() { return lastName; }
    public String getEmail() { return email; }
    public String getPhone() { return phone; }
    public LocalDate getDateOfBirth() { return dateOfBirth; }
    public CustomerStatus getStatus() { return status; }
    public long getVersion() { return version; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }

    // ---------------------------------------------------------------------
    // Helpers
    // ---------------------------------------------------------------------

    private static String requireNonBlank(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " must not be blank");
        }
        return value.trim();
    }
}