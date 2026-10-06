package com.neueda.leap.repositories;

import com.neueda.leap.model.Account;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;

/**
 * Repository interface for Account persistence.
 * 
 * Extends JpaRepository for Spring Data JPA integration.
 * Provides CRUD operations and custom query methods.
 */
public interface AccountRepository extends JpaRepository<Account, Long> {
    /**
     * Finds an account by its unique account number string.
     *
     * @param accountNumber the account number to search for
     * @return Optional containing the account if found, empty otherwise
     */
    Optional<Account> findByAccountNumber(String accountNumber);

    /**
     * Finds the accounts owned by a customer (JWT subject).
     *
     * @param ownerUsername the username from the token's "sub" claim
     * @return the customer's accounts, empty if they have none
     */
    List<Account> findByOwnerUsername(String ownerUsername);
}
