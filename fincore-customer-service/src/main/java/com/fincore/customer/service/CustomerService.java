package com.fincore.customer.service;

import com.fincore.common.exception.ConflictException;
import com.fincore.common.exception.NotFoundException;
import com.fincore.customer.domain.Customer;
import com.fincore.customer.domain.CustomerStatus;
import com.fincore.customer.repository.CustomerRepository;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.UUID;

/**
 * Application service for the Customer domain.
 *
 * <p>This class orchestrates use cases. It does not contain business rules —
 * those live on the {@link Customer} aggregate. It does not know about HTTP —
 * that lives in the controller.
 *
 * <p>Every public method runs inside a transaction. Read methods override
 * with {@code readOnly = true}.
 */
@Service
@Transactional
public class CustomerService {

    private final CustomerRepository repository;

    public CustomerService(CustomerRepository repository) {
        this.repository = repository;
    }

    // ---------------------------------------------------------------------
    // Commands
    // ---------------------------------------------------------------------

    /**
     * Creates a new customer in PENDING_KYC status.
     *
     * @throws ConflictException if a customer with the same email already exists
     */
    public Customer createCustomer(CreateCustomerCommand command) {
        if (repository.existsByEmail(command.email())) {
            throw new ConflictException("Customer with email " + command.email() + " already exists");
        }

        Customer customer = Customer.create(
                command.firstName(),
                command.lastName(),
                command.email(),
                command.phone(),
                command.dateOfBirth()
        );

        try {
            return repository.save(customer);
        } catch (DataIntegrityViolationException ex) {
            // Unique index on email caught a race condition
            throw new ConflictException("Customer with email " + command.email() + " already exists");
        }
    }

    /**
     * Updates the customer's mutable contact details.
     *
     * @throws NotFoundException  if the customer does not exist
     * @throws ConflictException  if a concurrent update was detected (optimistic lock)
     */
    public Customer updateContactDetails(UUID id, UpdateContactCommand command) {
        Customer customer = findOrThrow(id);
        customer.updateContactDetails(command.firstName(), command.lastName(), command.phone());
        return saveTranslatingConflicts(customer, id);
    }

    public Customer activateCustomer(UUID id) {
        Customer customer = findOrThrow(id);
        customer.activate();
        return saveTranslatingConflicts(customer, id);
    }

    public Customer suspendCustomer(UUID id) {
        Customer customer = findOrThrow(id);
        customer.suspend();
        return saveTranslatingConflicts(customer, id);
    }

    public Customer closeCustomer(UUID id) {
        Customer customer = findOrThrow(id);
        customer.close();
        return saveTranslatingConflicts(customer, id);
    }

    // ---------------------------------------------------------------------
    // Queries
    // ---------------------------------------------------------------------

    @Transactional(readOnly = true)
    public Customer getCustomer(UUID id) {
        return findOrThrow(id);
    }

    @Transactional(readOnly = true)
    public Page<Customer> listCustomers(CustomerStatus status, Pageable pageable) {
        if (status == null) {
            return repository.findAll(pageable);
        }
        return repository.findByStatus(status, pageable);
    }

    // ---------------------------------------------------------------------
    // Helpers
    // ---------------------------------------------------------------------

    private Customer findOrThrow(UUID id) {
        return repository.findById(id)
                .orElseThrow(() -> new NotFoundException("Customer", id));
    }

    private Customer saveTranslatingConflicts(Customer customer, UUID id) {
        try {
            return repository.save(customer);
        } catch (OptimisticLockingFailureException ex) {
            throw new ConflictException(
                    "Customer " + id + " was modified by another request. Please retry.");
        }
    }

    // ---------------------------------------------------------------------
    // Command records
    // ---------------------------------------------------------------------

    public record CreateCustomerCommand(
            String firstName,
            String lastName,
            String email,
            String phone,
            LocalDate dateOfBirth
    ) {}

    public record UpdateContactCommand(
            String firstName,
            String lastName,
            String phone
    ) {}
}