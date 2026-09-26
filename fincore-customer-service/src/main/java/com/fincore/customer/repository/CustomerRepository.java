package com.fincore.customer.repository;

import com.fincore.customer.domain.Customer;
import com.fincore.customer.domain.CustomerStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

/**
 * Repository for {@link Customer}.
 *
 * <p>Spring Data JPA generates the implementation at runtime.
 *
 * <p>Design rules:
 * <ul>
 *   <li>Prefer derived query methods (findByX, existsByX) over hand-written JPQL.</li>
 *   <li>Return {@link Optional} for single-result queries to force null handling.</li>
 *   <li>Use pagination for any query that could return unbounded results.
 *       Never call the inherited {@code findAll()} without a {@link Pageable}.</li>
 *   <li>Do not add methods "just in case". Add them when a service needs them.</li>
 * </ul>
 */
@Repository
public interface CustomerRepository extends JpaRepository<Customer, UUID> {

    /**
     * Look up a customer by email. Emails are unique.
     */
    Optional<Customer> findByEmail(String email);

    /**
     * Fast existence check — does not load the entity.
     */
    boolean existsByEmail(String email);

    /**
     * Paged listing by lifecycle status. Used by admin/read endpoints.
     */
    Page<Customer> findByStatus(CustomerStatus status, Pageable pageable);
}